package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * The "now playing" card under the clock. While something plays (Spotify, YouTube Music...) it shows the
 * artwork, title and artist; otherwise, if Spotify is installed, a "Spotify · Open" shortcut. It is focusable:
 * OK on the info opens the playing app (or Spotify); previous / play-pause / next buttons follow it, and the
 * remote's play/pause key works anywhere on the card. Right from the last item goes to the top bar, Down to
 * the dock (see MainActivity). Without the NowPlayingListener grant it is only the shortcut.
 */
final class NowPlaying {

    static final String SPOTIFY = "com.spotify.tv.android";

    /** Where focus goes when it leaves the card. */
    interface Exits {
        void right();
        void down();
    }

    private final Context context;
    private final View card;
    private final ImageView art;
    private final TextView title;
    private final TextView artist;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaSessionManager sessions;
    private MediaController current;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata metadata) { render(); }
        @Override public void onPlaybackStateChanged(PlaybackState state) { pick(); }
        @Override public void onSessionDestroyed() { pick(); }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener changed = controllers -> pick();

    private final View info;
    private final View track;
    private final View fill;
    /** Once a second while something plays and Home is resumed: moves the progress line. */
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            progress();
            main.postDelayed(this, 1000);
        }
    };
    private final ImageView prev;
    private final ImageView play;
    private final ImageView next;

    NowPlaying(Context context, View card, Exits exits) {
        this.context = context;
        this.card = card;
        this.info = card.findViewById(R.id.nowPlayingInfo);
        this.track = card.findViewById(R.id.nowPlayingTrack);
        this.fill = card.findViewById(R.id.nowPlayingFill);
        this.art = card.findViewById(R.id.nowPlayingArt);
        art.setClipToOutline(true);   // rounded artwork, as on tvOS
        this.title = card.findViewById(R.id.nowPlayingTitle);
        this.artist = card.findViewById(R.id.nowPlayingArtist);
        this.prev = card.findViewById(R.id.nowPlayingPrev);
        this.play = card.findViewById(R.id.nowPlayingPlay);
        this.next = card.findViewById(R.id.nowPlayingNext);
        info.setOnClickListener(v -> open());
        prev.setOnClickListener(v -> { if (current != null) current.getTransportControls().skipToPrevious(); });
        play.setOnClickListener(v -> togglePlayback());
        next.setOnClickListener(v -> { if (current != null) current.getTransportControls().skipToNext(); });
        View[] items = {info, prev, play, next};
        for (int i = 0; i < items.length; i++) {
            final int at = i;
            View item = items[i];
            item.setOnFocusChangeListener((v, focused) -> v.animate()
                    .scaleX(focused ? 1.08f : 1f).scaleY(focused ? 1.08f : 1f).setDuration(160).start());
            item.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        for (int j = at + 1; j < items.length; j++) {
                            if (items[j].getVisibility() == View.VISIBLE) { items[j].requestFocus(); return true; }
                        }
                        exits.right();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        for (int j = at - 1; j >= 0; j--) {
                            if (items[j].getVisibility() == View.VISIBLE) { items[j].requestFocus(); return true; }
                        }
                        return true;   // the card is the left edge: stay
                    case KeyEvent.KEYCODE_DPAD_DOWN: exits.down(); return true;
                    case KeyEvent.KEYCODE_DPAD_UP: return true;
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return togglePlayback();
                    default: return false;
                }
            });
        }
    }

    boolean isShown() {
        return card.getVisibility() == View.VISIBLE;
    }

    void focus() {
        info.requestFocus();
    }

    void start() {
        sessions = context.getSystemService(MediaSessionManager.class);
        if (sessions != null) {
            try {
                sessions.addOnActiveSessionsChangedListener(changed, listener(), main);
            } catch (SecurityException notGranted) {
                sessions = null;
            }
        }
        pick();
    }

    void stop() {
        main.removeCallbacks(tick);
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(changed);
        sessions = null;
        watch(null);
    }

    private ComponentName listener() {
        return new ComponentName(context, NowPlayingListener.class);
    }

    /** Follows the first session that is playing, else a paused one, so a pause keeps the song on the card. */
    private void pick() {
        MediaController best = null;
        if (sessions != null) {
            try {
                List<MediaController> list = sessions.getActiveSessions(listener());
                for (MediaController c : list) {
                    PlaybackState s = c.getPlaybackState();
                    if (s == null || c.getMetadata() == null) continue;
                    if (s.getState() == PlaybackState.STATE_PLAYING) { best = c; break; }
                    if (best == null && s.getState() == PlaybackState.STATE_PAUSED) best = c;
                }
            } catch (SecurityException notGranted) {
                best = null;
            }
        }
        watch(best);
        render();
    }

    private void watch(MediaController next) {
        if (current != null && (next == null || !current.getSessionToken().equals(next.getSessionToken()))) {
            current.unregisterCallback(callback);
            current = null;
        }
        if (next != null && current == null) {
            current = next;
            current.registerCallback(callback, main);
        }
    }

    private void render() {
        MediaMetadata m = current == null ? null : current.getMetadata();
        CharSequence t = m == null ? null : m.getText(MediaMetadata.METADATA_KEY_TITLE);
        if (t != null) {
            PlaybackState s = current.getPlaybackState();
            boolean paused = s == null || s.getState() != PlaybackState.STATE_PLAYING;
            CharSequence a = m.getText(MediaMetadata.METADATA_KEY_ARTIST);
            title.setText(t);
            String line = a == null ? "" : a.toString();
            if (paused) line = line.isEmpty() ? context.getString(R.string.now_playing_paused)
                    : line + " · " + context.getString(R.string.now_playing_paused);
            artist.setText(line);
            artist.setVisibility(line.isEmpty() ? View.GONE : View.VISIBLE);
            Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            if (b != null) art.setImageBitmap(b); else art.setImageDrawable(icon(current.getPackageName()));
            play.setImageResource(paused ? R.drawable.ic_media_play : R.drawable.ic_media_pause);
            long duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            track.setVisibility(duration > 0 ? View.VISIBLE : View.GONE);
            main.removeCallbacks(tick);
            if (duration > 0) {
                progress();
                if (!paused) main.postDelayed(tick, 1000);
            }
            controls(true);
            show();
            return;
        }
        // Nothing playing: a Spotify shortcut, if Spotify is installed.
        Drawable spotify = icon(SPOTIFY);
        if (spotify == null) {
            if (card.hasFocus()) card.clearFocus();
            controls(false);
            card.setVisibility(View.GONE);
            return;
        }
        title.setText("Spotify");
        artist.setText(R.string.now_playing_open);
        artist.setVisibility(View.VISIBLE);
        art.setImageDrawable(spotify);
        main.removeCallbacks(tick);
        track.setVisibility(View.GONE);
        controls(false);
        show();
    }

    /** Sizes the progress fill from the session's position, extrapolated from its last update. */
    private void progress() {
        if (current == null || current.getMetadata() == null) return;
        long duration = current.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION);
        PlaybackState s = current.getPlaybackState();
        if (duration <= 0 || s == null) return;
        long pos = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            pos += (long) ((android.os.SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
        }
        float f = Math.max(0f, Math.min(1f, pos / (float) duration));
        int w = track.getWidth();
        if (w == 0) return;
        android.view.ViewGroup.LayoutParams lp = fill.getLayoutParams();
        int target = Math.round(w * f);
        if (lp.width != target) { lp.width = target; fill.setLayoutParams(lp); }
    }

    private void controls(boolean on) {
        if (!on && (prev.hasFocus() || play.hasFocus() || next.hasFocus())) info.requestFocus();
        int v = on ? View.VISIBLE : View.GONE;
        prev.setVisibility(v);
        play.setVisibility(v);
        next.setVisibility(v);
    }

    private void show() {
        art.setVisibility(art.getDrawable() == null ? View.GONE : View.VISIBLE);
        card.setVisibility(View.VISIBLE);
    }

    private Drawable icon(String pkg) {
        try {
            return context.getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException missing) {
            return null;
        }
    }

    private void open() {
        String pkg = current != null ? current.getPackageName() : SPOTIFY;
        PackageManager pm = context.getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        if (launch == null) return;
        try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    private boolean togglePlayback() {
        if (current == null) return false;
        PlaybackState s = current.getPlaybackState();
        if (s != null && s.getState() == PlaybackState.STATE_PLAYING) current.getTransportControls().pause();
        else current.getTransportControls().play();
        return true;
    }
}

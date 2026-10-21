package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * The "now playing" card under the clock: artwork, title and artist of whatever media session is playing
 * (Spotify, YouTube Music...). Display only, never focusable, so D-pad navigation is untouched; the remote's
 * media keys and Spotify Connect still control playback. Without the NowPlayingListener grant it stays gone.
 */
final class NowPlaying {

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

    NowPlaying(Context context, View card) {
        this.context = context;
        this.card = card;
        this.art = card.findViewById(R.id.nowPlayingArt);
        this.title = card.findViewById(R.id.nowPlayingTitle);
        this.artist = card.findViewById(R.id.nowPlayingArtist);
    }

    void start() {
        sessions = context.getSystemService(MediaSessionManager.class);
        if (sessions == null) return;
        try {
            sessions.addOnActiveSessionsChangedListener(changed, listener(), main);
        } catch (SecurityException notGranted) {
            sessions = null;
            card.setVisibility(View.GONE);
            return;
        }
        pick();
    }

    void stop() {
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(changed);
        sessions = null;
        watch(null);
    }

    private ComponentName listener() {
        return new ComponentName(context, NowPlayingListener.class);
    }

    /** Follows the first session that is playing; none playing, the card hides. */
    private void pick() {
        if (sessions == null) return;
        MediaController playing = null;
        try {
            List<MediaController> list = sessions.getActiveSessions(listener());
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { playing = c; break; }
            }
        } catch (SecurityException notGranted) {
            playing = null;
        }
        watch(playing);
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
        PlaybackState s = current == null ? null : current.getPlaybackState();
        boolean playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
        CharSequence t = m == null ? null : m.getText(MediaMetadata.METADATA_KEY_TITLE);
        if (!playing || t == null) {
            card.setVisibility(View.GONE);
            return;
        }
        CharSequence a = m.getText(MediaMetadata.METADATA_KEY_ARTIST);
        title.setText(t);
        artist.setText(a == null ? "" : a);
        artist.setVisibility(a == null ? View.GONE : View.VISIBLE);
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        art.setImageBitmap(b);
        art.setVisibility(b == null ? View.GONE : View.VISIBLE);
        card.setVisibility(View.VISIBLE);
    }
}

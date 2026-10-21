package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "All apps" screen opened from the dock's last tile: every TV app (leanback launcher entry) in a plain grid,
 * alphabetical, on black. Lives only while open, so Home keeps nothing extra in memory. Back returns to Home.
 */
public class AllAppsActivity extends Activity {

    static final String COLUMNS_KEY = "all_apps_columns";
    static final int DEFAULT_COLUMNS = 5;
    /** Launcher settings' column choice (4 to 7) while home_more is on. */
    private int columns;
    /** Tile size from the real column width: 16:9, with a gap on each side. */
    private int tileW, tileH;

    private static final class App {
        final String label;
        final ComponentName component;
        final Drawable banner;
        final Drawable icon;

        App(String label, ComponentName component, Drawable banner, Drawable icon) {
            this.label = label;
            this.component = component;
            this.banner = banner;
            this.icon = icon;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_all_apps);
        RecyclerView grid = findViewById(R.id.allAppsGrid);
        int chosen = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getInt(COLUMNS_KEY, DEFAULT_COLUMNS);
        columns = chosen >= 4 && chosen <= 7 ? chosen : DEFAULT_COLUMNS;
        float d = getResources().getDisplayMetrics().density;
        int screen = getResources().getDisplayMetrics().widthPixels;
        int column = (screen - Math.round(160 * d)) / columns;
        tileW = column - Math.round(24 * d);
        tileH = Math.round(tileW * 9 / 16f);
        grid.setLayoutManager(new GridLayoutManager(this, columns));
        grid.setItemAnimator(null);
        List<App> apps = load();
        grid.setAdapter(new Adapter(apps));
        grid.post(() -> {
            RecyclerView.ViewHolder first = grid.findViewHolderForAdapterPosition(0);
            if (first != null) first.itemView.findViewById(R.id.allAppTile).requestFocus();
        });
    }

    private List<App> load() {
        PackageManager pm = getPackageManager();
        Set<String> hidden = new HashSet<>();
        Collections.addAll(hidden, getResources().getStringArray(R.array.hidden_apps));
        List<App> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || hidden.contains(pkg) || !seen.add(pkg)) continue;
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            Drawable icon = banner == null ? ri.loadIcon(pm) : null;
            apps.add(new App(String.valueOf(ri.loadLabel(pm)),
                    new ComponentName(pkg, ri.activityInfo.name), banner, icon));
        }
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return apps;
    }

    private void launch(App app) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        private final List<App> apps;

        Adapter(List<App> apps) {
            this.apps = apps;
        }

        final class Holder extends RecyclerView.ViewHolder {
            final View tile;
            final ImageView banner;
            final ImageView icon;
            final TextView name;

            Holder(View v) {
                super(v);
                tile = v.findViewById(R.id.allAppTile);
                banner = v.findViewById(R.id.allAppBanner);
                icon = v.findViewById(R.id.allAppIcon);
                name = v.findViewById(R.id.allAppName);
                tile.setClipToOutline(true);
                tile.setOnFocusChangeListener((view, focused) -> {
                    view.animate().scaleX(focused ? 1.1f : 1f).scaleY(focused ? 1.1f : 1f)
                            .translationZ(focused ? 12f : 0f).setDuration(160).start();
                    name.animate().alpha(focused ? 1f : 0f).setDuration(160).start();
                });
            }
        }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_all_app, parent, false);
            View tile = v.findViewById(R.id.allAppTile);
            ViewGroup.LayoutParams lp = tile.getLayoutParams();
            lp.width = tileW;
            lp.height = tileH;
            tile.setLayoutParams(lp);
            View name = v.findViewById(R.id.allAppName);
            ViewGroup.LayoutParams nlp = name.getLayoutParams();
            nlp.width = tileW;
            name.setLayoutParams(nlp);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            App app = apps.get(position);
            h.name.setText(app.label);
            h.banner.setImageDrawable(app.banner);
            h.banner.setVisibility(app.banner != null ? View.VISIBLE : View.GONE);
            h.icon.setImageDrawable(app.icon);
            h.icon.setVisibility(app.banner == null ? View.VISIBLE : View.GONE);
            h.tile.setOnClickListener(v -> launch(app));
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }
    }
}

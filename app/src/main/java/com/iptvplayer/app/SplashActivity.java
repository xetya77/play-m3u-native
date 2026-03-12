package com.iptvplayer.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SplashActivity extends Activity {

    private PrefsManager prefs;
    private List<com.iptvplayer.app.Playlist> playlists;
    private boolean animDone = false;
    private boolean loadDone = false;
    private boolean refreshed = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        // Fullscreen sejati: sembunyikan status bar sepenuhnya
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        super.onCreate(savedInstanceState);

        // Immersive sticky: status bar + nav bar tersembunyi penuh
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        prefs = new PrefsManager(this);
        playlists = prefs.loadPlaylists();

        // Orientation: portrait saat belum ada playlist, landscape saat sudah ada
        boolean hasPlaylist = !playlists.isEmpty()
                && playlists.get(Math.min(prefs.getCurrentPlaylistIndex(), playlists.size()-1)).channels != null
                && !playlists.get(Math.min(prefs.getCurrentPlaylistIndex(), playlists.size()-1)).channels.isEmpty();

        if (hasPlaylist) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        setContentView(R.layout.activity_splash);

        // Animasi: fade in teks → tahan → fade out (sama persis seperti sebelumnya)
        LinearLayout container = findViewById(R.id.splash_text_container);
        container.setAlpha(0f);

        container.animate()
            .alpha(1f)
            .setDuration(900)
            .setStartDelay(150)
            .withEndAction(() -> {
                handler.postDelayed(() ->
                    container.animate()
                        .alpha(0f)
                        .setDuration(700)
                        .withEndAction(() -> {
                            animDone = true;
                            tryNavigate();
                        })
                        .start(),
                700);
            })
            .start();

        // Parallel: refresh playlist di background selama animasi berjalan
        refreshPlaylistInBackground();
    }

    private void refreshPlaylistInBackground() {
        if (playlists.isEmpty()) {
            loadDone = true;
            return;
        }

        int idx = prefs.getCurrentPlaylistIndex();
        if (idx >= playlists.size()) idx = 0;
        com.iptvplayer.app.Playlist pl = playlists.get(idx);

        if (!pl.downloadOnStart || pl.url == null || pl.url.isEmpty()
                || pl.url.startsWith("content://")) {
            loadDone = true;
            return;
        }

        executor.execute(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
                Request req = new Request.Builder().url(pl.url).build();
                Response resp = client.newCall(req).execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    String content = resp.body().string();
                    List<Channel> channels = M3UParser.parse(content);
                    if (!channels.isEmpty()) {
                        pl.channels = channels;
                        pl.lastUpdated = System.currentTimeMillis();
                        prefs.savePlaylists(playlists);
                        refreshed = true;
                    }
                }
            } catch (Exception e) {
                // Gagal refresh — pakai data lama
            }
            handler.post(() -> {
                loadDone = true;
                tryNavigate();
            });
        });
    }

    private void tryNavigate() {
        if (!animDone || !loadDone) return;

        if (playlists.isEmpty()) {
            goToMain();
            return;
        }

        int idx = prefs.getCurrentPlaylistIndex();
        if (idx >= playlists.size()) idx = 0;
        com.iptvplayer.app.Playlist pl = playlists.get(idx);

        if (pl.channels == null || pl.channels.isEmpty()) {
            goToMain();
            return;
        }

        int chIdx = prefs.getCurrentChannelIndex();
        if (chIdx >= pl.channels.size()) chIdx = 0;

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("playlist_index", idx);
        intent.putExtra("channel_index", chIdx);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}

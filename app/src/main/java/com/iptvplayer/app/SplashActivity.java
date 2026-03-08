package com.iptvplayer.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

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
        // Harus sebelum super.onCreate — cegah status bar hitam
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().setStatusBarColor(0xFFE4EEF0);
        getWindow().setNavigationBarColor(0xFFE4EEF0);

        super.onCreate(savedInstanceState);

        // Immersive fullscreen
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setContentView(R.layout.activity_splash);

        prefs = new PrefsManager(this);
        playlists = prefs.loadPlaylists();

        ImageView logo = findViewById(R.id.iv_splash_logo);
        logo.setAlpha(0f);

        // Animasi fade in → tahan → fade out
        logo.animate()
            .alpha(1f)
            .setDuration(900)
            .setStartDelay(150)
            .withEndAction(() -> {
                // Tahan → fade out
                handler.postDelayed(() ->
                    logo.animate()
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

    /**
     * Refresh playlist aktif di background (jika downloadOnStart=true dan URL tersedia).
     * Setelah selesai, set loadDone=true dan coba navigasi.
     */
    private void refreshPlaylistInBackground() {
        if (playlists.isEmpty()) {
            // Belum ada playlist, tidak perlu refresh
            loadDone = true;
            return;
        }

        int idx = prefs.getCurrentPlaylistIndex();
        if (idx >= playlists.size()) idx = 0;
        com.iptvplayer.app.Playlist pl = playlists.get(idx);
        final int finalIdx = idx;

        if (!pl.downloadOnStart || pl.url == null || pl.url.isEmpty()
                || pl.url.startsWith("content://")) {
            // File lokal atau tidak perlu auto-refresh
            loadDone = true;
            return;
        }

        // Fetch dari URL di background
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
                // Gagal refresh — pakai data lama yang sudah tersimpan
            }
            handler.post(() -> {
                loadDone = true;
                tryNavigate();
            });
        });
    }

    /**
     * Navigasi hanya dilakukan saat KEDUA kondisi terpenuhi:
     * animasi selesai AND load selesai.
     */
    private void tryNavigate() {
        if (!animDone || !loadDone) return;

        if (playlists.isEmpty()) {
            // Belum punya playlist → tampilkan halaman welcome
            goToMain();
            return;
        }

        int idx = prefs.getCurrentPlaylistIndex();
        if (idx >= playlists.size()) idx = 0;
        com.iptvplayer.app.Playlist pl = playlists.get(idx);

        if (pl.channels == null || pl.channels.isEmpty()) {
            // Playlist kosong → ke MainActivity (settings)
            goToMain();
            return;
        }

        // Ada playlist + channel → langsung play!
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

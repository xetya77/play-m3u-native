package com.iptvplayer.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.C;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends Activity {

    // Player views
    private PlayerView playerView;
    private View videoLoading, blackFlash;
    private View bar1, bar2, bar3;
    private TextView tvLoadingMsg;

    // Clock
    private TextView tvClock;

    // Channel info OSD
    private LinearLayout channelInfo;
    private TextView tvChNum, tvChName, tvChEpg, tvChGroup, tvChPlaylistName;
    private ImageView ivChLogo;
    private TextView tvChLogoFallback;
    private FrameLayout chLogoContainer;

    // Remote guide
    private View remoteGuide;

    // Number overlay
    private TextView numOverlay;

    // Backdrop
    private View chListBackdrop;

    // Sidebar kategori
    private LinearLayout categorySidebar;
    private LinearLayout catAll, catTv, catRadio, catMovie, catSettings;
    private ImageView icCatAll, icCatTv, icCatRadio, icCatMovie, icCatSettings;

    // Panel daftar channel
    private LinearLayout chListPanel;
    private TextView tvPanelTitle, tvPanelClock, tvResolution, tvBitrate;
    private androidx.recyclerview.widget.RecyclerView rvChList;

    // Panel kategori full
    private LinearLayout categoryPanelFull;
    private LinearLayout catFullAll, catFullTv, catFullRadio, catFullMovie, catFullSettings;
    private ImageView catFullAllIcon, catFullTvIcon, catFullRadioIcon, catFullMovieIcon, catFullSettingsIcon;
    private TextView catFullAllText, catFullTvText, catFullRadioText, catFullMovieText, catFullSettingsText;

    // Swipe feedback
    private View swipeHint;
    private TextView swipeFbUp, swipeFbDown;

    // Player
    private ExoPlayer player;
    private List<Channel> channels = new ArrayList<>();
    private int currentChannelIdx = 0;
    private int playlistIdx = 0;
    private String playlistName = "";

    // State
    private boolean panelOpen = false;
    private boolean categoryFullOpen = false;
    private String numBuffer = "";
    private boolean streamStarted = false; // apakah stream sudah pernah berhasil tampil
    private String activeCategoryFilter = "ALL"; // ALL, TV, RADIO, FILM, SETTINGS

    // Handlers
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable chInfoHideRunnable;
    private Runnable numClearRunnable;
    private Runnable swipeHintHideRunnable;
    private Runnable clockRunnable;
    private Runnable dominoRunnable;

    // Domino animation state
    private int dominoPhase = 0;

    private PrefsManager prefs;
    private ChannelAdapter channelAdapter;
    private GestureDetector gestureDetector;

    // Touch: hitung tap untuk detect double-tap
    private long lastTapTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        setContentView(R.layout.activity_player);

        prefs = new PrefsManager(this);
        playlistIdx = getIntent().getIntExtra("playlist_index", 0);
        currentChannelIdx = getIntent().getIntExtra("channel_index", 0);

        List<Playlist> playlists = prefs.loadPlaylists();
        if (playlistIdx < playlists.size()) {
            Playlist pl = playlists.get(playlistIdx);
            channels = pl.channels != null ? pl.channels : new ArrayList<>();
            playlistName = pl.name;
        }

        if (channels.isEmpty()) { finish(); return; }

        bindViews();
        setupPlayer();
        setupChannelAdapter();
        setupGestures();
        setupCategoryListeners();
        startClock();
        startDominoAnimation();

        playChannel(currentChannelIdx, false);
        showSwipeHint();
    }

    // ===== BIND VIEWS =====
    private void bindViews() {
        playerView     = findViewById(R.id.player_view);
        videoLoading   = findViewById(R.id.video_loading);
        blackFlash     = findViewById(R.id.black_flash);
        bar1           = findViewById(R.id.bar1);
        bar2           = findViewById(R.id.bar2);
        bar3           = findViewById(R.id.bar3);
        tvLoadingMsg   = findViewById(R.id.tv_loading_msg);
        tvClock        = findViewById(R.id.tv_clock);

        channelInfo       = findViewById(R.id.channel_info);
        tvChNum           = findViewById(R.id.tv_ch_num);
        tvChName          = findViewById(R.id.tv_ch_name);
        tvChEpg           = findViewById(R.id.tv_ch_epg);
        tvChGroup         = findViewById(R.id.tv_ch_group);
        tvChPlaylistName  = findViewById(R.id.tv_ch_playlist_name);
        ivChLogo          = findViewById(R.id.iv_ch_logo);
        tvChLogoFallback  = findViewById(R.id.tv_ch_logo_fallback);
        chLogoContainer   = findViewById(R.id.ch_logo_container);

        remoteGuide    = findViewById(R.id.remote_guide);
        numOverlay     = findViewById(R.id.num_overlay);
        chListBackdrop = findViewById(R.id.ch_list_backdrop);

        categorySidebar = findViewById(R.id.category_sidebar);
        catAll     = findViewById(R.id.cat_all);
        catTv      = findViewById(R.id.cat_tv);
        catRadio   = findViewById(R.id.cat_radio);
        catMovie   = findViewById(R.id.cat_movie);
        catSettings= findViewById(R.id.cat_settings);
        icCatAll   = findViewById(R.id.ic_cat_all);
        icCatTv    = findViewById(R.id.ic_cat_tv);
        icCatRadio = findViewById(R.id.ic_cat_radio);
        icCatMovie = findViewById(R.id.ic_cat_movie);
        icCatSettings = findViewById(R.id.ic_cat_settings);

        chListPanel   = findViewById(R.id.ch_list_panel);
        tvPanelTitle  = findViewById(R.id.tv_panel_title);
        tvPanelClock  = findViewById(R.id.tv_panel_clock);
        tvResolution  = findViewById(R.id.tv_resolution);
        tvBitrate     = findViewById(R.id.tv_bitrate);
        rvChList      = findViewById(R.id.rv_ch_list);

        categoryPanelFull   = findViewById(R.id.category_panel_full);
        catFullAll          = findViewById(R.id.cat_full_all);
        catFullTv           = findViewById(R.id.cat_full_tv);
        catFullRadio        = findViewById(R.id.cat_full_radio);
        catFullMovie        = findViewById(R.id.cat_full_movie);
        catFullSettings     = findViewById(R.id.cat_full_settings);
        catFullAllIcon      = findViewById(R.id.cat_full_all_icon);
        catFullTvIcon       = findViewById(R.id.cat_full_tv_icon);
        catFullRadioIcon    = findViewById(R.id.cat_full_radio_icon);
        catFullMovieIcon    = findViewById(R.id.cat_full_movie_icon);
        catFullSettingsIcon = findViewById(R.id.cat_full_settings_icon);
        catFullAllText      = findViewById(R.id.cat_full_all_text);
        catFullTvText       = findViewById(R.id.cat_full_tv_text);
        catFullRadioText    = findViewById(R.id.cat_full_radio_text);
        catFullMovieText    = findViewById(R.id.cat_full_movie_text);
        catFullSettingsText = findViewById(R.id.cat_full_settings_text);

        swipeHint  = findViewById(R.id.swipe_hint);
        swipeFbUp  = findViewById(R.id.swipe_fb_up);
        swipeFbDown= findViewById(R.id.swipe_fb_down);
    }

    // ===== CLOCK =====
    private void startClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        clockRunnable = new Runnable() {
            @Override public void run() {
                String time = sdf.format(new Date());
                tvClock.setText(time);
                if (tvPanelClock != null) tvPanelClock.setText(time);
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(clockRunnable);
    }

    // ===== DOMINO ANIMATION =====
    private void startDominoAnimation() {
        // Animasi 3 bar: domino style — masing-masing naik turun bergantian
        dominoRunnable = new Runnable() {
            @Override public void run() {
                if (videoLoading.getVisibility() != View.VISIBLE) {
                    handler.postDelayed(this, 400);
                    return;
                }
                animateDominoPhase();
                dominoPhase = (dominoPhase + 1) % 6;
                handler.postDelayed(this, 200);
            }
        };
        handler.postDelayed(dominoRunnable, 200);
    }

    private void animateDominoPhase() {
        // Setiap bar scaleY naik-turun bergantian seperti domino
        float[][] scales = {
            {0.4f, 0.7f, 1.0f},
            {0.6f, 1.0f, 0.7f},
            {1.0f, 0.7f, 0.4f},
            {0.7f, 0.4f, 0.7f},
            {0.4f, 0.7f, 1.0f},
            {0.7f, 1.0f, 0.7f},
        };
        float[] s = scales[dominoPhase];
        animateBar(bar1, s[0]);
        animateBar(bar2, s[1]);
        animateBar(bar3, s[2]);
    }

    private void animateBar(View bar, float targetAlpha) {
        if (bar == null) return;
        bar.animate().alpha(targetAlpha).setDuration(180).start();
    }

    // ===== PLAYER SETUP =====
    private void setupPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setUseController(false);
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    videoLoading.setBackgroundColor(0x00000000);
                    videoLoading.setVisibility(View.VISIBLE);
                    tvLoadingMsg.setText("Memuat...");
                } else if (state == Player.STATE_READY) {
                    videoLoading.setVisibility(View.GONE);
                    if (!streamStarted) {
                        onFirstFrameReady();
                    }
                    streamStarted = true;
                    updateVideoStats();
                } else if (state == Player.STATE_ENDED) {
                    videoLoading.setBackgroundColor(0x00000000);
                    videoLoading.setVisibility(View.VISIBLE);
                    tvLoadingMsg.setText("Stream berakhir...");
                }
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                videoLoading.setVisibility(View.VISIBLE);
                tvLoadingMsg.setText(getErrorMessage(error));
                handler.postDelayed(() -> {
                    if (!isFinishing()) playChannel(currentChannelIdx, false);
                }, 3000);
            }
        });
    }

    private void onFirstFrameReady() {
        // Panduan remote hilang setelah stream pertama tampil
        remoteGuide.animate().alpha(0f).setDuration(800)
                .withEndAction(() -> remoteGuide.setVisibility(View.GONE)).start();
        // Opacity jam turun jadi 65%
        tvClock.animate().alpha(0.65f).setDuration(600).start();
    }

    private void updateVideoStats() {
        try {
            Format vf = player.getVideoFormat();
            if (vf != null && tvResolution != null) {
                String res = vf.width + "x" + vf.height;
                tvResolution.setText("Resolusi: " + res);
                int bitrate = vf.bitrate;
                if (bitrate > 0 && tvBitrate != null) {
                    tvBitrate.setText("Bitrate: " + (bitrate / 1000) + " kb/s");
                }
            }
        } catch (Exception ignored) {}
    }

    private String getErrorMessage(androidx.media3.common.PlaybackException error) {
        int code = error.errorCode;
        if (code == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
            return "Tidak ada koneksi internet";
        if (code == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
            return "Koneksi timeout, coba lagi...";
        if (code == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
            return "Stream tidak tersedia (HTTP error)";
        if (code == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)
            return "Format tidak didukung";
        return "Gagal memutar stream";
    }

    // ===== CHANNEL ADAPTER =====
    private void setupChannelAdapter() {
        tvPanelTitle.setText(playlistName.isEmpty() ? "SEMUA SALURAN" : playlistName.toUpperCase());

        channelAdapter = new ChannelAdapter(idx -> {
            playChannel(idx, true);
            hidePanel();
        });
        channelAdapter.setChannels(channels);
        channelAdapter.setActiveIndex(currentChannelIdx);

        rvChList.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        rvChList.setAdapter(channelAdapter);
    }

    // ===== GESTURE =====
    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (e1 == null || e2 == null) return false;
                float dY = e2.getY() - e1.getY();
                float dX = e2.getX() - e1.getX();

                if (Math.abs(dX) > Math.abs(dY)) {
                    if (dX > 80 && Math.abs(vX) > 100) {
                        // Swipe KANAN:
                        // - Tidak ada panel terbuka → buka panel daftar channel
                        // - Panel sudah terbuka → buka panel kategori (keterangan icon)
                        if (!panelOpen && !categoryFullOpen) {
                            openPanel();
                        } else if (panelOpen && !categoryFullOpen) {
                            openCategoryFull();
                        }
                        return true;
                    } else if (dX < -80 && Math.abs(vX) > 100) {
                        // Swipe KIRI → tutup / kembali
                        if (categoryFullOpen) closeCategoryFull();
                        else if (panelOpen) hidePanel();
                        return true;
                    }
                } else {
                    if (Math.abs(dY) > 80 && Math.abs(vY) > 100) {
                        if (!panelOpen && !categoryFullOpen) {
                            if (dY < 0) { showSwipeFeedback(true);  playChannel(currentChannelIdx + 1, true); }
                            else        { showSwipeFeedback(false); playChannel(currentChannelIdx - 1, true); }
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                long now = System.currentTimeMillis();
                if (now - lastTapTime < 400) {
                    // Double tap
                    lastTapTime = 0;
                    if (!categoryFullOpen) openCategoryFull();
                    else closeCategoryFull();
                } else {
                    lastTapTime = now;
                    handler.postDelayed(() -> {
                        if (lastTapTime != 0) {
                            // Single tap
                            lastTapTime = 0;
                            if (categoryFullOpen) closeCategoryFull();
                            else if (panelOpen) hidePanel();
                            else toggleChInfo();
                        }
                    }, 420);
                }
                return true;
            }
        });

        playerView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });

        chListBackdrop.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (categoryFullOpen) closeCategoryFull();
                else if (panelOpen) hidePanel();
            }
            return true;
        });
    }

    // ===== KATEGORI LISTENERS =====
    private void setupCategoryListeners() {
        // Sidebar icons
        catAll.setOnClickListener(v -> selectCategory("ALL"));
        catTv.setOnClickListener(v -> selectCategory("TV"));
        catRadio.setOnClickListener(v -> selectCategory("RADIO"));
        catMovie.setOnClickListener(v -> selectCategory("FILM"));
        catSettings.setOnClickListener(v -> goToSettings());

        // Full category panel
        catFullAll.setOnClickListener(v -> { selectCategory("ALL"); closeCategoryFull(); });
        catFullTv.setOnClickListener(v -> { selectCategory("TV"); closeCategoryFull(); });
        catFullRadio.setOnClickListener(v -> { selectCategory("RADIO"); closeCategoryFull(); });
        catFullMovie.setOnClickListener(v -> { selectCategory("FILM"); closeCategoryFull(); });
        catFullSettings.setOnClickListener(v -> goToSettings());
    }

    private void selectCategory(String cat) {
        activeCategoryFilter = cat;

        // Update sidebar icon opacity
        icCatAll.setAlpha(cat.equals("ALL") ? 1.0f : 0.5f);
        icCatTv.setAlpha(cat.equals("TV") ? 1.0f : 0.5f);
        icCatRadio.setAlpha(cat.equals("RADIO") ? 1.0f : 0.5f);
        icCatMovie.setAlpha(cat.equals("FILM") ? 1.0f : 0.5f);
        icCatSettings.setAlpha(0.5f);

        // Update full panel item styling
        setCategoryFullItemActive(catFullAll, catFullAllIcon, catFullAllText, cat.equals("ALL"));
        setCategoryFullItemActive(catFullTv, catFullTvIcon, catFullTvText, cat.equals("TV"));
        setCategoryFullItemActive(catFullRadio, catFullRadioIcon, catFullRadioText, cat.equals("RADIO"));
        setCategoryFullItemActive(catFullMovie, catFullMovieIcon, catFullMovieText, cat.equals("FILM"));
        setCategoryFullItemActive(catFullSettings, catFullSettingsIcon, catFullSettingsText, false);

        // Filter channel list
        channelAdapter.applyGroupFilter(cat);

        // Buka panel jika belum terbuka
        if (!panelOpen) openPanel();
    }

    private void setCategoryFullItemActive(LinearLayout item, ImageView icon, TextView text, boolean active) {
        if (active) {
            item.setBackground(getDrawable(R.drawable.bg_category_item_active));
            icon.setAlpha(1.0f);
            text.setTextColor(0xFF000000);
        } else {
            item.setBackground(null);
            icon.setAlpha(0.5f);
            text.setTextColor(0x80FFFFFF);
        }
    }

    private void goToSettings() {
        finish(); // Kembali ke MainActivity (settings)
    }

    // ===== PLAY CHANNEL =====
    private void playChannel(int idx, boolean withFlash) {
        if (channels.isEmpty()) return;
        if (idx < 0) idx = channels.size() - 1;
        if (idx >= channels.size()) idx = 0;

        currentChannelIdx = idx;
        prefs.setCurrentChannelIndex(idx);

        Channel ch = channels.get(idx);
        if (withFlash) blackFlash();

        videoLoading.setBackgroundColor(0x00000000);
        videoLoading.setVisibility(View.VISIBLE);
        tvLoadingMsg.setText("Memuat...");

        updateChInfo(ch, idx);

        try {
            player.stop();

            String ua = (ch.userAgent != null && !ch.userAgent.isEmpty())
                    ? ch.userAgent
                    : "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 Chrome/96.0 Safari/537.36";

            Map<String, String> headers = new HashMap<>();
            if (ch.referrer != null && !ch.referrer.isEmpty()) {
                String origin = ch.referrer.replaceAll("/$", "");
                headers.put("Referer", ch.referrer);
                headers.put("Origin", origin);
            }
            headers.put("Accept", "*/*");
            headers.put("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7");
            headers.put("Connection", "keep-alive");

            DefaultHttpDataSource.Factory dsFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent(ua)
                    .setConnectTimeoutMs(20000)
                    .setReadTimeoutMs(30000)
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(headers);

            String urlLower = ch.url.toLowerCase();
            MediaSource mediaSource;

            if ("clearkey".equals(ch.drmType) && ch.drmKey != null && ch.drmKey.contains(":")) {
                String[] parts = ch.drmKey.split(":");
                String keyId = parts[0].trim();
                String key   = parts[1].trim();
                String clearKeyJsonStr = "{\"keys\":[{\"kty\":\"oct\",\"kid\":\""
                        + toBase64Url(hexToBytes(keyId)) + "\",\"k\":\""
                        + toBase64Url(hexToBytes(key)) + "\"}],\"type\":\"temporary\"}";
                byte[] clearKeyJson = clearKeyJsonStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                DefaultDrmSessionManager drmManager = new DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .build(new LocalMediaDrmCallback(clearKeyJson));

                mediaSource = new DashMediaSource.Factory(dsFactory)
                        .setDrmSessionManagerProvider(unused -> drmManager)
                        .createMediaSource(MediaItem.fromUri(ch.url));
            } else if (urlLower.contains(".mpd") || urlLower.contains("dash")) {
                mediaSource = new DashMediaSource.Factory(dsFactory)
                        .createMediaSource(MediaItem.fromUri(ch.url));
            } else if (urlLower.contains(".m3u8") || urlLower.contains("/hls/") || urlLower.contains("m3u8")) {
                mediaSource = new HlsMediaSource.Factory(dsFactory)
                        .createMediaSource(MediaItem.fromUri(ch.url));
            } else {
                mediaSource = new DefaultMediaSourceFactory(dsFactory)
                        .createMediaSource(MediaItem.fromUri(ch.url));
            }

            player.setMediaSource(mediaSource);
            player.prepare();
            player.play();
        } catch (Exception e) {
            tvLoadingMsg.setText("Gagal: " + e.getMessage());
        }

        channelAdapter.setActiveIndex(idx);
        showChInfo();
    }

    // ===== CHANNEL INFO OSD =====
    private void updateChInfo(Channel ch, int idx) {
        tvChNum.setText(String.valueOf(idx + 1));
        tvChName.setText(ch.name);
        tvChEpg.setText("Tidak ada informasi");
        tvChPlaylistName.setText(playlistName);

        if (ch.logoUrl != null && !ch.logoUrl.isEmpty()) {
            ivChLogo.setVisibility(View.VISIBLE);
            tvChLogoFallback.setVisibility(View.GONE);
            Glide.with(this)
                    .load(ch.logoUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.bg_ch_logo)
                    .error((android.graphics.drawable.Drawable) null)
                    .into(ivChLogo);
        } else {
            ivChLogo.setVisibility(View.GONE);
            tvChLogoFallback.setVisibility(View.VISIBLE);
            String initials = ch.name.isEmpty() ? "TV" : ch.name.substring(0, Math.min(2, ch.name.length())).toUpperCase();
            tvChLogoFallback.setText(initials);
        }
    }

    private void showChInfo() {
        // Animasi masuk dari kiri
        channelInfo.setTranslationX(-700f);
        channelInfo.setAlpha(1f);
        channelInfo.setVisibility(View.VISIBLE);
        channelInfo.animate()
                .translationX(20f)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        if (chInfoHideRunnable != null) handler.removeCallbacks(chInfoHideRunnable);
        chInfoHideRunnable = this::hideChInfo;
        handler.postDelayed(chInfoHideRunnable, 4000);
    }

    private void hideChInfo() {
        channelInfo.animate()
                .translationX(-700f)
                .alpha(0.8f)
                .setDuration(500)
                .withEndAction(() -> {
                    channelInfo.setVisibility(View.GONE);
                    channelInfo.setAlpha(1f);
                })
                .start();
    }

    private void toggleChInfo() {
        if (channelInfo.getVisibility() == View.VISIBLE) hideChInfo();
        else showChInfo();
    }

    // ===== PANEL DAFTAR CHANNEL =====
    private void openPanel() {
        if (panelOpen) return;
        panelOpen = true;

        chListBackdrop.setVisibility(View.VISIBLE);
        chListBackdrop.animate().alpha(1f).setDuration(250).start();

        categorySidebar.setVisibility(View.VISIBLE);
        categorySidebar.animate().translationX(0f).setDuration(280)
                .setInterpolator(new DecelerateInterpolator()).start();

        chListPanel.setVisibility(View.VISIBLE);
        chListPanel.animate().translationX(0f).setDuration(300)
                .setInterpolator(new DecelerateInterpolator()).start();

        // Jam di header panel menjadi 25sp (sudah di XML tv_panel_clock = 25sp)
        if (currentChannelIdx >= 0) rvChList.scrollToPosition(currentChannelIdx);
    }

    private void hidePanel() {
        if (!panelOpen) return;
        panelOpen = false;

        float sidebarW = 68f * getResources().getDisplayMetrics().density;
        float panelW   = chListPanel.getWidth();

        categorySidebar.animate().translationX(-sidebarW).setDuration(280)
                .withEndAction(() -> categorySidebar.setVisibility(View.INVISIBLE)).start();

        chListPanel.animate().translationX(-(sidebarW + panelW)).setDuration(300)
                .withEndAction(() -> chListPanel.setVisibility(View.INVISIBLE)).start();

        chListBackdrop.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> chListBackdrop.setVisibility(View.INVISIBLE)).start();
    }

    // ===== PANEL KATEGORI FULL =====
    private void openCategoryFull() {
        if (!panelOpen) openPanel();
        categoryFullOpen = true;

        categoryPanelFull.setVisibility(View.VISIBLE);
        categoryPanelFull.animate().translationX(0f).setDuration(300)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void closeCategoryFull() {
        if (!categoryFullOpen) return;
        categoryFullOpen = false;

        float panelW = categoryPanelFull.getWidth();
        categoryPanelFull.animate().translationX(-panelW).setDuration(280)
                .withEndAction(() -> categoryPanelFull.setVisibility(View.INVISIBLE)).start();
    }

    // ===== SWIPE =====
    private void showSwipeFeedback(boolean up) {
        TextView fb = up ? swipeFbUp : swipeFbDown;
        fb.animate().alpha(1f).setDuration(150).withEndAction(() ->
                fb.animate().alpha(0f).setDuration(300).start()).start();
    }

    private void showSwipeHint() {
        swipeHint.animate().alpha(1f).setDuration(500).start();
        if (swipeHintHideRunnable != null) handler.removeCallbacks(swipeHintHideRunnable);
        swipeHintHideRunnable = () -> swipeHint.animate().alpha(0f).setDuration(800).start();
        handler.postDelayed(swipeHintHideRunnable, 3500);
    }

    private void blackFlash() {
        blackFlash.setVisibility(View.VISIBLE);
        blackFlash.setAlpha(1f);
        blackFlash.animate().alpha(0f).setDuration(150)
                .withEndAction(() -> blackFlash.setVisibility(View.INVISIBLE)).start();
    }

    // ===== TV REMOTE =====
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                if (!panelOpen && !categoryFullOpen) { showSwipeFeedback(true); playChannel(currentChannelIdx + 1, true); }
                return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (!panelOpen && !categoryFullOpen) { showSwipeFeedback(false); playChannel(currentChannelIdx - 1, true); }
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                // Kiri 1x → buka daftar channel
                // Kiri 2x (panel sudah terbuka) → buka kategori
                if (!panelOpen && !categoryFullOpen) {
                    openPanel();
                } else if (panelOpen && !categoryFullOpen) {
                    openCategoryFull();
                }
                // Jika kategori sudah terbuka, kiri tidak ada aksi (gunakan BACK untuk kembali)
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // Kanan = tutup/kembali (kebalikan dari kiri)
                if (categoryFullOpen) closeCategoryFull();
                else if (panelOpen) hidePanel();
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!panelOpen && !categoryFullOpen) toggleChInfo();
                return true;

            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (categoryFullOpen) closeCategoryFull();
                else if (panelOpen) hidePanel();
                else finish();
                return true;

            default:
                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                    handleNumKey(keyCode - KeyEvent.KEYCODE_0);
                    return true;
                }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void handleNumKey(int num) {
        numBuffer += num;
        numOverlay.setText(numBuffer);
        numOverlay.setVisibility(View.VISIBLE);

        if (numClearRunnable != null) handler.removeCallbacks(numClearRunnable);
        numClearRunnable = () -> {
            int targetCh = Integer.parseInt(numBuffer) - 1;
            numBuffer = "";
            numOverlay.setVisibility(View.GONE);
            if (targetCh >= 0 && targetCh < channels.size()) playChannel(targetCh, true);
        };
        handler.postDelayed(numClearRunnable, 1500);
    }

    // ===== DRM HELPERS =====
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i+1), 16));
        return data;
    }

    private static String toBase64Url(byte[] input) {
        String b64 = android.util.Base64.encodeToString(input, android.util.Base64.NO_WRAP);
        return b64.replace('+', '-').replace('/', '_').replace("=", "");
    }

    // ===== LIFECYCLE =====
    @Override protected void onPause()   { super.onPause();   if (player != null) player.pause(); }
    @Override protected void onResume()  {
        super.onResume();
        if (player != null) player.play();
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (player != null) { player.release(); player = null; }
        handler.removeCallbacksAndMessages(null);
    }

    @Override public void onBackPressed() {
        if (categoryFullOpen) closeCategoryFull();
        else if (panelOpen) hidePanel();
        else finish();
    }
}

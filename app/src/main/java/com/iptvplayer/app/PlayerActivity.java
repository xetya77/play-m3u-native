package com.iptvplayer.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback;
import androidx.media3.exoplayer.hls.HlsMediaSource;
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

    // Player
    private PlayerView playerView;
    private ExoPlayer player;
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

    // Remote & number
    private View remoteGuide;
    private TextView numOverlay;

    // Backdrop
    private View chListBackdrop;

    // Sidebar icon strip (68dp, fixed)
    private LinearLayout categorySidebar;
    private LinearLayout catAll, catTv, catRadio, catMovie, catSettings;
    private ImageView icCatAll, icCatTv, icCatRadio, icCatMovie, icCatSettings;

    // Panel daftar channel
    private LinearLayout chListPanel;
    private TextView tvPanelTitle, tvResolution, tvBitrate;
    private androidx.recyclerview.widget.RecyclerView rvChList;

    // Panel kategori expanded (icon + teks, muncul di atas ch_list_panel)
    private LinearLayout categoryPanelFull;
    private LinearLayout catFullAll, catFullTv, catFullRadio, catFullMovie, catFullSettings;
    // icon di category_panel_full dihapus dari layout, hanya teks
    private TextView catFullAllText, catFullTvText, catFullRadioText, catFullMovieText, catFullSettingsText;

    // Swipe
    private View swipeHint;
    private TextView swipeFbUp, swipeFbDown;

    // State
    private List<Channel> channels = new ArrayList<>();
    private int currentChannelIdx = 0;
    private int playlistIdx = 0;
    private String playlistName = "";
    private boolean panelOpen = false;
    private boolean categoryFullOpen = false;
    private String activeCategoryFilter = "ALL";
    private String numBuffer = "";
    private boolean streamStarted = false;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable chInfoHideRunnable, numClearRunnable, swipeHintHideRunnable;
    private Runnable clockRunnable, dominoRunnable, bitrateRunnable;
    private int dominoPhase = 0;
    private long lastTapTime = 0;

    private PrefsManager prefs;
    private ChannelAdapter channelAdapter;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
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
        startBitrateUpdater();
        startDominoAnimation();
        playChannel(currentChannelIdx, false);
        showSwipeHint();
    }

    private void bindViews() {
        playerView        = findViewById(R.id.player_view);
        videoLoading      = findViewById(R.id.video_loading);
        blackFlash        = findViewById(R.id.black_flash);
        bar1              = findViewById(R.id.bar1);
        bar2              = findViewById(R.id.bar2);
        bar3              = findViewById(R.id.bar3);
        tvLoadingMsg      = findViewById(R.id.tv_loading_msg);
        tvClock           = findViewById(R.id.tv_clock);
        // tv_panel_clock dihapus dari layout, jam hanya di tv_clock

        channelInfo       = findViewById(R.id.channel_info);
        tvChNum           = findViewById(R.id.tv_ch_num);
        tvChName          = findViewById(R.id.tv_ch_name);
        tvChEpg           = findViewById(R.id.tv_ch_epg);
        tvChGroup         = findViewById(R.id.tv_ch_group);
        tvChPlaylistName  = findViewById(R.id.tv_ch_playlist_name);
        ivChLogo          = findViewById(R.id.iv_ch_logo);
        tvChLogoFallback  = findViewById(R.id.tv_ch_logo_fallback);

        remoteGuide       = findViewById(R.id.remote_guide);
        numOverlay        = findViewById(R.id.num_overlay);
        chListBackdrop    = findViewById(R.id.ch_list_backdrop);

        categorySidebar   = findViewById(R.id.category_sidebar);
        catAll            = findViewById(R.id.cat_all);
        catTv             = findViewById(R.id.cat_tv);
        catRadio          = findViewById(R.id.cat_radio);
        catMovie          = findViewById(R.id.cat_movie);
        catSettings       = findViewById(R.id.cat_settings);
        icCatAll          = findViewById(R.id.ic_cat_all);
        icCatTv           = findViewById(R.id.ic_cat_tv);
        icCatRadio        = findViewById(R.id.ic_cat_radio);
        icCatMovie        = findViewById(R.id.ic_cat_movie);
        icCatSettings     = findViewById(R.id.ic_cat_settings);

        chListPanel       = findViewById(R.id.ch_list_panel);
        tvPanelTitle      = findViewById(R.id.tv_panel_title);
        tvResolution      = findViewById(R.id.tv_resolution);
        tvBitrate         = findViewById(R.id.tv_bitrate);
        rvChList          = findViewById(R.id.rv_ch_list);

        categoryPanelFull    = findViewById(R.id.category_panel_full);
        catFullAll           = findViewById(R.id.cat_full_all);
        catFullTv            = findViewById(R.id.cat_full_tv);
        catFullRadio         = findViewById(R.id.cat_full_radio);
        catFullMovie         = findViewById(R.id.cat_full_movie);
        catFullSettings      = findViewById(R.id.cat_full_settings);
        // icon di category_panel_full sudah dihapus dari layout XML
        catFullAllText       = findViewById(R.id.cat_full_all_text);
        catFullTvText        = findViewById(R.id.cat_full_tv_text);
        catFullRadioText     = findViewById(R.id.cat_full_radio_text);
        catFullMovieText     = findViewById(R.id.cat_full_movie_text);
        catFullSettingsText  = findViewById(R.id.cat_full_settings_text);

        swipeHint         = findViewById(R.id.swipe_hint);
        swipeFbUp         = findViewById(R.id.swipe_fb_up);
        swipeFbDown       = findViewById(R.id.swipe_fb_down);
    }

    // ===== CLOCK =====
    private void startClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        clockRunnable = new Runnable() {
            @Override public void run() {
                tvClock.setText(sdf.format(new Date()));
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(clockRunnable);
    }

    // ===== BITRATE REAL-TIME =====
    private void startBitrateUpdater() {
        bitrateRunnable = new Runnable() {
            @Override public void run() {
                try {
                    if (player != null && player.isPlaying()) {
                        long bitsPerSecond = player.getBandwidthMeter().getBitrateEstimate();
                        if (tvBitrate != null) {
                            if (bitsPerSecond > 0) {
                                long kbps = bitsPerSecond / 1000;
                                tvBitrate.setText(kbps + " kb/s");
                            }
                        }
                        // Update resolusi juga
                        Format vf = player.getVideoFormat();
                        if (vf != null && tvResolution != null) {
                            tvResolution.setText(vf.width + "x" + vf.height);
                        }
                    }
                } catch (Exception ignored) {}
                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(bitrateRunnable, 1000);
    }

    // ===== DOMINO ANIMATION =====
    private void startDominoAnimation() {
        dominoRunnable = new Runnable() {
            @Override public void run() {
                if (videoLoading.getVisibility() == View.VISIBLE) animateDomino();
                dominoPhase = (dominoPhase + 1) % 6;
                handler.postDelayed(this, 200);
            }
        };
        handler.postDelayed(dominoRunnable, 200);
    }
    private void animateDomino() {
        float[][] s = {{0.4f,0.7f,1.0f},{0.6f,1.0f,0.7f},{1.0f,0.7f,0.4f},
                       {0.7f,0.4f,0.7f},{0.4f,0.7f,1.0f},{0.7f,1.0f,0.7f}};
        float[] p = s[dominoPhase];
        if (bar1 != null) bar1.animate().alpha(p[0]).setDuration(180).start();
        if (bar2 != null) bar2.animate().alpha(p[1]).setDuration(180).start();
        if (bar3 != null) bar3.animate().alpha(p[2]).setDuration(180).start();
    }

    // ===== PLAYER =====
    private void setupPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setUseController(false);
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    videoLoading.setBackgroundColor(0x00000000);
                    videoLoading.setVisibility(View.VISIBLE);
                    tvLoadingMsg.setText("Memuat...");
                } else if (state == Player.STATE_READY) {
                    videoLoading.setVisibility(View.GONE);
                    if (!streamStarted) onFirstStreamReady();
                    streamStarted = true;
                } else if (state == Player.STATE_ENDED) {
                    videoLoading.setBackgroundColor(0x00000000);
                    videoLoading.setVisibility(View.VISIBLE);
                    tvLoadingMsg.setText("Stream berakhir...");
                }
            }
            @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                videoLoading.setVisibility(View.VISIBLE);
                tvLoadingMsg.setText(getErrorMessage(error));
                handler.postDelayed(() -> { if (!isFinishing()) playChannel(currentChannelIdx, false); }, 3000);
            }
        });
    }
    private void onFirstStreamReady() {
        if (remoteGuide != null)
            remoteGuide.animate().alpha(0f).setDuration(800)
                    .withEndAction(() -> remoteGuide.setVisibility(View.GONE)).start();
        // Jam opacity 50% saat menonton (panel tutup)
        tvClock.animate().alpha(0.5f).setDuration(600).start();
    }
    private String getErrorMessage(androidx.media3.common.PlaybackException e) {
        int c = e.errorCode;
        if (c == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) return "Tidak ada koneksi internet";
        if (c == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) return "Koneksi timeout, coba lagi...";
        if (c == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return "Stream tidak tersedia (HTTP error)";
        if (c == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) return "Format tidak didukung";
        return "Gagal memutar stream";
    }

    // ===== CHANNEL ADAPTER =====
    private void setupChannelAdapter() {
        tvPanelTitle.setText(playlistName.isEmpty() ? "SEMUA SALURAN" : playlistName.toUpperCase());
        channelAdapter = new ChannelAdapter(idx -> { playChannel(idx, true); hidePanel(); });
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
                float dX = e2.getX() - e1.getX();
                float dY = e2.getY() - e1.getY();
                if (Math.abs(dX) > Math.abs(dY)) {
                    if (dX > 80 && Math.abs(vX) > 100) {
                        // Swipe KANAN:
                        // - Belum ada panel → buka daftar channel
                        // - Daftar channel sudah terbuka → buka kategori
                        if (!panelOpen && !categoryFullOpen)       openPanel();
                        else if (panelOpen && !categoryFullOpen)   openCategoryFull();
                        return true;
                    } else if (dX < -80 && Math.abs(vX) > 100) {
                        // Swipe KIRI = kembali/tutup
                        if (categoryFullOpen) closeCategoryFull();
                        else if (panelOpen)   hidePanel();
                        return true;
                    }
                } else if (Math.abs(dY) > 80 && Math.abs(vY) > 100) {
                    if (!panelOpen && !categoryFullOpen) {
                        if (dY < 0) { showSwipeFeedback(true);  playChannel(currentChannelIdx + 1, true); }
                        else        { showSwipeFeedback(false); playChannel(currentChannelIdx - 1, true); }
                    }
                    return true;
                }
                return false;
            }
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                long now = System.currentTimeMillis();
                if (now - lastTapTime < 400) {
                    lastTapTime = 0;
                    if (!categoryFullOpen) openCategoryFull(); else closeCategoryFull();
                } else {
                    lastTapTime = now;
                    handler.postDelayed(() -> {
                        if (lastTapTime != 0) {
                            lastTapTime = 0;
                            if (categoryFullOpen)      closeCategoryFull();
                            else if (panelOpen)        hidePanel();
                            else                       toggleChInfo();
                        }
                    }, 420);
                }
                return true;
            }
        });
        playerView.setOnTouchListener((v, e) -> { gestureDetector.onTouchEvent(e); return true; });

        // Swipe di backdrop → tutup panel
        chListBackdrop.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                if (categoryFullOpen) closeCategoryFull(); else if (panelOpen) hidePanel();
            }
            return true;
        });

        // Swipe KANAN di panel daftar channel → buka kategori
        // Swipe KIRI di panel daftar channel → tutup panel
        GestureDetector panelSwipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (e1 == null || e2 == null) return false;
                float dX = e2.getX() - e1.getX();
                if (Math.abs(dX) > 80 && Math.abs(vX) > 100) {
                    if (dX > 0) {
                        // Swipe kanan di daftar channel → buka panel kategori
                        if (!categoryFullOpen) openCategoryFull();
                    } else {
                        // Swipe kiri → tutup panel
                        if (categoryFullOpen) closeCategoryFull();
                        else hidePanel();
                    }
                    return true;
                }
                return false;
            }
        });
        chListPanel.setOnTouchListener((v, e) -> { panelSwipe.onTouchEvent(e); return false; });

        // Swipe KIRI di panel kategori → kembali ke daftar channel
        GestureDetector catSwipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (e1 == null || e2 == null) return false;
                float dX = e2.getX() - e1.getX();
                if (dX < -80 && Math.abs(vX) > 100) {
                    closeCategoryFull();
                    return true;
                }
                return false;
            }
        });
        categoryPanelFull.setOnTouchListener((v, e) -> { catSwipe.onTouchEvent(e); return false; });
    }

    // ===== KATEGORI =====
    private void setupCategoryListeners() {
        // Icon sidebar → buka kategori expanded (jika panel sudah terbuka)
        catAll.setOnClickListener(v -> { if (panelOpen) openCategoryFull(); });
        catTv.setOnClickListener(v -> { if (panelOpen) openCategoryFull(); });
        catRadio.setOnClickListener(v -> { if (panelOpen) openCategoryFull(); });
        catMovie.setOnClickListener(v -> { if (panelOpen) openCategoryFull(); });
        catSettings.setOnClickListener(v -> finish());

        // Item di panel kategori expanded → pilih dan kembali ke daftar channel
        catFullAll.setOnClickListener(v -> selectCategory("ALL"));
        catFullTv.setOnClickListener(v -> selectCategory("TV"));
        catFullRadio.setOnClickListener(v -> selectCategory("RADIO"));
        catFullMovie.setOnClickListener(v -> selectCategory("FILM"));
        catFullSettings.setOnClickListener(v -> finish());
    }

    /** Pilih kategori, update styling, filter channel, tutup panel kategori */
    private void selectCategory(String cat) {
        activeCategoryFilter = cat;

        // Update sidebar icon — highlight + warna icon sesuai aktif/tidak
        updateSidebarItemStyle(catAll,      icCatAll,      cat.equals("ALL"));
        updateSidebarItemStyle(catTv,       icCatTv,       cat.equals("TV"));
        updateSidebarItemStyle(catRadio,    icCatRadio,    cat.equals("RADIO"));
        updateSidebarItemStyle(catMovie,    icCatMovie,    cat.equals("FILM"));
        updateSidebarItemStyle(catSettings, icCatSettings, false);

        // Update styling panel kategori
        updateCategoryItemStyle(catFullAll, catFullAllText, cat.equals("ALL"));
        updateCategoryItemStyle(catFullTv,  catFullTvText,  cat.equals("TV"));
        updateCategoryItemStyle(catFullRadio, catFullRadioText, cat.equals("RADIO"));
        updateCategoryItemStyle(catFullMovie, catFullMovieText, cat.equals("FILM"));
        updateCategoryItemStyle(catFullSettings, catFullSettingsText, false);

        // Filter daftar channel
        channelAdapter.applyGroupFilter(cat);

        // Tutup panel kategori → kembali tampilkan daftar channel
        closeCategoryFull();
    }

    private void updateCategoryItemStyle(LinearLayout item, TextView text, boolean active) {
        if (active) {
            item.setBackground(getDrawable(R.drawable.bg_category_item_active));
            text.setTextColor(0xFF000000);
        } else {
            item.setBackground(null);
            text.setTextColor(0xCCFFFFFF);
        }
    }

    private void updateSidebarItemStyle(LinearLayout item, ImageView icon, boolean active) {
        if (active) {
            item.setBackground(getDrawable(R.drawable.bg_category_item_active));
            icon.setAlpha(1.0f);
            icon.setColorFilter(0xFF000000, android.graphics.PorterDuff.Mode.SRC_IN);
        } else {
            item.setBackground(null);
            icon.setAlpha(0.5f);
            icon.clearColorFilter();
        }
    }

    // ===== PANEL DAFTAR CHANNEL =====
    private void openPanel() {
        if (panelOpen) return;
        panelOpen = true;

        float dp = getResources().getDisplayMetrics().density;

        chListBackdrop.setVisibility(View.VISIBLE);
        chListBackdrop.animate().alpha(1f).setDuration(250).start();

        // Jam opacity 100% saat panel terbuka
        tvClock.animate().alpha(1.0f).setDuration(250).start();

        // Sidebar icon masuk dari kiri: reset posisi ke -68dp lalu animasi ke 0
        categorySidebar.setVisibility(View.VISIBLE);
        categorySidebar.setTranslationX(-68f * dp);
        categorySidebar.animate().translationX(0f).setDuration(280)
                .setInterpolator(new DecelerateInterpolator()).start();

        // Panel daftar channel masuk dari kiri: reset ke -448dp lalu animasi ke 0
        chListPanel.setVisibility(View.VISIBLE);
        chListPanel.setAlpha(1f);
        chListPanel.setTranslationX(-448f * dp);
        chListPanel.animate().translationX(0f).setDuration(300)
                .setInterpolator(new DecelerateInterpolator()).start();

        if (currentChannelIdx >= 0) rvChList.scrollToPosition(currentChannelIdx);
    }

    private void hidePanel() {
        if (!panelOpen) return;
        panelOpen = false;
        categoryFullOpen = false;

        float dp = getResources().getDisplayMetrics().density;

        // Jam kembali opacity 50% saat panel ditutup
        tvClock.animate().alpha(0.5f).setDuration(300).start();

        categorySidebar.animate().translationX(-68f * dp).setDuration(280)
                .withEndAction(() -> categorySidebar.setVisibility(View.INVISIBLE)).start();

        chListPanel.animate().translationX(-448f * dp).setDuration(300)
                .withEndAction(() -> chListPanel.setVisibility(View.INVISIBLE)).start();

        // Tutup panel kategori jika sedang terbuka
        if (categoryPanelFull.getVisibility() == View.VISIBLE) {
            categoryPanelFull.animate().translationX(-240f * dp).setDuration(250)
                    .withEndAction(() -> categoryPanelFull.setVisibility(View.INVISIBLE)).start();
        }

        chListBackdrop.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> chListBackdrop.setVisibility(View.INVISIBLE)).start();
    }

    // ===== PANEL KATEGORI EXPANDED =====
    private void openCategoryFull() {
        if (categoryFullOpen) return;
        categoryFullOpen = true;

        // Jika panel belum terbuka, buka dulu
        if (!panelOpen) openPanel();

        float dp = getResources().getDisplayMetrics().density;

        // Sidebar tetap terlihat di posisinya — tidak disembunyikan
        // Panel kategori expanded slide masuk dari kiri (mulai dari -240dp → 0)
        // Layout: sidebar(68dp) | category_panel_full(240dp) | ch_list_panel
        // category_panel_full sudah punya layout_marginLeft="68dp" jadi posisi sudah benar
        categoryPanelFull.setVisibility(View.VISIBLE);
        categoryPanelFull.setAlpha(1f);
        categoryPanelFull.setTranslationX(-240f * dp);
        categoryPanelFull.animate().translationX(0f).setDuration(280)
                .setInterpolator(new DecelerateInterpolator()).start();

        // Geser ch_list_panel ke kanan sebesar 240dp agar tidak tertimpa panel kategori
        chListPanel.animate().translationX(240f * dp).setDuration(280)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void closeCategoryFull() {
        if (!categoryFullOpen) return;
        categoryFullOpen = false;

        float dp = getResources().getDisplayMetrics().density;

        // Panel kategori slide keluar ke kiri
        categoryPanelFull.animate().translationX(-240f * dp).setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> categoryPanelFull.setVisibility(View.INVISIBLE)).start();

        // Kembalikan ch_list_panel ke posisi semula (translationX = 0)
        chListPanel.animate().translationX(0f).setDuration(280)
                .setInterpolator(new DecelerateInterpolator()).start();
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
            String ua = (ch.userAgent != null && !ch.userAgent.isEmpty()) ? ch.userAgent
                    : "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 Chrome/96.0 Safari/537.36";
            Map<String, String> headers = new HashMap<>();
            if (ch.referrer != null && !ch.referrer.isEmpty()) {
                headers.put("Referer", ch.referrer);
                headers.put("Origin", ch.referrer.replaceAll("/$", ""));
            }
            headers.put("Accept", "*/*");
            headers.put("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7");
            headers.put("Connection", "keep-alive");
            DefaultHttpDataSource.Factory dsFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent(ua).setConnectTimeoutMs(20000).setReadTimeoutMs(30000)
                    .setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(headers);
            String urlLower = ch.url.toLowerCase();
            MediaSource mediaSource;
            if ("clearkey".equals(ch.drmType) && ch.drmKey != null && ch.drmKey.contains(":")) {
                String[] parts = ch.drmKey.split(":");
                String ckJson = "{\"keys\":[{\"kty\":\"oct\",\"kid\":\""
                        + toBase64Url(hexToBytes(parts[0].trim())) + "\",\"k\":\""
                        + toBase64Url(hexToBytes(parts[1].trim())) + "\"}],\"type\":\"temporary\"}";
                DefaultDrmSessionManager drm = new DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .build(new LocalMediaDrmCallback(ckJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                mediaSource = new DashMediaSource.Factory(dsFactory)
                        .setDrmSessionManagerProvider(u -> drm)
                        .createMediaSource(MediaItem.fromUri(ch.url));
            } else if (urlLower.contains(".mpd") || urlLower.contains("dash")) {
                mediaSource = new DashMediaSource.Factory(dsFactory).createMediaSource(MediaItem.fromUri(ch.url));
            } else if (urlLower.contains(".m3u8") || urlLower.contains("/hls/") || urlLower.contains("m3u8")) {
                mediaSource = new HlsMediaSource.Factory(dsFactory).createMediaSource(MediaItem.fromUri(ch.url));
            } else {
                mediaSource = new DefaultMediaSourceFactory(dsFactory).createMediaSource(MediaItem.fromUri(ch.url));
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
            Glide.with(this).load(ch.logoUrl).diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.bg_ch_logo)
                    .error((android.graphics.drawable.Drawable)null).into(ivChLogo);
        } else {
            ivChLogo.setVisibility(View.GONE);
            tvChLogoFallback.setVisibility(View.VISIBLE);
            tvChLogoFallback.setText(ch.name.isEmpty() ? "TV"
                    : ch.name.substring(0, Math.min(2, ch.name.length())).toUpperCase());
        }
    }
    private void showChInfo() {
        channelInfo.setTranslationX(-700f);
        channelInfo.setAlpha(1f);
        channelInfo.setVisibility(View.VISIBLE);
        channelInfo.animate().translationX(20f).setDuration(400)
                .setInterpolator(new DecelerateInterpolator()).start();
        if (chInfoHideRunnable != null) handler.removeCallbacks(chInfoHideRunnable);
        chInfoHideRunnable = this::hideChInfo;
        handler.postDelayed(chInfoHideRunnable, 4000);
    }
    private void hideChInfo() {
        channelInfo.animate().translationX(-700f).alpha(0.8f).setDuration(500)
                .withEndAction(() -> { channelInfo.setVisibility(View.GONE); channelInfo.setAlpha(1f); }).start();
    }
    private void toggleChInfo() {
        if (channelInfo.getVisibility() == View.VISIBLE) hideChInfo(); else showChInfo();
    }

    // ===== SWIPE =====
    private void showSwipeFeedback(boolean up) {
        TextView fb = up ? swipeFbUp : swipeFbDown;
        fb.animate().alpha(1f).setDuration(150)
                .withEndAction(() -> fb.animate().alpha(0f).setDuration(300).start()).start();
    }
    private void showSwipeHint() {
        swipeHint.animate().alpha(1f).setDuration(500).start();
        if (swipeHintHideRunnable != null) handler.removeCallbacks(swipeHintHideRunnable);
        swipeHintHideRunnable = () -> swipeHint.animate().alpha(0f).setDuration(800).start();
        handler.postDelayed(swipeHintHideRunnable, 3500);
    }
    private void blackFlash() {
        blackFlash.setVisibility(View.VISIBLE); blackFlash.setAlpha(1f);
        blackFlash.animate().alpha(0f).setDuration(150)
                .withEndAction(() -> blackFlash.setVisibility(View.INVISIBLE)).start();
    }

    // ===== TV REMOTE =====
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                if (!panelOpen && !categoryFullOpen) {
                    showSwipeFeedback(true); playChannel(currentChannelIdx + 1, true);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (!panelOpen && !categoryFullOpen) {
                    showSwipeFeedback(false); playChannel(currentChannelIdx - 1, true);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                // Tekan 1x → buka daftar channel
                // Tekan 2x (panel sudah buka) → buka kategori
                if (!panelOpen && !categoryFullOpen)      openPanel();
                else if (panelOpen && !categoryFullOpen)  openCategoryFull();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // Kanan = tutup/kembali
                if (categoryFullOpen) closeCategoryFull();
                else if (panelOpen)   hidePanel();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!panelOpen && !categoryFullOpen) toggleChInfo();
                return true;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (categoryFullOpen) closeCategoryFull();
                else if (panelOpen)   hidePanel();
                else finish();
                return true;
            default:
                if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                    handleNumKey(keyCode - KeyEvent.KEYCODE_0); return true;
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
            int t = Integer.parseInt(numBuffer) - 1;
            numBuffer = ""; numOverlay.setVisibility(View.GONE);
            if (t >= 0 && t < channels.size()) playChannel(t, true);
        };
        handler.postDelayed(numClearRunnable, 1500);
    }

    // ===== DRM =====
    private static byte[] hexToBytes(String hex) {
        int len = hex.length(); byte[] data = new byte[len/2];
        for (int i = 0; i < len; i += 2)
            data[i/2] = (byte)((Character.digit(hex.charAt(i),16)<<4)+Character.digit(hex.charAt(i+1),16));
        return data;
    }
    private static String toBase64Url(byte[] input) {
        return android.util.Base64.encodeToString(input, android.util.Base64.NO_WRAP)
                .replace('+','-').replace('/','_').replace("=","");
    }

    // ===== LIFECYCLE =====
    @Override protected void onPause()  { super.onPause();  if (player != null) player.pause(); }
    @Override protected void onResume() {
        super.onResume(); if (player != null) player.play();
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
        if (categoryFullOpen) closeCategoryFull(); else if (panelOpen) hidePanel(); else finish();
    }
}

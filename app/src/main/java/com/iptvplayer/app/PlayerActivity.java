package com.iptvplayer.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
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
import androidx.recyclerview.widget.RecyclerView;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.PlayerView;
import androidx.media3.common.Tracks;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.DefaultLoadControl;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import android.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

@OptIn(markerClass = UnstableApi.class)
@android.annotation.SuppressLint("SetJavaScriptEnabled")
public class PlayerActivity extends AppCompatActivity {

    // Player
    private PlayerView playerView;
    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private WebView youtubeWebView;
    private boolean isYouTubeMode = false;
    // Playlist YouTube
    private static final String YT_API_KEY = "AIzaSyATLSrkqWdfkdzYN8hciMcQi9yYxcrL184";
    private List<String> ytPlaylistVideoIds = new ArrayList<>();
    private int ytPlaylistCurrentIdx = 0;
    private String ytPlaylistId = null; // null = bukan playlist mode
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
    private TextView numOverlay;

    // Backdrop
    private View chListBackdrop;

    // Sidebar icon strip (68dp, fixed)
    private LinearLayout categorySidebar;
    private LinearLayout catAll, catTv, catRadio, catMovie, catSettings;
    private ImageView icCatAll, icCatTv, icCatRadio, icCatMovie, icCatSettings;

    // Panel daftar channel
    private LinearLayout chListPanel;
    private TextView tvPanelTitle, tvResolution, tvBitrate, tvQuotaUsed;
    private androidx.recyclerview.widget.RecyclerView rvChList;

    // Panel kategori expanded (icon + teks, muncul di atas ch_list_panel)
    private LinearLayout categoryPanelFull;
    private LinearLayout catFullAll, catFullTv, catFullRadio, catFullMovie, catFullSettings;
    private ImageView catFullAllIcon, catFullTvIcon, catFullRadioIcon, catFullMovieIcon, catFullSettingsIcon;
    // icon di category_panel_full dihapus dari layout, hanya teks
    private TextView catFullAllText, catFullTvText, catFullRadioText, catFullMovieText, catFullSettingsText;

    // Swipe
    private View swipeHint;
    private TextView swipeFbUp, swipeFbDown;
    // Group title panel
    // Panel kanan — group title (mirror panel kiri, tanpa sidebar)
    private View groupBackdrop;
    private LinearLayout groupListPanel;
    private LinearLayout groupChannelPanel;
    private RecyclerView rvGroupTitles;
    private RecyclerView rvGroupChannels;
    private TextView tvGroupClock;
    private TextView tvGroupChannelClock;
    private TextView tvGroupChannelTitle;
    private boolean groupPanelOpen = false;
    private boolean groupChannelOpen = false;
    private String activeGroupFilter = null;

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
        startDominoAnimation();
        playChannel(currentChannelIdx, false);
        showSwipeHint();
    }

    private void bindViews() {
        playerView        = findViewById(R.id.player_view);
        youtubeWebView         = findViewById(R.id.youtube_webview);
        youtubeFullscreenContainer = findViewById(R.id.youtube_fullscreen_container);
        youtubeWebView.addJavascriptInterface(new YouTubeAndroidInterface(), "AndroidInterface");
        setupYouTubeWebView();
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
        tvQuotaUsed       = findViewById(R.id.tv_quota_used);
        rvChList          = findViewById(R.id.rv_ch_list);

        categoryPanelFull    = findViewById(R.id.category_panel_full);
        catFullAll           = findViewById(R.id.cat_full_all);
        catFullTv            = findViewById(R.id.cat_full_tv);
        catFullRadio         = findViewById(R.id.cat_full_radio);
        catFullMovie         = findViewById(R.id.cat_full_movie);
        catFullSettings      = findViewById(R.id.cat_full_settings);

        catFullAllIcon       = findViewById(R.id.cat_full_all_icon);
        catFullTvIcon        = findViewById(R.id.cat_full_tv_icon);
        catFullRadioIcon     = findViewById(R.id.cat_full_radio_icon);
        catFullMovieIcon     = findViewById(R.id.cat_full_movie_icon);
        catFullSettingsIcon  = findViewById(R.id.cat_full_settings_icon);
        // icon di category_panel_full sudah dihapus dari layout XML
        catFullAllText       = findViewById(R.id.cat_full_all_text);
        catFullTvText        = findViewById(R.id.cat_full_tv_text);
        catFullRadioText     = findViewById(R.id.cat_full_radio_text);
        catFullMovieText     = findViewById(R.id.cat_full_movie_text);
        catFullSettingsText  = findViewById(R.id.cat_full_settings_text);

        swipeHint         = findViewById(R.id.swipe_hint);
        swipeFbUp         = findViewById(R.id.swipe_fb_up);
        swipeFbDown       = findViewById(R.id.swipe_fb_down);
        groupBackdrop        = findViewById(R.id.group_backdrop);
        groupListPanel       = findViewById(R.id.group_list_panel);
        groupChannelPanel    = findViewById(R.id.group_channel_panel);
        rvGroupTitles        = findViewById(R.id.rv_group_titles);
        rvGroupChannels      = findViewById(R.id.rv_group_channels);
        tvGroupClock         = findViewById(R.id.tv_group_clock);
        tvGroupChannelClock  = findViewById(R.id.tv_group_channel_clock);
        tvGroupChannelTitle  = findViewById(R.id.tv_group_channel_title);
    }

    // ===== CLOCK =====
    private void startClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        clockRunnable = new Runnable() {
            @Override public void run() {
                String t = sdf.format(new Date());
                tvClock.setText(t);
                // POIN 7: saat panel kanan terbuka, jam dipindah ke header panel kanan
                if (groupPanelOpen) {
                    tvClock.setVisibility(View.INVISIBLE);
                    if (tvGroupClock != null) tvGroupClock.setText(t);
                    if (tvGroupChannelClock != null) tvGroupChannelClock.setText(t);
                } else {
                    tvClock.setVisibility(View.VISIBLE);
                    if (tvGroupClock != null) tvGroupClock.setText("");
                    if (tvGroupChannelClock != null) tvGroupChannelClock.setText("");
                }
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(clockRunnable);
    }

    // ===== BITRATE REAL-TIME =====
    private final AtomicLong bytesAccumulator = new AtomicLong(0);
    private long lastByteSnapshot = 0;
    // Moving average — simpan bytes dari 4 window terakhir (masing-masing 1 detik)
    private final long[] bitrateWindow = new long[4];
    private int bitrateWindowIdx = 0;

    // TransferListener sebagai field — dipasang sekali, aktif terus
    private final TransferListener transferListener = new TransferListener() {
        @Override public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {}
        @Override public void onTransferStart(DataSource source, DataSpec dataSpec, boolean isNetwork) {}
        @Override public void onBytesTransferred(DataSource source, DataSpec dataSpec, boolean isNetwork, int bytesTransferred) {
            if (isNetwork) bytesAccumulator.addAndGet(bytesTransferred);
        }
        @Override public void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {}
    };

    private void startBitrateUpdater() {
        if (bitrateRunnable != null) handler.removeCallbacks(bitrateRunnable);
        lastByteSnapshot = bytesAccumulator.get();
        // Reset window rata-rata
        for (int i = 0; i < bitrateWindow.length; i++) bitrateWindow[i] = 0;
        bitrateWindowIdx = 0;

        bitrateRunnable = new Runnable() {
            @Override public void run() {
                try {
                    long totalBytes = bytesAccumulator.get();
                    long bytesThisSecond = totalBytes - lastByteSnapshot;
                    lastByteSnapshot = totalBytes;

                    // Simpan ke window, hitung rata-rata 4 detik terakhir
                    bitrateWindow[bitrateWindowIdx % bitrateWindow.length] = bytesThisSecond;
                    bitrateWindowIdx++;
                    long sumBytes = 0;
                    int validCount = Math.min(bitrateWindowIdx, bitrateWindow.length);
                    for (int i = 0; i < validCount; i++) sumBytes += bitrateWindow[i];
                    long avgBytesPerSec = sumBytes / validCount;
                    long kbps = (avgBytesPerSec * 8) / 1000;

                    if (tvBitrate != null) {
                        tvBitrate.setText(kbps + " kb/s");
                    }

                    // Kuota total channel ini
                    if (tvQuotaUsed != null) {
                        String quota;
                        if (totalBytes < 1024L * 1024) {
                            quota = totalBytes / 1024 + " KB";
                        } else if (totalBytes < 1024L * 1024 * 1024) {
                            quota = String.format(Locale.getDefault(), "%.1f MB", totalBytes / (1024.0 * 1024));
                        } else {
                            quota = String.format(Locale.getDefault(), "%.2f GB", totalBytes / (1024.0 * 1024 * 1024));
                        }
                        tvQuotaUsed.setText(quota);
                    }

                    // Resolusi
                    if (player != null) {
                        Format vf = player.getVideoFormat();
                        if (vf != null && tvResolution != null) {
                            tvResolution.setText(vf.width + "x" + vf.height);
                        }
                    }
                } catch (Exception ignored) {}
                handler.postDelayed(this, 1000); // 1 detik per window
            }
        };
        handler.post(bitrateRunnable);
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

    // ===== YOUTUBE PLAYER =====

    /** Ekstrak YouTube video/live ID dari berbagai format URL YouTube */
    private String extractYouTubeId(String url) {
        // Format: youtu.be/ID
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("youtu\\.be/([a-zA-Z0-9_-]{11})")
            .matcher(url);
        if (m.find()) return m.group(1);

        // Format: youtube.com/watch?v=ID
        m = java.util.regex.Pattern
            .compile("[?&]v=([a-zA-Z0-9_-]{11})")
            .matcher(url);
        if (m.find()) return m.group(1);

        // Format: youtube.com/live/ID atau /embed/ID atau /shorts/ID
        m = java.util.regex.Pattern
            .compile("youtube\\.com/(?:live|embed|shorts)/([a-zA-Z0-9_-]{11})")
            .matcher(url);
        if (m.find()) return m.group(1);

        return null;
    }

    /** Ekstrak playlist ID dari URL YouTube */
    private String extractYouTubePlaylistId(String url) {
        if (url == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("[?&]list=([a-zA-Z0-9_-]+)")
            .matcher(url);
        return m.find() ? m.group(1) : null;
    }

    /** Cek apakah URL adalah YouTube (video atau playlist) */
    private boolean isYouTubeUrl(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        if (!lower.contains("youtube.com") && !lower.contains("youtu.be")) return false;
        // Video biasa
        if (extractYouTubeId(url) != null) return true;
        // Playlist murni (tidak ada v=ID, hanya list=PL...)
        if (lower.contains("playlist?list=") || lower.contains("&list=") || lower.contains("?list=")) return true;
        return false;
    }

    /** Setup WebView sekali saat onCreate */
    private void setupYouTubeWebView() {
        WebSettings ws = youtubeWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
        ws.setSupportZoom(false);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        // WAJIB: Pakai UA desktop Chrome — YouTube blokir embed jika deteksi WebView
        // Ciri WebView yang diblokir: ada "; wv" di UA atau tidak ada "Chrome/"
        // Desktop UA = tidak ada "wv", tidak ada "Mobile" → YouTube izinkan embed
        // WAJIB: Windows desktop Chrome UA
        // YouTube blokir embed jika UA mengandung "Android", "wv", atau tidak ada "Chrome/"
        // Desktop UA = paling aman, tidak pernah diblokir YouTube untuk embed
        ws.setUserAgentString(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");

        youtubeWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                youtubeWebView.setVisibility(View.GONE);
                if (youtubeFullscreenContainer != null) {
                    youtubeFullscreenContainer.setVisibility(View.VISIBLE);
                    youtubeFullscreenContainer.addView(view);
                    youtubeFullscreenView = view;
                    youtubeFullscreenCallback = callback;
                }
            }
            @Override
            public void onHideCustomView() {
                if (youtubeFullscreenContainer != null && youtubeFullscreenView != null) {
                    youtubeFullscreenContainer.removeView(youtubeFullscreenView);
                    youtubeFullscreenContainer.setVisibility(View.GONE);
                    youtubeFullscreenView = null;
                    youtubeFullscreenCallback = null;
                }
                youtubeWebView.setVisibility(View.VISIBLE);
            }
        });

        youtubeWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(android.webkit.WebView view, String url) {
                // Semua navigasi tetap di WebView
                return false;
            }
            @Override
            public void onPageFinished(android.webkit.WebView view, String url) {
                if (!isYouTubeMode) return;
                if (url == null || url.equals("about:blank")) return;

                // JS komprehensif untuk halaman youtube.com/watch:
                // 1. Sembunyikan semua UI (header, sidebar, comments, ads, controls)
                // 2. Paksa video fill layar penuh
                // 3. Klik tombol fullscreen YouTube secara otomatis
                // 4. Play video jika belum playing
                String js =
                    "(function(){" +
                    // === INJECT CSS: sembunyikan semua elemen non-video ===
                    "var s=document.getElementById('__hyt__');" +
                    "if(!s){s=document.createElement('style');s.id='__hyt__';document.head.appendChild(s);}" +
                    "s.textContent=" +
                    // Sembunyikan header, sidebar, comments, ads, related, footer
                    "'#masthead,#masthead-container,ytd-masthead," +
                    "#secondary,#related,ytd-watch-next-secondary-results-renderer," +
                    "#comments,ytd-comments,#below,#info,#meta,#description," +
                    "ytd-merch-shelf-renderer,ytd-item-section-renderer," +
                    "#chat,ytd-live-chat-frame,tp-yt-paper-dialog," +
                    ".ytp-chrome-top,.ytp-chrome-bottom,.ytp-gradient-top,.ytp-gradient-bottom," +
                    ".ytp-watermark,.ytp-share-button,.ytp-watch-later-button," +
                    ".ytp-pause-overlay,.ytp-endscreen-content,.ytp-ce-element," +
                    ".ytp-unmute,.ytp-mute-button,.iv-branding,.annotation," +
                    "#movie_player .ytp-chrome-controls," +
                    "ytd-app>*:not(#content),#page-manager>*:not(ytd-watch-flexy)," +
                    "ytd-watch-flexy #panels,ytd-watch-flexy #player-ads," +
                    "ytd-watch-flexy #watch-below-the-fold{display:none!important}" +
                    // Paksa video dan player fill layar penuh
                    "html,body{margin:0!important;padding:0!important;overflow:hidden!important;" +
                    "background:#000!important;width:100%!important;height:100%!important}" +
                    "#movie_player,#player-container,ytd-watch-flexy," +
                    "#player-container-outer,#player-container-inner," +
                    ".html5-video-container,.html5-main-video," +
                    "#content,#page-manager,ytd-app{" +
                    "width:100%!important;height:100%!important;" +
                    "max-width:100%!important;max-height:100%!important;" +
                    "margin:0!important;padding:0!important;background:#000!important}" +
                    "video{position:fixed!important;top:0!important;left:0!important;" +
                    "width:100%!important;height:100%!important;" +
                    "object-fit:contain!important;z-index:9999!important}';" +
                    // === FULLSCREEN: klik tombol fullscreen YouTube ===
                    "try{" +
                    "var fs=document.querySelector('.ytp-fullscreen-button');" +
                    "if(fs&&!document.fullscreenElement){fs.click();}" +
                    "}catch(e){}" +
                    // === PLAY: pastikan video berjalan ===
                    "try{" +
                    "var v=document.querySelector('video');" +
                    "if(v&&v.paused){v.play().catch(function(){});}" +
                    "}catch(e){}" +
                    "})();";

                // JS deteksi video selesai via polling — lebih reliable dari event 'ended'
                // Alasan: YouTube me-load video baru ke element yang sama sehingga
                // event 'ended' sering tidak terpicu, yang terpicu adalah 'emptied'/'pause'
                String endedJs =
                    "(function(){" +
                    // Hanya pasang 1 interval per halaman
                    "if(window.__ytPollActive)return;" +
                    "window.__ytPollActive=true;" +
                    // Simpan URL video yang sedang kita putar (diset dari Android saat loadUrl)
                    "var lastSrc='';" +
                    "var notified=false;" +
                    "setInterval(function(){" +
                    "  var v=document.querySelector('video');" +
                    "  if(!v||!window.AndroidInterface)return;" +
                    // Matikan autoplay & loop bawaan YouTube
                    "  v.loop=false;" +
                    "  v.autoplay=false;" +
                    // Deteksi: video src berubah = YouTube sudah ganti video lain
                    "  if(v.src&&v.src!==lastSrc){" +
                    "    lastSrc=v.src;" +
                    "    notified=false;" +
                    "  }" +
                    // Deteksi: video hampir selesai (sisa < 1.5 detik) atau ended
                    // Skip jika ini iklan — iklan biasanya < 60 detik, video asli >= 60 detik
                    "  var isAd=(v.duration>0&&v.duration<60);" +
                    "  var ended=!isAd&&(v.ended||(v.duration>0&&!v.paused&&" +
                    "    (v.duration-v.currentTime)<1.5));" +
                    "  if(ended&&!notified){" +
                    "    notified=true;" +
                    "    v.pause();" +
                    "    window.AndroidInterface.onVideoEnded();" +
                    "  }" +
                    // Sembunyikan endscreen & up-next overlay
                    "  var st=document.getElementById('__hyt_end__');" +
                    "  if(!st){st=document.createElement('style');" +
                    "    st.id='__hyt_end__';document.head.appendChild(st);" +
                    "    st.textContent='.ytp-endscreen-content,.ytp-ce-element," +
                    ".ytp-autonav-endscreen,.ytp-upnext,.ytp-upnext-autoplay," +
                    "ytd-compact-autoplay-renderer{display:none!important;" +
                    "pointer-events:none!important}';}" +
                    "},500);" + // cek setiap 500ms
                    "})();";

                // Inject bertahap karena YouTube render secara async
                view.postDelayed(() -> { if (isYouTubeMode) view.evaluateJavascript(js, null); }, 800);
                view.postDelayed(() -> { if (isYouTubeMode) { view.evaluateJavascript(js, null); view.evaluateJavascript(endedJs, null); } }, 2000);
                view.postDelayed(() -> { if (isYouTubeMode) { view.evaluateJavascript(js, null); view.evaluateJavascript(endedJs, null); } }, 4000);
            }
        });
    }

    // Field untuk fullscreen WebView
    private View youtubeFullscreenView = null;
    private WebChromeClient.CustomViewCallback youtubeFullscreenCallback = null;
    private android.widget.FrameLayout youtubeFullscreenContainer;

    /**
     * Putar YouTube via embed URL langsung — tidak ada UI YouTube,
     * autoplay + unmute, tidak bisa di-pause dari layar.
     */
    /**
     * Fetch semua video ID dari playlist via YouTube Data API v3.
     * pageToken = null untuk halaman pertama, lanjut recursif untuk halaman berikutnya.
     */
    private void fetchYouTubePlaylist(String playlistId, String pageToken) {
        tvLoadingMsg.setText(getString(R.string.loading_playlist));
        videoLoading.setVisibility(View.VISIBLE);

        String url = "https://www.googleapis.com/youtube/v3/playlistItems"
            + "?part=contentDetails&maxResults=50&playlistId=" + playlistId
            + "&key=" + YT_API_KEY
            + (pageToken != null ? "&pageToken=" + pageToken : "");

        new Thread(() -> {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(url).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Accept", "application/json");

                java.io.InputStream is = conn.getInputStream();
                // Baca InputStream manual — kompatibel dengan minSdk 21
                // readAllBytes() baru ada di API 33, tidak bisa dipakai
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                String json = baos.toString("UTF-8");
                is.close();

                // Parse video IDs dari JSON
                java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"videoId\"\\s*:\\s*\"([a-zA-Z0-9_-]{11})\"")
                    .matcher(json);
                List<String> ids = new ArrayList<>();
                while (m.find()) ids.add(m.group(1));

                // Cek nextPageToken untuk halaman berikutnya
                java.util.regex.Matcher np = java.util.regex.Pattern
                    .compile("\"nextPageToken\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(json);
                String nextToken = np.find() ? np.group(1) : null;

                runOnUiThread(() -> {
                    ytPlaylistVideoIds.addAll(ids);
                    if (nextToken != null) {
                        // Masih ada halaman berikutnya — fetch lagi
                        fetchYouTubePlaylist(playlistId, nextToken);
                    } else {
                        // Semua video sudah terkumpul — putar dari awal
                        if (!ytPlaylistVideoIds.isEmpty()) {
                            ytPlaylistCurrentIdx = 0;
                            playYouTube(ytPlaylistVideoIds.get(0));
                        } else {
                            tvLoadingMsg.setText(getString(R.string.status_playlist_unavailable));
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    // Tampilkan error — jangan finish() atau balik ke settings
                    isYouTubeMode = true;
                    youtubeWebView.setVisibility(View.VISIBLE);
                    videoLoading.setVisibility(View.VISIBLE);
                    tvLoadingMsg.setText(getString(R.string.status_error_load, e.getMessage()));
                });
            }
        }).start();
    }

    /** Putar video berikutnya dalam playlist */
    private void playNextYtPlaylistItem() {
        if (ytPlaylistVideoIds.isEmpty()) return;
        ytPlaylistCurrentIdx++;
        if (ytPlaylistCurrentIdx >= ytPlaylistVideoIds.size()) ytPlaylistCurrentIdx = 0;
        playYouTube(ytPlaylistVideoIds.get(ytPlaylistCurrentIdx));
    }



    private void playYouTube(final String videoId) {
        isYouTubeMode = true;
        playerView.setVisibility(View.GONE);
        youtubeWebView.setVisibility(View.VISIBLE);
        videoLoading.setVisibility(View.GONE);
        // Sembunyikan overlay remote guide — hanya muncul untuk ExoPlayer

        // Pakai halaman watch biasa — tidak kena error 152/153 karena bukan embed
        // UA desktop agar YouTube tidak redirect ke m.youtube.com (mobile UI sulit di-inject)
        // rel=0: matikan related videos di akhir
        // autoplay=1 tetap agar video langsung mulai tanpa perlu klik
        String url = "https://www.youtube.com/watch?v=" + videoId + "&autoplay=1&rel=0";
        youtubeWebView.loadUrl(url);
    }

    /** Kembali ke mode ExoPlayer normal */
    private void switchToExoMode() {
        if (isYouTubeMode) {
            isYouTubeMode = false;
            if (youtubeFullscreenCallback != null) {
                youtubeFullscreenCallback.onCustomViewHidden();
            }
            youtubeWebView.loadUrl("about:blank");
            youtubeWebView.setVisibility(View.GONE);
            playerView.setVisibility(View.VISIBLE);
        }
    }


    // ===== PLAYER =====
    private void setupPlayer() {
        trackSelector = new DefaultTrackSelector(this);
        // Buffer dari preferensi user (0–60 detik)
        int bufSecs = prefs.getBufferSecs();
        int bufMs   = bufSecs <= 0 ? 1000 : bufSecs * 1000; // min 1 detik agar tidak crash
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    bufMs,           // minBufferMs
                    bufMs * 3,       // maxBufferMs (3x min)
                    Math.min(bufMs, 2500), // bufferForPlaybackMs
                    Math.min(bufMs, 5000)) // bufferForPlaybackAfterRebufferMs
                .build();
        player = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build();
        playerView.setUseController(false);
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onTracksChanged(Tracks tracks) {
                // Re-apply resolusi + subtitle saat tracks tersedia/berubah
                applyResolution();
                applySubtitle();
            }

            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    videoLoading.setBackgroundColor(0x00000000);
                    videoLoading.setVisibility(View.VISIBLE);
                    tvLoadingMsg.setText(getString(R.string.loading_generic));
                } else if (state == Player.STATE_READY) {
                    videoLoading.setVisibility(View.GONE);
                    if (!streamStarted) onFirstStreamReady();
                    streamStarted = true;
                } else if (state == Player.STATE_ENDED) {
                    videoLoading.setBackgroundColor(0x00000000);
                    videoLoading.setVisibility(View.VISIBLE);
                    tvLoadingMsg.setText(getString(R.string.player_stream_ended));
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
        // Jam opacity 50% saat menonton (panel tutup)
        tvClock.animate().alpha(0.5f).setDuration(600).start();
    }
    private String getErrorMessage(androidx.media3.common.PlaybackException e) {
        int c = e.errorCode;
        if (c == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) return getString(R.string.error_no_connection);
        if (c == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) return "Koneksi timeout, coba lagi...";
        if (c == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return getString(R.string.error_http);
        if (c == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) return "Format tidak didukung";
        return getString(R.string.error_playback);
    }

    // ===== CHANNEL ADAPTER =====
    private void setupChannelAdapter() {
        tvPanelTitle.setText(playlistName.isEmpty() ? getString(R.string.player_all_channels) : playlistName.toUpperCase());
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
                        // Swipe KANAN
                        if (groupPanelOpen)                        closeGroupPanel();    // poin 6
                        else if (!panelOpen && !categoryFullOpen)  openPanel();
                        else if (panelOpen && !categoryFullOpen)   openCategoryFull();
                        return true;
                    } else if (dX < -80 && Math.abs(vX) > 100) {
                        // Swipe KIRI
                        if (panelOpen || categoryFullOpen) {       // poin 6
                            if (categoryFullOpen) closeCategoryFull();
                            else hidePanel();
                        } else if (!groupPanelOpen) openGroupPanel();
                        else closeGroupPanel();
                        return true;
                    }
                } else if (Math.abs(dY) > 80 && Math.abs(vY) > 100) {
                    // POIN 4: jangan pindah channel saat panel kanan terbuka
                    if (!panelOpen && !categoryFullOpen && !groupPanelOpen) {
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
        // Sidebar icon tidak boleh propagate touch ke backdrop
        categorySidebar.setOnTouchListener((v, e) -> true);

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
        chListPanel.setOnTouchListener((v, e) -> {
            panelSwipe.onTouchEvent(e);
            return true; // intercept: jangan propagate ke backdrop
        });

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
        catSettings.setOnClickListener(v -> openMainActivity());

        // Item di panel kategori expanded → pilih dan kembali ke daftar channel
        catFullAll.setOnClickListener(v -> selectCategory("ALL"));
        catFullTv.setOnClickListener(v -> selectCategory("TV"));
        catFullRadio.setOnClickListener(v -> selectCategory("RADIO"));
        catFullMovie.setOnClickListener(v -> selectCategory("FILM"));
        catFullSettings.setOnClickListener(v -> openMainActivity());

        // Setup group title panel
        setupGroupTitlePanel();
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
        updateCategoryItemStyle(catFullAll,      catFullAllIcon,      catFullAllText,      cat.equals("ALL"));
        updateCategoryItemStyle(catFullTv,       catFullTvIcon,       catFullTvText,       cat.equals("TV"));
        updateCategoryItemStyle(catFullRadio,    catFullRadioIcon,    catFullRadioText,    cat.equals("RADIO"));
        updateCategoryItemStyle(catFullMovie,    catFullMovieIcon,    catFullMovieText,    cat.equals("FILM"));
        updateCategoryItemStyle(catFullSettings, catFullSettingsIcon, catFullSettingsText, false);

        // Filter daftar channel
        channelAdapter.applyGroupFilter(cat);
        // Jika dibuka via swipe kiri (langsung kategori), buka panel channel setelah pilih
        if (!panelOpen) openPanel();

        // Tutup panel kategori → kembali tampilkan daftar channel
        closeCategoryFull();
    }

    private void updateCategoryItemStyle(LinearLayout item, ImageView icon, TextView text, boolean active) {
        if (active) {
            item.setBackground(getDrawable(R.drawable.bg_category_item_active));
            text.setTextColor(0xFF000000);
            if (icon != null) {
                icon.setAlpha(1.0f);
                icon.setColorFilter(0xFF000000, android.graphics.PorterDuff.Mode.SRC_IN);
            }
        } else {
            item.setBackground(null);
            text.setTextColor(0xCCFFFFFF);
            if (icon != null) {
                icon.setAlpha(0.5f);
                icon.clearColorFilter();
            }
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

    /** Setup panel kanan (group title) */
    private void setupGroupTitlePanel() {
        // POIN 4: Touch blocker — semua touch di panel kanan tidak tembus ke playerView/youtube
        groupListPanel.setOnTouchListener((v, e) -> true);
        groupChannelPanel.setOnTouchListener((v, e) -> true);

        // Backdrop kanan tap → tutup
        groupBackdrop.setOnClickListener(v -> closeGroupPanel());

        // Tombol kembali di panel channel group → kembali ke list group
        findViewById(R.id.btn_group_back).setOnClickListener(v -> showGroupList());

        // POIN 6: Swipe kanan di panel list group → tutup panel kanan
        GestureDetector groupListSwipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (e1 == null || e2 == null) return false;
                float dX = e2.getX() - e1.getX();
                if (dX > 80 && Math.abs(vX) > 100) { closeGroupPanel(); return true; }
                return false;
            }
        });
        groupListPanel.setOnTouchListener((v, e) -> { groupListSwipe.onTouchEvent(e); return true; });

        // POIN 6: Swipe kanan di panel channel group → kembali ke list group
        GestureDetector groupChSwipe = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (e1 == null || e2 == null) return false;
                float dX = e2.getX() - e1.getX();
                if (dX > 80 && Math.abs(vX) > 100) { showGroupList(); return true; }
                return false;
            }
        });
        groupChannelPanel.setOnTouchListener((v, e) -> { groupChSwipe.onTouchEvent(e); return true; });

        rvGroupTitles.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        rvGroupChannels.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
    }

    /** Buka panel kanan — tampilkan list group */
    private void openGroupPanel() {
        if (groupPanelOpen) return;
        groupPanelOpen = true;
        groupChannelOpen = false;

        // POIN 7: sembunyikan jam di pojok kanan atas
        tvClock.setVisibility(View.INVISIBLE);

        // Backdrop
        groupBackdrop.setVisibility(View.VISIBLE);
        groupBackdrop.animate().alpha(1f).setDuration(200).start();

        showGroupList();
    }

    /** Tampilkan list group (tahap 1 / kembali dari channel) */
    private void showGroupList() {
        float dp = getResources().getDisplayMetrics().density;
        float panelW = 380f * dp;

        // Isi adapter group titles
        java.util.LinkedHashSet<String> groups = new java.util.LinkedHashSet<>();
        for (Channel ch : channels) {
            if (ch.group != null && !ch.group.isEmpty()) groups.add(ch.group);
        }
        java.util.List<String> groupList = new java.util.ArrayList<>(groups);

        rvGroupTitles.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup p, int t) {
                android.view.View v = android.view.LayoutInflater.from(PlayerActivity.this)
                        .inflate(R.layout.item_group_title, p, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder h, int pos) {
                String g = groupList.get(pos);
                android.widget.TextView tvName  = h.itemView.findViewById(R.id.tv_group_title_item);
                android.widget.TextView tvCount = h.itemView.findViewById(R.id.tv_group_count);
                // Penanda aktif: group_item_bg dan iv_group_triangle (dari item_group_title.xml baru)
                // Jika ID tidak ditemukan (item_group_title.xml lama), graceful fallback
                tvName.setText(g);
                long count = channels.stream().filter(ch -> g.equals(ch.group)).count();
                tvCount.setText(String.valueOf(count));
                boolean active = g.equals(activeGroupFilter);
                // Poin 1+3: segitiga #16232A + background #E4EEF0 + teks #16232A saat aktif
                android.view.View itemBg       = h.itemView.findViewById(R.id.group_item_bg);
                android.widget.ImageView ivTri = h.itemView.findViewById(R.id.iv_group_triangle);
                if (itemBg != null) itemBg.setVisibility(active ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
                if (ivTri != null) {
                    ivTri.setVisibility(active ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
                    ivTri.setColorFilter(0xFF16232A, android.graphics.PorterDuff.Mode.SRC_IN);
                }
                tvName.setTextColor(active ? 0xFF16232A : 0xCCFFFFFF);
                tvCount.setTextColor(active ? 0x8016232A : 0x50FFFFFF);
                h.itemView.setOnClickListener(v -> showGroupChannels(g));
            }
            @Override public int getItemCount() { return groupList.size(); }
        });

        if (groupChannelOpen) {
            // Sequential: channel keluar ke kanan dulu → baru list masuk dari kanan
            groupChannelOpen = false;
            groupChannelPanel.animate().translationX(panelW).setDuration(240)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        groupChannelPanel.setVisibility(View.INVISIBLE);
                        groupListPanel.setVisibility(View.VISIBLE);
                        groupListPanel.setTranslationX(panelW);
                        groupListPanel.animate().translationX(0f).setDuration(260)
                                .setInterpolator(new DecelerateInterpolator()).start();
                    }).start();
        } else {
            groupListPanel.setVisibility(View.VISIBLE);
            groupListPanel.setTranslationX(panelW);
            groupListPanel.animate().translationX(0f).setDuration(260)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    /** Pilih group → dorong list group keluar, tampilkan channel group */
    private void showGroupChannels(String group) {
        activeGroupFilter = group;
        // POIN 2: TIDAK memanggil channelAdapter — panel kiri tidak berubah

        float dp = getResources().getDisplayMetrics().density;
        float panelW = 380f * dp;

        tvGroupChannelTitle.setText(group.toUpperCase());

        // Buat map channel → index asli
        java.util.LinkedHashMap<Integer, Channel> idxMap = new java.util.LinkedHashMap<>();
        for (int i = 0; i < channels.size(); i++) {
            if (group.equals(channels.get(i).group)) idxMap.put(i, channels.get(i));
        }
        java.util.List<Integer> idxList = new java.util.ArrayList<>(idxMap.keySet());
        java.util.List<Channel> chList = new java.util.ArrayList<>(idxMap.values());

        rvGroupChannels.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup p, int t) {
                android.view.View v = android.view.LayoutInflater.from(PlayerActivity.this)
                        .inflate(R.layout.item_ch_panel, p, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder h, int pos) {
                Channel ch = chList.get(pos);
                int realIdx = idxList.get(pos);
                // POIN 5: penanda aktif sama persis seperti panel kiri
                boolean isActive = (realIdx == currentChannelIdx);

                android.widget.TextView tvNum  = h.itemView.findViewById(R.id.tv_num);
                android.widget.TextView tvName = h.itemView.findViewById(R.id.tv_ch_name);
                android.widget.TextView tvEpg  = h.itemView.findViewById(R.id.tv_ch_epg);
                android.widget.ImageView ivLogo = h.itemView.findViewById(R.id.iv_logo);
                android.widget.TextView tvFb   = h.itemView.findViewById(R.id.tv_logo_fallback);
                android.view.View itemBg        = h.itemView.findViewById(R.id.item_bg);
                android.widget.LinearLayout wave = h.itemView.findViewById(R.id.waveform_bars);
                android.widget.ImageView ivPlay = h.itemView.findViewById(R.id.iv_play_arrow);

                tvNum.setText(realIdx >= 0 ? String.valueOf(realIdx + 1) : "");
                tvNum.setTextColor(isActive ? 0xFF000000 : 0x80FFFFFF);
                tvName.setText(ch.name);
                tvName.setTextColor(isActive ? 0xFF000000 : 0xFFFFFFFF);
                tvEpg.setText((ch.group != null && !ch.group.isEmpty())
                        ? ch.group : getString(R.string.player_no_info));
                tvEpg.setTextColor(isActive ? 0x80000000 : 0x80FFFFFF);

                if (ch.logoUrl != null && !ch.logoUrl.isEmpty()) {
                    ivLogo.setVisibility(View.VISIBLE);
                    tvFb.setVisibility(View.GONE);
                    com.bumptech.glide.Glide.with(ivLogo.getContext()).load(ch.logoUrl)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                            .into(ivLogo);
                } else {
                    ivLogo.setVisibility(View.GONE);
                    tvFb.setVisibility(View.VISIBLE);
                    String ini = ch.name.isEmpty() ? "?" :
                            ch.name.substring(0, Math.min(2, ch.name.length())).toUpperCase();
                    tvFb.setText(ini);
                    tvFb.setTextColor(isActive ? 0xFF000000 : 0x66FFFFFF);
                }
                // POIN 1+5: background putih + segitiga saat aktif (bukan waveform)
                if (itemBg != null) itemBg.setVisibility(isActive ? View.VISIBLE : View.INVISIBLE);
                if (wave != null) wave.setVisibility(View.GONE); // tidak pakai waveform di panel kanan
                if (ivPlay != null) {
                    ivPlay.setImageResource(R.drawable.ic_triangle_play);
                    ivPlay.setColorFilter(0xFF16232A, android.graphics.PorterDuff.Mode.SRC_IN);
                    ivPlay.setScaleX(-1f); // flip ke kiri
                    ivPlay.setVisibility(isActive ? View.VISIBLE : View.GONE);
                }

                h.itemView.setOnClickListener(v -> {
                    playChannel(realIdx, true);
                    closeGroupPanel();
                });
            }
            @Override public int getItemCount() { return chList.size(); }
        });

        // Sequential: list keluar ke kanan dulu → setelah selesai channel masuk dari kanan
        groupChannelOpen = true;
        groupListPanel.animate().translationX(panelW).setDuration(240)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    groupListPanel.setVisibility(View.INVISIBLE);
                    // Baru sekarang channel panel masuk dari kanan
                    groupChannelPanel.setVisibility(View.VISIBLE);
                    groupChannelPanel.setTranslationX(panelW);
                    groupChannelPanel.animate().translationX(0f).setDuration(260)
                            .setInterpolator(new DecelerateInterpolator()).start();
                }).start();
    }

    /** Tutup semua panel kanan */
    private void closeGroupPanel() {
        if (!groupPanelOpen) return;
        groupPanelOpen = false;

        float dp = getResources().getDisplayMetrics().density;
        float panelW = 380f * dp;

        // POIN 2: tutup panel aktif ke kanan dengan durasi konsisten
        LinearLayout activePanel = groupChannelOpen ? groupChannelPanel : groupListPanel;
        activePanel.animate().translationX(panelW).setDuration(260)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    groupListPanel.setVisibility(View.INVISIBLE);
                    groupChannelPanel.setVisibility(View.INVISIBLE);
                }).start();
        groupChannelOpen = false;

        groupBackdrop.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> groupBackdrop.setVisibility(View.INVISIBLE)).start();

        // POIN 7: kembalikan jam ke pojok kanan atas
        tvClock.setVisibility(View.VISIBLE);
    }

    /** Filter channel berdasarkan group title exact match */
    private void applyGroupTitleFilter(String group) {
        channelAdapter.applyExactGroupFilter(group);
    }

    /** Buka panel kategori langsung tanpa panel daftar channel (dari swipe kiri di player) */
    private void openCategoryDirect() {
        if (categoryFullOpen) return;
        // Buka panel channel di background tapi tak kelihatan, supaya state konsisten
        panelOpen = true;
        float dp = getResources().getDisplayMetrics().density;
        chListBackdrop.setVisibility(View.VISIBLE);
        chListBackdrop.animate().alpha(1f).setDuration(200).start();
        tvClock.animate().alpha(1.0f).setDuration(200).start();
        // Sidebar dan chListPanel tidak dimunculkan — langsung buka panel kategori
        chListPanel.setVisibility(View.INVISIBLE);
        categorySidebar.setVisibility(View.INVISIBLE);
        // Buka panel kategori
        openCategoryFull();
    }

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
        if (groupPanelOpen || groupChannelOpen) {
            groupPanelOpen = false; groupChannelOpen = false;
            groupListPanel.setVisibility(View.INVISIBLE);
            groupChannelPanel.setVisibility(View.INVISIBLE);
            if (groupBackdrop != null) groupBackdrop.setVisibility(View.INVISIBLE);
            tvClock.setVisibility(View.VISIBLE);
        }

        float dp = getResources().getDisplayMetrics().density;

        // Jam kembali opacity 50% saat panel ditutup
        tvClock.animate().alpha(0.5f).setDuration(300).start();

        categorySidebar.animate().translationX(-68f * dp).setDuration(280)
                .withEndAction(() -> categorySidebar.setVisibility(View.INVISIBLE)).start();

        chListPanel.animate().translationX(-448f * dp).setDuration(300)
                .withEndAction(() -> chListPanel.setVisibility(View.INVISIBLE)).start();

        // Tutup panel kategori jika sedang terbuka
        if (categoryPanelFull.getVisibility() == View.VISIBLE) {
            categoryPanelFull.animate().translationX(-308f * dp).setDuration(250)
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

        // Sembunyikan sidebar — panel full menutupi area sidebar sepenuhnya
        categorySidebar.setVisibility(View.INVISIBLE);

        categoryPanelFull.setVisibility(View.VISIBLE);
        categoryPanelFull.setAlpha(1f);
        categoryPanelFull.setTranslationX(-308f * dp);
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

        // Panel kategori slide keluar ke kiri, lalu tampilkan kembali sidebar
        categoryPanelFull.animate().translationX(-308f * dp).setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    categoryPanelFull.setVisibility(View.INVISIBLE);
                    categorySidebar.setVisibility(View.VISIBLE);
                }).start();

        // Kembalikan ch_list_panel ke posisi semula (translationX = 0)
        chListPanel.animate().translationX(0f).setDuration(280)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    // ===== PLAY CHANNEL =====

    // ===== RESOLUSI =====
    /** Terapkan preferensi resolusi ke ExoPlayer track selector */
    private void applyResolution() {
        if (trackSelector == null || player == null) return;
        String res = prefs.getResolution();
        if (PrefsManager.RES_AUTO.equals(res)) {
            // Kembali ke adaptive bitrate — hapus semua override
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .clearOverrides()
                    .setForceHighestSupportedBitrate(false)
                    .setForceLowestBitrate(false)
                    .build()
            );
            return;
        }
        // Tunggu tracks tersedia
        Tracks tracks = player.getCurrentTracks();
        if (tracks == null) return;

        TrackGroup bestGroup = null;
        int bestIdx = -1;
        int bestWidth = -1;

        for (Tracks.Group tg : tracks.getGroups()) {
            if (tg.getType() != androidx.media3.common.C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < tg.length; i++) {
                if (!tg.isTrackSupported(i)) continue;
                Format fmt = tg.getTrackFormat(i);
                int w = fmt.width > 0 ? fmt.width : fmt.height;
                if (w <= 0) continue;
                if (PrefsManager.RES_HIGHEST.equals(res)) {
                    if (w > bestWidth) { bestWidth = w; bestGroup = tg.getMediaTrackGroup(); bestIdx = i; }
                } else { // lowest
                    if (bestWidth < 0 || w < bestWidth) { bestWidth = w; bestGroup = tg.getMediaTrackGroup(); bestIdx = i; }
                }
            }
        }

        if (bestGroup != null && bestIdx >= 0) {
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .clearOverrides()
                    .addOverride(new TrackSelectionOverride(bestGroup, bestIdx))
                    .build()
            );
        } else {
            // Single track atau track belum tersedia — biarkan auto
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .clearOverrides()
                    .setForceHighestSupportedBitrate(PrefsManager.RES_HIGHEST.equals(res))
                    .setForceLowestBitrate(PrefsManager.RES_LOWEST.equals(res))
                    .build()
            );
        }
    }


    // ===== SUBTITLE =====
    /** Aktifkan/nonaktifkan subtitle sesuai preferensi */
    private void applySubtitle() {
        if (trackSelector == null) return;
        boolean enabled = prefs.getSubtitleEnabled();
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setRendererDisabled(androidx.media3.common.C.INDEX_UNSET, false)
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !enabled)
                .build()
        );
    }



    /** True jika URL memerlukan resolusi sebelum diputar */
    private boolean needsUrlResolving(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains("drive.google.com") ||
               lower.contains("dropbox.com") ||
               lower.contains("1drv.ms") ||
               lower.contains("onedrive.live.com");
    }

    // ===== URL RESOLVER (Google Drive, Dropbox, OneDrive, redirect) =====
    /**
     * Resolve URL video hosting ke direct stream URL.
     * Dijalankan di background thread; callback di main thread.
     */
    /**
     * Konversi URL share hosting → direct stream URL.
     * Jika tidak dikenali, kembalikan URL asli.
     */
    private String resolveStreamUrl(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        String url = raw.trim();

        // ── Google Drive ──
        // Format: https://drive.google.com/file/d/{ID}/view?...
        // atau: https://drive.google.com/open?id={ID}
        java.util.regex.Matcher gdMatcher = java.util.regex.Pattern
            .compile("drive[.]google[.]com/(?:file/d/|open[?]id=)([a-zA-Z0-9_-]+)")
            .matcher(url);
        if (gdMatcher.find()) {
            String fileId = gdMatcher.group(1);
            return "https://drive.google.com/uc?export=download&id=" + fileId;
        }

        // ── Dropbox ──
        // ?dl=0 → ?dl=1 ; www.dropbox.com → dl.dropboxusercontent.com
        if (url.contains("dropbox.com")) {
            url = url.replaceAll("[?&]dl=0", "")       // hapus dl=0
                     .replaceAll("[?&]dl=1", "");       // hapus duplikat dl=1
            // Tambah dl=1
            url = url + (url.contains("?") ? "&dl=1" : "?dl=1");
            // Ganti domain ke direct download
            url = url.replace("www.dropbox.com", "dl.dropboxusercontent.com");
            return url;
        }

        // ── OneDrive ──
        // https://1drv.ms/{id} atau https://onedrive.live.com/...
        // Encode ke base64 direct link
        if (url.contains("1drv.ms") || url.contains("onedrive.live.com")) {
            try {
                byte[] b = (url).getBytes("UTF-8");
                String b64 = android.util.Base64.encodeToString(b,
                    android.util.Base64.NO_PADDING | android.util.Base64.NO_WRAP
                    | android.util.Base64.URL_SAFE);
                // Remove trailing '=' padding
                b64 = b64.replaceAll("=+$", "");
                return "https://api.onedrive.com/v1.0/shares/u!" + b64
                    + "/root/content";
            } catch (Exception ignored) {}
        }

        // ── Generic redirect follow ──
        // Jika URL adalah shortlink/redirect, follow hingga final URL
        // Hanya untuk URL yang tidak langsung berupa stream
        if (!looksLikeDirectStream(url)) {
            try {
                String followed = followRedirect(url, 5);
                if (followed != null && !followed.equals(url)) return followed;
            } catch (Exception ignored) {}
        }

        return url;
    }

    /** True jika URL kemungkinan besar adalah direct stream (ends with media ext atau port stream) */
    private boolean looksLikeDirectStream(String url) {
        String lower = url.toLowerCase();
        return lower.contains(".m3u8") || lower.contains(".ts") ||
               lower.contains(".mp4") || lower.contains(".mkv") ||
               lower.contains(".avi") || lower.contains(".mpd") ||
               lower.contains(":8080") || lower.contains(":1935") ||
               lower.contains(":8888") || lower.startsWith("rtsp://") ||
               lower.startsWith("rtmp://");
    }

    /** Follow HTTP redirect (max maxHops kali) — sync, jalankan di background thread */
    private String followRedirect(String urlStr, int maxHops) throws Exception {
        String current = urlStr;
        for (int i = 0; i < maxHops; i++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            int code = conn.getResponseCode();
            conn.disconnect();
            if (code >= 300 && code < 400) {
                String loc = conn.getHeaderField("Location");
                if (loc == null || loc.equals(current)) break;
                current = loc;
            } else {
                break;
            }
        }
        return current;
    }

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
        tvLoadingMsg.setText(getString(R.string.loading_generic));
        updateChInfo(ch, idx);
        // Cek YouTube dulu — jika ya, gunakan WebView bukan ExoPlayer
        if (isYouTubeUrl(ch.url)) {
            player.stop();
            playerView.setVisibility(View.GONE);
            channelAdapter.setActiveIndex(idx);
            showChInfo();

            String playlistId = extractYouTubePlaylistId(ch.url);
            String videoId    = extractYouTubeId(ch.url);

            if (playlistId != null) {
                // URL playlist → tampilkan loading, fetch via API, lalu putar
                isYouTubeMode = true;
                youtubeWebView.setVisibility(View.VISIBLE);
                videoLoading.setVisibility(View.VISIBLE);
                ytPlaylistId = playlistId;
                ytPlaylistVideoIds.clear();
                ytPlaylistCurrentIdx = 0;
                fetchYouTubePlaylist(playlistId, null);
            } else if (videoId != null) {
                // Video biasa
                ytPlaylistId = null;
                ytPlaylistVideoIds.clear();
                playYouTube(videoId);
            }
            return;
        }

        // Bukan YouTube — switch ke ExoPlayer (reset WebView jika sedang YouTube mode)
        switchToExoMode();

        // Resolve URL hosting share links (Google Drive, Dropbox, OneDrive)
        final String channelUrl = ch.url;
        if (needsUrlResolving(channelUrl)) {
            // Jalankan resolver di background, lalu rekursif playChannel dengan url sudah di-resolve
            ExecutorService ex = Executors.newSingleThreadExecutor();
            ex.execute(() -> {
                String resolved = resolveStreamUrl(channelUrl);
                if (!resolved.equals(channelUrl)) {
                    // Buat channel baru dengan URL yang sudah di-resolve
                    Channel resolvedCh = new Channel(ch.name, resolved, ch.logoUrl, ch.group);
                    resolvedCh.userAgent  = ch.userAgent;
                    resolvedCh.referrer   = ch.referrer;
                    resolvedCh.drmType    = ch.drmType;
                    resolvedCh.drmKey     = ch.drmKey;
                    resolvedCh.isDrm      = ch.isDrm;
                    // Ganti channel di list lalu play ulang di main thread
                    final int resolvedIdx = idx;
                    final Channel finalCh = resolvedCh;
                    runOnUiThread(() -> {
                        if (resolvedIdx < channels.size()) {
                            channels.set(resolvedIdx, finalCh);
                        }
                        playChannelDirect(finalCh, resolvedIdx, withFlash);
                    });
                } else {
                    runOnUiThread(() -> playChannelDirect(ch, idx, withFlash));
                }
                ex.shutdown();
            });
            return;
        }

        playChannelDirect(ch, idx, withFlash);
    }

    /** Putar channel langsung tanpa URL resolving (URL sudah final) */
    private void playChannelDirect(Channel ch, int idx, boolean withFlash) {
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
                    .setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(headers)
                    .setTransferListener(transferListener);
            // Reset counter & restart updater saat ganti channel
            bytesAccumulator.set(0);
            startBitrateUpdater();
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
            // Terapkan preferensi resolusi + subtitle
            applyResolution();
            applySubtitle();
        } catch (Exception e) {
            tvLoadingMsg.setText(getString(R.string.status_error_generic, e.getMessage()));
        }
        channelAdapter.setActiveIndex(idx);
        showChInfo();
    }

    // ===== CHANNEL INFO OSD =====
    private void updateChInfo(Channel ch, int idx) {
        tvChNum.setText(String.valueOf(idx + 1));
        tvChName.setText(ch.name);
        tvChEpg.setText((ch.group != null && !ch.group.isEmpty())
                ? ch.group
                : getString(R.string.player_no_info));
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
    /** Hitung seberapa jauh OSD harus digeser ke kiri agar benar-benar keluar layar.
     *  Menggunakan lebar aktual box + margin kiri + sedikit ekstra, dalam pixel. */
    private float osdHideOffset() {
        // getWidth() mengembalikan pixel, sudah benar untuk translationX
        int w = channelInfo.getWidth();
        if (w == 0) {
            // Fallback sebelum first layout: gunakan screenWidth sebagai batas aman
            return -(getResources().getDisplayMetrics().widthPixels + 100f);
        }
        // Geser sejauh lebar box + margin kirinya + 50px ekstra biar benar-benar hilang
        return -(w + channelInfo.getLeft() + 50f);
    }

    private void showChInfo() {
        // Poin 5: langsung tampil tanpa animasi masuk (anti-lag saat pindah channel cepat)
        channelInfo.animate().cancel();
        channelInfo.setAlpha(1f);
        channelInfo.setTranslationX(20f);
        channelInfo.setVisibility(View.VISIBLE);
        if (chInfoHideRunnable != null) handler.removeCallbacks(chInfoHideRunnable);
        chInfoHideRunnable = this::hideChInfo;
        handler.postDelayed(chInfoHideRunnable, 4000);
    }

    private void hideChInfo() {
        // Geser sejauh lebar aktual box agar keluar layar tanpa patah
        float target = osdHideOffset();
        channelInfo.animate()
                .translationX(target)
                .alpha(0.9f)
                .setDuration(450)
                .setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f))
                .withEndAction(() -> {
                    channelInfo.setVisibility(View.GONE);
                    channelInfo.setAlpha(1f);
                }).start();
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
    // Activity-level touch intercept — dijalankan SEBELUM view hierarchy (termasuk WebView YouTube)
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (isYouTubeMode && !panelOpen && !categoryFullOpen) {
            gestureDetector.onTouchEvent(event);
            // Tetap pass event ke bawah agar kontrol YouTube bisa diklik
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int kc = event.getKeyCode();
            // Volume Up/Down/OK saat YouTube mode → unmute video
            // (autoplay harus mute dulu, user unmute manual via remote/HP)
            if (isYouTubeMode &&
                (kc == KeyEvent.KEYCODE_VOLUME_UP ||
                 kc == KeyEvent.KEYCODE_VOLUME_DOWN ||
                 kc == KeyEvent.KEYCODE_DPAD_CENTER ||
                 kc == KeyEvent.KEYCODE_ENTER)) {
                youtubeWebView.evaluateJavascript(
                    "(function(){var v=document.querySelector('video');" +
                    "if(v&&v.muted){v.muted=false;v.volume=1;" +
                    "var s=document.getElementById('__hyt__');" +
                    "if(s)s.textContent+='.ytp-unmute{display:none!important}';}})();", null);
                // Volume Up/Down tetap diteruskan ke sistem agar volume bar muncul
                if (kc == KeyEvent.KEYCODE_VOLUME_UP || kc == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    return super.dispatchKeyEvent(event);
                }
            }
            // Intercept semua key saat YouTube mode agar remote bisa ganti channel
            if (isYouTubeMode) {
                if (onKeyDown(kc, event)) return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

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
                if (!panelOpen && !categoryFullOpen)      openPanel();
                else if (panelOpen && !categoryFullOpen)  openCategoryFull();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
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
                else showExitConfirmDialog();
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
    @Override protected void onPause()  { super.onPause();  if (player != null) player.pause(); if (isYouTubeMode) youtubeWebView.onPause(); }
    @Override protected void onResume() {
        super.onResume(); if (player != null) { if (!isYouTubeMode) player.play(); }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override protected void onDestroy() {
        super.onDestroy();
        if (player != null) { player.release(); player = null; }
        youtubeWebView.destroy();
        handler.removeCallbacksAndMessages(null);
    }
    @Override public void onBackPressed() {
        if (categoryFullOpen) { closeCategoryFull(); return; }
        if (panelOpen) { hidePanel(); return; }
        showExitConfirmDialog();
    }

    /** Dialog konfirmasi keluar app — pakai palette & font app */
    private void showExitConfirmDialog() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_exit, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        dialog.show();
        TextView btnCancel  = dialogView.findViewById(R.id.btn_exit_cancel);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_exit_confirm);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            finishAffinity(); // tutup semua Activity (Player + Main) sekaligus
        });
        // Poin 6: warna teks berubah saat ditekan (#E4EEF0) dan kembali (#16232A)
        btnConfirm.setOnTouchListener((v, ev) -> {
            int action = ev.getAction();
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                ((TextView) v).setTextColor(0xFFE4EEF0);
            } else if (action == android.view.MotionEvent.ACTION_UP
                    || action == android.view.MotionEvent.ACTION_CANCEL) {
                ((TextView) v).setTextColor(0xFF16232A);
            }
            return false;
        });
    }
    /** Buka MainActivity (settings/playlist) — selalu buat instance baru jika perlu */
    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        // FLAG_ACTIVITY_REORDER_TO_FRONT: jika MainActivity sudah ada di stack, pindahkan ke depan
        // FLAG_ACTIVITY_CLEAR_TOP: jika tidak ada, buat baru dan clear stack di atasnya
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        // Jangan finish() PlayerActivity — biarkan di back stack agar bisa balik ke player
    }

    /**
     * Interface dipanggil dari JavaScript YouTube IFrame API
     * untuk mengirim event error ke Android.
     */
    private class YouTubeAndroidInterface {
        @JavascriptInterface
        public void onYouTubeError(int errorCode) {
            runOnUiThread(() -> {
                if (isYouTubeMode) {
                    youtubeWebView.setVisibility(View.GONE);
                    videoLoading.setVisibility(View.VISIBLE);
                    tvLoadingMsg.setText(getString(R.string.player_unavailable));
                }
            });
        }

        @JavascriptInterface
        public void onVideoEnded() {
            runOnUiThread(() -> {
                // Playlist mode → otomatis lanjut ke video berikutnya
                if (isYouTubeMode && !ytPlaylistVideoIds.isEmpty()) {
                    playNextYtPlaylistItem();
                }
            });
        }
    }

}

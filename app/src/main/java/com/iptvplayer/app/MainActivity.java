package com.iptvplayer.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.view.animation.DecelerateInterpolator;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;
import android.view.GestureDetector;
import android.animation.ValueAnimator;
import android.animation.ObjectAnimator;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends androidx.appcompat.app.AppCompatActivity {

    // ===== PAGES =====
    private View pageWelcome, pageSource, pageUrl, pageName, pageSettings, pagePlaylists, pageAppSettings;

    // ===== WELCOME =====
    private View btnWelcomeAdd;
    private android.widget.ImageView checkboxUrlIcon, checkboxFileIcon;

    // ===== SOURCE =====
    private View btnSourceNext, sourceUrlItem, sourceFileItem;
    private TextView tvSourceNote;
    private String selectedSource = null; // "url" or "file"

    // ===== URL =====
    private View btnUrlBack, btnUrlClear;
    private TextView btnUrlNext;
    private EditText etUrl;
    private TextView tvChCountUrl;
    private List<Channel> pendingChannels = new ArrayList<>();

    // ===== NAME =====
    private View btnNameBack, btnNameClear, btnNameSave;
    private EditText etName;
    private TextView tvChCountName;
    private TextView radioYes, radioNo;
    private View radioBoxYes, radioBoxNo;
    private View toggleThumb;
    private FrameLayout toggleContainer;
    private boolean toggleIsYes = true;
    private boolean downloadOnStart = true;
    private String pendingUrl = "";
    private boolean isFetching = false; // guard: cegah double tap saat loading

    // ===== SETTINGS =====
    private View btnSettingsExit, btnStartWatch, btnGoPlaylists, btnAddPlaylistSettings;
    private View btnEpg;
    private android.widget.Switch switchLoadLast, switchSubtitle;
    private android.widget.TextView tvResolutionValue, tvBufferValue;
    private android.widget.TextView tvSettingsPlaylistName;
    // State "first tap" untuk 2x klik: null=belum ada, "start"/"playlists"/"epg"
    private String settingsSelectedMenu = null;

    // ===== PLAYLISTS =====
    private View btnPlaylistsBack, btnUpdatePlaylist, btnSwitchPlaylist, btnAddPlaylistMain;
    private LinearLayout playlistListContainer;
    private TextView tvUpdateSuccess;
    private TextView tvPlaylistHeaderCount;

    // ===== SWITCHER =====
    private View switcherBackdrop, btnSwitcherClose;
    private LinearLayout switcherList;

    // ===== LOADING =====
    private View loadingOverlay;
    private View cardLoading, cardSuccess, cardError;
    private View progressFill;
    private android.widget.TextView tvChCountLoading, tvLoadingLabel;
    // Animasi dots & counter
    private android.os.Handler dotsHandler = new android.os.Handler();
    private Runnable dotsRunnable;
    private int dotsCount = 0;
    private ValueAnimator counterAnimator;
    private ValueAnimator progressAnimator;
    private TextView tvLoadingText, tvLoadingSub;

    // ===== STATE =====
    private List<String> pageHistory = new ArrayList<>();
    private List<Playlist> playlists = new ArrayList<>();
    private int currentPlaylistIdx = 0;

    private PrefsManager prefs;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler handler = new Handler(Looper.getMainLooper());

    private ActivityResultLauncher<String> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sembunyikan status bar + nav bar SEBELUM layout di-render — cegah flicker hitam
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(0xFFE4EEF0);
        getWindow().setNavigationBarColor(0xFFE4EEF0);

        setContentView(R.layout.activity_main);

        // Immersive sticky fullscreen setelah layout siap
        hideSystemUI();

        prefs = new PrefsManager(this);
        playlists = prefs.loadPlaylists();
        currentPlaylistIdx = prefs.getCurrentPlaylistIndex();

        bindViews();
        setupListeners();
        setupFilePicker();

        // Decide starting page
        if (playlists.isEmpty()) {
            showPage("welcome");
        } else {
            // Jika sudah ada playlist, langsung play tanpa perlu klik "Mulai Menonton"
            boolean launchedFromIcon = getIntent().getAction() != null &&
                    getIntent().getAction().equals(android.content.Intent.ACTION_MAIN);
            if (launchedFromIcon) {
                autoPlay();
            } else {
                showPage("settings");
            }
        }
    }

    // Sembunyikan status bar + navigation bar (truly fullscreen, immersive sticky)
    @SuppressWarnings("deprecation")
    private void hideSystemUI() {
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    // Pastikan fullscreen tetap aktif saat window mendapat fokus kembali
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    private void bindViews() {
        // Pages
        pageWelcome = findViewById(R.id.page_welcome);
        pageSource = findViewById(R.id.page_source);
        pageUrl = findViewById(R.id.page_url);
        pageName = findViewById(R.id.page_name);
        pageSettings = findViewById(R.id.page_settings);
        pageAppSettings = findViewById(R.id.page_app_settings);
        pagePlaylists = findViewById(R.id.page_playlists);

        // Welcome
        btnWelcomeAdd = findViewById(R.id.btn_welcome_add);

        // Source
        btnSourceNext = findViewById(R.id.btn_source_next);
        checkboxUrlIcon = (android.widget.ImageView) findViewById(R.id.checkbox_url);
        checkboxFileIcon = (android.widget.ImageView) findViewById(R.id.checkbox_file);
        sourceUrlItem = findViewById(R.id.source_url_item);
        sourceFileItem = findViewById(R.id.source_file_item);
        tvSourceNote = findViewById(R.id.tv_source_note);

        // URL
        btnUrlBack = findViewById(R.id.btn_url_back);
        btnUrlClear = findViewById(R.id.btn_url_clear);
        btnUrlNext = (TextView) findViewById(R.id.btn_url_next);
        etUrl = findViewById(R.id.et_url);
        tvChCountUrl = findViewById(R.id.tv_ch_count_url);

        // Name
        btnNameBack = findViewById(R.id.btn_name_back);
        btnNameClear = findViewById(R.id.btn_name_clear);
        btnNameSave = findViewById(R.id.btn_name_save);
        etName = findViewById(R.id.et_name);
        tvChCountName = findViewById(R.id.tv_ch_count_name);
        radioYes = (TextView) findViewById(R.id.radio_yes);
        radioNo = (TextView) findViewById(R.id.radio_no);
        toggleThumb = findViewById(R.id.toggle_thumb);
        toggleContainer = (FrameLayout) findViewById(R.id.toggle_container);
        radioBoxYes = findViewById(R.id.radio_box_yes);
        radioBoxNo = findViewById(R.id.radio_box_no);

        // Settings
        btnSettingsExit = findViewById(R.id.btn_settings_exit);
        btnStartWatch   = findViewById(R.id.btn_start_watch);
        btnGoPlaylists  = findViewById(R.id.btn_go_playlists);
        btnAddPlaylistSettings = findViewById(R.id.btn_add_playlist_settings);
        btnEpg = findViewById(R.id.btn_epg);
        switchLoadLast  = findViewById(R.id.switch_load_last);
        switchSubtitle  = findViewById(R.id.switch_subtitle);
        tvResolutionValue = findViewById(R.id.tv_resolution_value);
        tvBufferValue     = findViewById(R.id.tv_buffer_value);
        tvSettingsPlaylistName = findViewById(R.id.tv_settings_playlist_name);

        // Playlists
        btnPlaylistsBack = findViewById(R.id.btn_playlists_back);
        btnUpdatePlaylist = findViewById(R.id.btn_update_playlist);
        btnSwitchPlaylist = findViewById(R.id.btn_switch_playlist);
        playlistListContainer = findViewById(R.id.playlist_list_container);
        tvUpdateSuccess = findViewById(R.id.tv_update_success);
        tvPlaylistHeaderCount = (TextView) findViewById(R.id.tv_playlist_header_count);
        btnAddPlaylistMain = findViewById(R.id.btn_add_playlist_main);

        // Switcher
        switcherBackdrop = findViewById(R.id.switcher_backdrop);
        btnSwitcherClose = findViewById(R.id.btn_switcher_close);
        switcherList = findViewById(R.id.switcher_list);

        // Loading
        loadingOverlay = findViewById(R.id.loading_overlay);
        tvLoadingText = findViewById(R.id.tv_loading_text);
        tvLoadingSub = findViewById(R.id.tv_loading_sub);
        cardLoading = findViewById(R.id.card_loading);
        cardSuccess = findViewById(R.id.card_success);
        cardError   = findViewById(R.id.card_error);
        progressFill = findViewById(R.id.progress_fill);
        tvChCountLoading = findViewById(R.id.tv_ch_count_loading);
        tvLoadingLabel   = findViewById(R.id.tv_loading_label);
    }

    private void setupListeners() {
        // Welcome
        btnWelcomeAdd.setOnClickListener(v -> showPageWithTransition("source"));

        // Source
        btnSourceNext.setOnClickListener(v -> {
            if (selectedSource == null) {
                android.widget.Toast.makeText(this, getString(R.string.source_choose_first), android.widget.Toast.LENGTH_SHORT).show();
            } else if ("url".equals(selectedSource)) {
                showPageWithTransition("url");
            } else if ("file".equals(selectedSource)) {
                filePickerLauncher.launch("*/*");
            }
        });
        sourceUrlItem.setOnClickListener(v -> selectSource("url"));
        sourceFileItem.setOnClickListener(v -> selectSource("file"));

        // URL
        btnUrlBack.setOnClickListener(v -> showPageWithTransition("source"));
        btnUrlClear.setOnClickListener(v -> { etUrl.setText(""); tvChCountUrl.setText(""); pendingChannels.clear(); });
        btnUrlNext.setOnClickListener(v -> fetchPlaylist());
        btnUrlNext.setOnTouchListener((v, event) -> {
            // Abaikan sentuhan saat sedang fetch
            if (isFetching) return true;
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    // Pressed: bg orange + teks putih
                    v.setBackgroundResource(R.drawable.bg_add_playlist_btn_pressed);
                    ((TextView) v).setTextColor(0xFFFFFFFF);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                    // Released: kembali normal lalu jalankan
                    v.setBackgroundResource(R.drawable.bg_add_playlist_btn);
                    ((TextView) v).setTextColor(0xFF16232A);
                    v.performClick();
                    break;
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.setBackgroundResource(R.drawable.bg_add_playlist_btn);
                    ((TextView) v).setTextColor(0xFF16232A);
                    break;
            }
            return true;
        });
        etUrl.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) {
                if (s.length() == 0) tvChCountUrl.setText("");
            }
        });
        etUrl.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                // Fokus: bg putih #E4EEF0, teks & hint gelap Mirage
                etUrl.setTextColor(0xFF16232A);
                etUrl.setHintTextColor(0x8016232A); // Mirage 50% opacity
            } else {
                // Unfocus: bg gelap Mirage, teks & hint terang Wild Sand
                etUrl.setTextColor(0xFFE4EEF0);
                etUrl.setHintTextColor(0x80E4EEF0); // Wild Sand 50% opacity
            }
        });

        etUrl.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_GO) {
                fetchPlaylist();
                return true;
            }
            return false;
        });

        // Name
        btnNameBack.setOnClickListener(v -> showPageWithTransition("url"));
        btnNameClear.setOnClickListener(v -> etName.setText(""));
        // Done button: default #E4EEF0 teks gelap; saat ada teks → bg #FF5B04 teks putih
        btnNameSave.setOnClickListener(v -> saveName());
        btnNameSave.setOnTouchListener((v, event) -> {
            boolean hasText = etName != null && etName.getText().length() > 0;
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    if (hasText) {
                        // Ada teks: pressed orange lebih gelap
                        v.setBackgroundResource(R.drawable.bg_done_btn_active_pressed);
                        ((TextView) v).setTextColor(0xFFFFFFFF);
                    } else {
                        // Kosong: pressed abu-abu
                        v.setBackgroundResource(R.drawable.bg_done_btn_pressed);
                        ((TextView) v).setTextColor(0xFF16232A);
                    }
                    break;
                case android.view.MotionEvent.ACTION_UP:
                    // Kembalikan ke state sesuai teks
                    v.setBackgroundResource(hasText ? R.drawable.bg_done_btn_active : R.drawable.bg_done_btn);
                    ((TextView) v).setTextColor(hasText ? 0xFFFFFFFF : 0xFF16232A);
                    v.performClick();
                    break;
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.setBackgroundResource(hasText ? R.drawable.bg_done_btn_active : R.drawable.bg_done_btn);
                    ((TextView) v).setTextColor(hasText ? 0xFFFFFFFF : 0xFF16232A);
                    break;
            }
            return true;
        });

        // TextWatcher: ubah bg & warna teks Done saat ada/tidak ada teks
        if (etName != null) {
            etName.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                public void onTextChanged(CharSequence s, int st, int b, int c) {}
                public void afterTextChanged(android.text.Editable s) {
                    boolean hasText = s.length() > 0;
                    if (btnNameSave != null) {
                        btnNameSave.setBackgroundResource(
                            hasText ? R.drawable.bg_done_btn_active : R.drawable.bg_done_btn);
                        ((TextView) btnNameSave).setTextColor(
                            hasText ? 0xFFFFFFFF : 0xFF16232A);
                    }
                }
            });

            // Focus change: unfocused=teal gelap teks terang, fokus=putih teks gelap
            etName.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    etName.setTextColor(0xFF16232A);
                    etName.setHintTextColor(0x80607D8B);
                } else {
                    etName.setTextColor(0xFFE4EEF0);
                    etName.setHintTextColor(0x80E4EEF0);
                }
            });
        }

        setupToggleSwipe();
        etName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveName();
                return true;
            }
            return false;
        });

        // Settings
        // Settings: 2x klik — pertama highlight, kedua eksekusi
        btnSettingsExit.setOnClickListener(v -> handleExitTap());
        setupSettingsMenuButton(btnStartWatch,   "start");
        setupSettingsMenuButton(btnGoPlaylists,  "playlists");
        setupSettingsMenuButton(btnEpg,          "epg");

        // App Settings page
        if (pageAppSettings != null) {
            View btnBack = pageAppSettings.findViewById(R.id.btn_app_settings_back);
            if (btnBack != null) btnBack.setOnClickListener(v -> showPageWithTransition("settings"));

            View rowRes = pageAppSettings.findViewById(R.id.row_resolution);
            if (rowRes != null) rowRes.setOnClickListener(v -> showResolutionPickerDialog());

            View rowBuf = pageAppSettings.findViewById(R.id.row_buffer);
            if (rowBuf != null) rowBuf.setOnClickListener(v -> showBufferPickerDialog());

            if (switchLoadLast != null) {
                switchLoadLast.setChecked(prefs.getLoadLastChannel());
                switchLoadLast.setOnCheckedChangeListener((btn, checked) -> prefs.setLoadLastChannel(checked));
            }
            if (switchSubtitle != null) {
                switchSubtitle.setChecked(prefs.getSubtitleEnabled());
                switchSubtitle.setOnCheckedChangeListener((btn, checked) -> prefs.setSubtitleEnabled(checked));
            }
        }
        // Touch: pressed state → orange (page 3 PDF)
        setupMenuPressedState(btnStartWatch,  "start");
        setupMenuPressedState(btnGoPlaylists, "playlists");
        setupMenuPressedState(btnEpg,         "epg");
        btnAddPlaylistSettings.setOnClickListener(v -> showPageWithTransition("source"));

        // Playlists
        btnPlaylistsBack.setOnClickListener(v -> showPageWithTransition("settings"));
        btnUpdatePlaylist.setOnClickListener(v -> updateCurrentPlaylist());
        btnSwitchPlaylist.setOnClickListener(v -> showSwitcher());
        if (btnAddPlaylistMain != null) {
            btnAddPlaylistMain.setOnClickListener(v -> showPageWithTransition("source"));
        }

        // Switcher
        switcherBackdrop.setOnClickListener(v -> hideSwitcher());
        btnSwitcherClose.setOnClickListener(v -> hideSwitcher());
    }

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) handleFileUri(uri);
                });
    }

    // ===== PAGE NAVIGATION =====

    private void showPageWithTransition(String page) {
        android.view.View currentVisible = null;
        for (android.view.View v : new android.view.View[]{pageWelcome, pageSource, pageUrl, pageName, pageSettings, pagePlaylists, pageAppSettings}) {
            if (v != null && v.getVisibility() == android.view.View.VISIBLE) { currentVisible = v; break; }
        }
        final android.view.View outView = currentVisible;
        if (outView != null) {
            // Pure fade out — tanpa slide
            outView.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                outView.setAlpha(1f);
                showPage(page);
                // Pure fade in halaman baru
                android.view.View inView = null;
                if ("source".equals(page)) inView = pageSource;
                else if ("url".equals(page)) inView = pageUrl;
                else if ("name".equals(page)) inView = pageName;
                else if ("settings".equals(page)) inView = pageSettings;
                else if ("welcome".equals(page)) inView = pageWelcome;
                else if ("playlists".equals(page)) inView = pagePlaylists;
                else if ("app_settings".equals(page)) inView = pageAppSettings;
                if (inView != null) {
                    inView.setAlpha(0f);
                    inView.animate().alpha(1f).setDuration(200).start();
                }
            }).start();
        } else {
            showPage(page);
        }
    }

    private void showPage(String page) {
        pageWelcome.setVisibility(View.GONE);
        pageSource.setVisibility(View.GONE);
        pageUrl.setVisibility(View.GONE);
        pageName.setVisibility(View.GONE);
        pageSettings.setVisibility(View.GONE);
        pagePlaylists.setVisibility(View.GONE);
        if (pageAppSettings != null) pageAppSettings.setVisibility(View.GONE);

        // Add to history
        if (pageHistory.isEmpty() || !pageHistory.get(pageHistory.size() - 1).equals(page)) {
            pageHistory.add(page);
        }

        switch (page) {
            case "welcome":
                pageWelcome.setVisibility(View.VISIBLE);
                break;
            case "source":
                pageSource.setVisibility(View.VISIBLE);
                // Reset source selection
                selectSource(null);
                break;
            case "url":
                pageUrl.setVisibility(View.VISIBLE);
                break;
            case "name":
                pageName.setVisibility(View.VISIBLE);
                selectDownload(true);
                updateChCountName();
                break;
            case "settings":
                pageSettings.setVisibility(View.VISIBLE);
                updateSettingsPlaylistName();
                settingsSelectedMenu = null;
                resetAllSettingsMenus();
                break;
            case "playlists":
                pagePlaylists.setVisibility(View.VISIBLE);
                rebuildPlaylistList();
                break;
            case "app_settings":
                if (pageAppSettings != null) {
                    pageAppSettings.setVisibility(View.VISIBLE);
                    updateAppSettingsPage();
                }
                break;
        }
    }

    private void autoPlay() {
        if (playlists.isEmpty()) {
            showPage("welcome");
            return;
        }
        Playlist pl = playlists.get(currentPlaylistIdx);
        if (pl.channels == null || pl.channels.isEmpty()) {
            // Channel belum ada, tampilkan settings
            showPage("settings");
            return;
        }
        // Load last channel jika preferensi aktif, sinon mulai dari 0
        int chIdx = prefs.getLoadLastChannel() ? prefs.getCurrentChannelIndex() : 0;
        if (chIdx >= pl.channels.size()) chIdx = 0;
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("playlist_index", currentPlaylistIdx);
        intent.putExtra("channel_index", chIdx);
        startActivity(intent);
        // Tetap tampilkan settings di belakang agar bisa balik
        showPage("settings");
    }

    private void confirmExit() {
        // Reset exit button ke normal setelah dialog
        if (btnSettingsExit != null)
            btnSettingsExit.setBackgroundResource(R.drawable.bg_exit_btn_normal);

        // Custom dialog pakai palette
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_exit, null);
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setView(dialogView);
        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        dialog.show();

        TextView btnCancel  = dialogView.findViewById(R.id.btn_exit_cancel);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_exit_confirm);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> { dialog.dismiss(); finishAffinity(); });
    }

    /** Tap pertama X → orange; tap kedua → confirmExit */
    private void handleExitTap() {
        if (btnSettingsExit == null) return;
        if ("exit_pending".equals(settingsSelectedMenu)) {
            settingsSelectedMenu = null;
            btnSettingsExit.setBackgroundResource(R.drawable.bg_exit_btn_normal);
            confirmExit();
        } else {
            // Reset semua menu highlight dulu, baru set X orange
            settingsSelectedMenu = "exit_pending";
            resetAllSettingsMenus();
            btnSettingsExit.setBackgroundResource(R.drawable.bg_exit_btn_pressed);
        }
    }

    private void goBack() {
        // Jika sedang di settings atau welcome (root), tampilkan konfirmasi keluar
        String current = pageHistory.isEmpty() ? "" : pageHistory.get(pageHistory.size() - 1);
        if (current.equals("settings") || current.equals("welcome") || pageHistory.size() <= 1) {
            confirmExit();
            return;
        }
        pageHistory.remove(pageHistory.size() - 1);
        String prev = pageHistory.remove(pageHistory.size() - 1);
        showPageWithTransition(prev);
    }

    @Override
    public void onBackPressed() {
        if (switcherBackdrop.getVisibility() == View.VISIBLE) {
            hideSwitcher();
            return;
        }
        goBack();
    }

    // ===== SOURCE SELECTION =====

    private void selectSource(String source) {
        selectedSource = source;

        // Tampilkan segitiga di option yang dipilih, sembunyikan yang lain
        if (checkboxUrlIcon != null) {
            checkboxUrlIcon.setVisibility("url".equals(source) ? android.view.View.VISIBLE : android.view.View.GONE);
        }
        if (checkboxFileIcon != null) {
            checkboxFileIcon.setVisibility("file".equals(source) ? android.view.View.VISIBLE : android.view.View.GONE);
        }

        // Update selected state pada button (untuk selector drawable)
        if (sourceUrlItem != null) sourceUrlItem.setSelected("url".equals(source));
        if (sourceFileItem != null) sourceFileItem.setSelected("file".equals(source));

        // Update hint text
        if (source == null) {
            if (tvSourceNote != null) tvSourceNote.setText("Choose one to continue");
        } else if ("url".equals(source)) {
            if (tvSourceNote != null) tvSourceNote.setText("Choose one to continue\nand press next step");
        } else if ("file".equals(source)) {
            if (tvSourceNote != null) tvSourceNote.setText("Choose one to continue\nand press next step");
        }
    }

    // ===== FETCH PLAYLIST =====

    private void fetchPlaylist() {
        // Guard: jangan fetch ulang jika sedang berjalan
        if (isFetching) return;

        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "Masukkan URL terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validasi format URL sebelum request
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            showErrorCard();
            return;
        }
        try {
            new java.net.URL(url).toURI();
        } catch (Exception e) {
            showErrorCard();
            return;
        }

        pendingUrl = url;
        isFetching = true;
        setUrlButtonEnabled(false); // nonaktifkan tombol saat loading

        showLoading("Mengunduh playlist...", "Mohon bersabar");
        // Progress + counter mulai BERSAMAAN sejak awal, lambat dan nikmati UI
        loadingOverlay.post(() -> startLoadingAnimation());

        executor.execute(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                Request req = new Request.Builder().url(url).build();
                Response resp = client.newCall(req).execute();
                if (!resp.isSuccessful()) {
                    throw new IOException("HTTP " + resp.code());
                }
                String body = resp.body().string();
                List<Channel> channels = M3UParser.parse(body);

                handler.post(() -> {
                    pendingChannels = channels;
                    if (!channels.isEmpty()) {
                        showSuccessCard(channels.size());
                    } else {
                        // Tidak ada channel = URL valid tapi bukan M3U → error card
                        isFetching = false;
                        setUrlButtonEnabled(true);
                        showErrorCard();
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    isFetching = false;
                    setUrlButtonEnabled(true);
                    showErrorCard();
                });
            }
        });
    }

    /** Aktif/nonaktifkan tombol Add playlist secara visual */
    private void setUrlButtonEnabled(boolean enabled) {
        if (btnUrlNext == null) return;
        btnUrlNext.setClickable(enabled);
        btnUrlNext.setFocusable(enabled);
        btnUrlNext.setAlpha(enabled ? 1f : 0.45f);
    }

    // ===== FILE HANDLING =====

    private void handleFileUri(Uri uri) {
        pendingUrl = uri.toString();
        showLoading("Membaca file...", "");

        executor.execute(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                reader.close();

                List<Channel> channels = M3UParser.parse(sb.toString());
                handler.post(() -> {
                    hideLoading();
                    pendingChannels = channels;
                    tvChCountUrl.setText(channels.size() + " channel ditemukan");
                    if (!channels.isEmpty()) {
                        showPageWithTransition("name");
                    } else {
                        Toast.makeText(this, getString(R.string.status_no_channels), Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    hideLoading();
                    Toast.makeText(this, getString(R.string.status_error_file, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ===== SAVE PLAYLIST =====

    private void saveName() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) name = "Playlist " + (playlists.size() + 1);

        Playlist pl = new Playlist(name, pendingUrl, selectedSource, downloadOnStart);
        pl.channels = new ArrayList<>(pendingChannels);
        pl.lastUpdated = System.currentTimeMillis();

        playlists.add(pl);
        currentPlaylistIdx = playlists.size() - 1;
        prefs.savePlaylists(playlists);
        prefs.setCurrentPlaylistIndex(currentPlaylistIdx);
        prefs.setCurrentChannelIndex(0);

        // Clear
        pendingChannels.clear();
        etUrl.setText("");
        etName.setText("");
        pendingUrl = "";

        showPageWithTransition("settings");
        updateSettingsPlaylistName();
    }

    private void updateChCountName() {
        if (!pendingChannels.isEmpty()) {
            tvChCountName.setText(pendingChannels.size() + " channel");
        }
    }

    private void selectDownload(boolean yes) {
        downloadOnStart = yes;
        toggleIsYes = yes;
        // Alpha teks: selected=opaque, unselected=dim
        if (radioYes != null) radioYes.setAlpha(yes ? 1f : 0.55f);
        if (radioNo != null) radioNo.setAlpha(!yes ? 1f : 0.55f);
    }

    // ===== START WATCHING =====

    private void startWatching() {
        if (playlists.isEmpty()) {
            Toast.makeText(this, getString(R.string.playlists_add_first), Toast.LENGTH_SHORT).show();
            return;
        }
        Playlist pl = playlists.get(currentPlaylistIdx);
        if (pl.channels == null || pl.channels.isEmpty()) {
            Toast.makeText(this, getString(R.string.playlists_empty_try_update), Toast.LENGTH_SHORT).show();
            return;
        }

        int chIdx = prefs.getCurrentChannelIndex();
        if (chIdx >= pl.channels.size()) chIdx = 0;

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("playlist_index", currentPlaylistIdx);
        intent.putExtra("channel_index", chIdx);
        startActivity(intent);
    }

    // ===== PLAYLISTS PAGE =====

    private void rebuildPlaylistList() {
        playlistListContainer.removeAllViews();

        // Update header count
        if (tvPlaylistHeaderCount != null) {
            tvPlaylistHeaderCount.setText(playlists.size() + " playlist tersimpan");
        }

        for (int i = 0; i < playlists.size(); i++) {
            final int idx = i;
            Playlist pl = playlists.get(i);

            View item = getLayoutInflater().inflate(R.layout.item_playlist_row, playlistListContainer, false);

            TextView tvNumber = item.findViewById(R.id.tv_pl_number);
            TextView tvName = item.findViewById(R.id.tv_pl_name);
            TextView tvChCount = item.findViewById(R.id.tv_pl_ch_count);
            TextView tvAutoUpdate = item.findViewById(R.id.tv_pl_auto_update);
            TextView tvUrlPreview = item.findViewById(R.id.tv_pl_url_preview);
            TextView tvBadgeActive = item.findViewById(R.id.tv_pl_badge_active);
            TextView btnEditName = item.findViewById(R.id.btn_pl_edit_name);
            TextView btnEditUrl = item.findViewById(R.id.btn_pl_edit_url);
            TextView btnDelete = item.findViewById(R.id.btn_pl_delete);

            // Set nomor urut
            if (tvNumber != null) tvNumber.setText(String.valueOf(i + 1));

            // Set nama
            tvName.setText(pl.name);

            // Set info channel count
            if (tvChCount != null) tvChCount.setText(pl.getChannelCount() + " channel");

            // Set status auto update
            if (tvAutoUpdate != null) tvAutoUpdate.setText(pl.downloadOnStart ? "Auto update" : "Manual");

            // Set URL preview
            if (tvUrlPreview != null) {
                String urlText = (pl.url != null && !pl.url.isEmpty()) ? pl.url : "(file lokal)";
                tvUrlPreview.setText(urlText);
            }

            // Tandai badge AKTIF jika ini playlist yang sedang dipakai
            if (tvBadgeActive != null) {
                tvBadgeActive.setVisibility(idx == currentPlaylistIdx ? android.view.View.VISIBLE : android.view.View.GONE);
            }

            // Highlight card jika aktif
            if (idx == currentPlaylistIdx) {
                item.setBackgroundResource(R.drawable.bg_playlist_card_active);
            } else {
                item.setBackgroundResource(R.drawable.bg_playlist_card);
            }

            playlistListContainer.addView(item);

            // Klik card = jadikan playlist aktif
            item.setOnClickListener(v -> {
                currentPlaylistIdx = idx;
                prefs.setCurrentPlaylistIndex(idx);
                rebuildPlaylistList();
            });

            // Tombol Edit Nama
            if (btnEditName != null) {
                btnEditName.setOnClickListener(v -> showEditDialog(idx, "name"));
            }

            // Tombol Edit URL
            if (btnEditUrl != null) {
                btnEditUrl.setOnClickListener(v -> showEditDialog(idx, "url"));
            }

            // Tombol Hapus — tampilkan konfirmasi dulu
            if (btnDelete != null) {
                btnDelete.setOnClickListener(v -> showDeletePlaylistDialog(idx));
            }
        }
    }


    /** Dialog konfirmasi hapus playlist (style = dialog_exit) */
    private void showDeletePlaylistDialog(int idx) {
        if (idx < 0 || idx >= playlists.size()) return;
        String plName = playlists.get(idx).name;

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        android.view.View v = getLayoutInflater().inflate(R.layout.dialog_delete_playlist, null);
        builder.setView(v);

        android.widget.TextView tvName = v.findViewById(R.id.tv_delete_playlist_name);
        android.widget.TextView btnCancel  = v.findViewById(R.id.btn_delete_cancel);
        android.widget.TextView btnConfirm = v.findViewById(R.id.btn_delete_confirm);

        if (tvName != null) tvName.setText(
            getString(R.string.delete_playlist_confirm) + " "" + plName + ""?");

        android.app.AlertDialog dlg = builder.create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        dlg.show();

        if (btnCancel  != null) btnCancel.setOnClickListener(x -> dlg.dismiss());
        if (btnConfirm != null) btnConfirm.setOnClickListener(x -> {
            dlg.dismiss();
            playlists.remove(idx);
            if (currentPlaylistIdx >= playlists.size())
                currentPlaylistIdx = Math.max(0, playlists.size() - 1);
            prefs.savePlaylists(playlists);
            prefs.setCurrentPlaylistIndex(currentPlaylistIdx);
            rebuildPlaylistList();
            if (playlists.isEmpty()) showPageWithTransition("welcome");
        });
    }

    /**
     * Dialog untuk edit nama atau URL playlist
     * @param idx   index playlist
     * @param field "name" atau "url"
     */
    private void showEditDialog(int idx, String field) {
        if (idx < 0 || idx >= playlists.size()) return;
        Playlist pl = playlists.get(idx);

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_playlist, null);
        builder.setView(dialogView);

        TextView tvTitle = dialogView.findViewById(R.id.tv_edit_title);
        TextView tvSubtitle = dialogView.findViewById(R.id.tv_edit_subtitle);
        android.widget.EditText etValue = dialogView.findViewById(R.id.et_edit_value);
        TextView btnCancel = dialogView.findViewById(R.id.btn_edit_cancel);
        TextView btnSave = dialogView.findViewById(R.id.btn_edit_save);

        if ("name".equals(field)) {
            tvTitle.setText(getString(R.string.playlist_edit_title));
            tvSubtitle.setText(getString(R.string.playlist_rename_for, pl.name));
            etValue.setHint(getString(R.string.playlist_name_placeholder));
            etValue.setText(pl.name);
        } else {
            tvTitle.setText(getString(R.string.playlist_url_label));
            tvSubtitle.setText(getString(R.string.playlist_rename_for, pl.name));
            etValue.setHint("URL playlist M3U...");
            etValue.setText(pl.url != null ? pl.url : "");
            etValue.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        }

        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        dialog.show();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String newValue = etValue.getText().toString().trim();
            if (newValue.isEmpty()) {
                Toast.makeText(this, getString(R.string.name_empty_error), Toast.LENGTH_SHORT).show();
                return;
            }
            if ("name".equals(field)) {
                pl.name = newValue;
            } else {
                pl.url = newValue;
            }
            prefs.savePlaylists(playlists);
            rebuildPlaylistList();
            dialog.dismiss();
        });
    }

    private void updateCurrentPlaylist() {
        if (playlists.isEmpty()) return;
        Playlist pl = playlists.get(currentPlaylistIdx);
        if (pl.url == null || pl.url.isEmpty()) {
            Toast.makeText(this, getString(R.string.playlists_cannot_update_file), Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading("Mengunduh ulang playlist...", pl.name);

        executor.execute(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                Request req = new Request.Builder().url(pl.url).build();
                Response resp = client.newCall(req).execute();
                String content = resp.body().string();
                List<Channel> channels = M3UParser.parse(content);

                handler.post(() -> {
                    hideLoading();
                    pl.channels = channels;
                    pl.lastUpdated = System.currentTimeMillis();
                    prefs.savePlaylists(playlists);
                    tvUpdateSuccess.setVisibility(View.VISIBLE);
                    rebuildPlaylistList();
                    handler.postDelayed(() -> tvUpdateSuccess.setVisibility(View.GONE), 3000);
                });
            } catch (Exception e) {
                handler.post(() -> {
                    hideLoading();
                    Toast.makeText(this, getString(R.string.status_error_generic, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ===== SWITCHER =====

    private void showSwitcher() {
        switcherList.removeAllViews();
        for (int i = 0; i < playlists.size(); i++) {
            final int idx = i;
            Playlist pl = playlists.get(i);

            TextView item = new TextView(this);
            item.setText("▶  " + pl.name + "\n" + pl.getChannelCount() + " channel");
            item.setTextColor(0xffffffff);
            item.setTextSize(15f);
            item.setPadding(dp(16), dp(14), dp(16), dp(14));
            item.setLineSpacing(0, 1.3f);
            int bg = (idx == currentPlaylistIdx) ?
                    R.drawable.bg_switcher_item_active : R.drawable.bg_switcher_item;
            item.setBackgroundResource(bg);
            item.setFocusable(true);
            item.setClickable(true);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            item.setLayoutParams(lp);

            item.setOnClickListener(v -> {
                currentPlaylistIdx = idx;
                prefs.setCurrentPlaylistIndex(idx);
                prefs.setCurrentChannelIndex(0);
                hideSwitcher();
            });

            switcherList.addView(item);
        }
        switcherBackdrop.setVisibility(View.VISIBLE);
    }

    private void hideSwitcher() {
        switcherBackdrop.setVisibility(View.GONE);
    }

    // ===== LOADING =====

    private void showLoading(String text, String sub) {
        // Compat: set text untuk kode lama yang masih pakai tvLoadingText
        if (tvLoadingText != null) tvLoadingText.setText(text);
        if (tvLoadingSub  != null) tvLoadingSub.setText(sub);
        // Reset ke state loading
        showLoadingCard(0);
    }

    /** Mulai animasi loading: progress + counter jalan bersamaan dari awal */
    private void startLoadingAnimation() {
        // Progress bar: 0 → 70% dengan durasi panjang (user menikmati UI)
        progressFill.post(() -> {
            int trackWidth = ((android.view.View) progressFill.getParent()).getWidth();
            if (trackWidth == 0) trackWidth = (int)(280 * getResources().getDisplayMetrics().density);
            final int target = (int)(trackWidth * 0.72f);
            if (progressAnimator != null) progressAnimator.cancel();
            progressAnimator = ValueAnimator.ofInt(0, target);
            progressAnimator.setDuration(6500); // 6.5 detik untuk 0→72% — user nikmati UI
            progressAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator(0.5f));
            progressAnimator.addUpdateListener(a -> {
                progressFill.getLayoutParams().width = (int) a.getAnimatedValue();
                progressFill.requestLayout();
            });
            progressAnimator.start();
        });

        // Counter: 0 → angka simulasi (akan di-reset saat sukses)
        // Angka naik lambat bersamaan progress, memberikan kesan real-time
        if (counterAnimator != null) counterAnimator.cancel();
        counterAnimator = ValueAnimator.ofInt(0, 999); // simulasi naik
        counterAnimator.setDuration(90000); // lambat, dibatalkan saat sukses — naik bersama progress
        counterAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
        counterAnimator.addUpdateListener(a -> {
            int val = (int) a.getAnimatedValue();
            if (tvChCountLoading != null) tvChCountLoading.setText(val + " Ch");
        });
        counterAnimator.start();
    }

        /** Tampilkan loading card dan mulai animasi dengan fade in halus */
    private void showLoadingCard(int initialChannels) {
        // Reset state card
        cardLoading.setVisibility(View.VISIBLE);
        cardSuccess.setVisibility(View.GONE);
        cardError.setVisibility(View.GONE);
        cardLoading.setAlpha(0f);

        // Reset progress fill ke 0
        progressFill.getLayoutParams().width = 0;
        progressFill.requestLayout();

        // Reset counter & dots
        if (tvChCountLoading != null) tvChCountLoading.setText("0 Ch");
        startDotsAnimation();

        // Fade in overlay background dulu (0→1, 250ms)
        loadingOverlay.setAlpha(0f);
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingOverlay.animate()
            .alpha(1f)
            .setDuration(250)
            .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
            .withEndAction(() -> {
                // Lalu fade in card loading (0→1, 300ms)
                cardLoading.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            })
            .start();
    }

    /** Tampilkan card sukses + animasi counter channel + progress bar penuh */
    private void showSuccessCard(int channelCount) {
        stopDotsAnimation();

        // Hentikan animasi loading, lanjut ke 100%
        stopProgressAnimation();

        // Counter dan progress berjalan BERSAMAAN ke nilai akhir
        animateProgressToFull(null); // progress → 100%
        animateCounter(                // counter → channelCount, bersamaan
            tvChCountLoading != null
                ? parseChCount(tvChCountLoading.getText().toString())
                : 0,
            channelCount,
            () -> {
                // Counter selesai → fade out card loading, fade in card sukses
                loadingOverlay.postDelayed(() -> {
                    android.widget.TextView tvMsg = cardSuccess.findViewById(R.id.tv_success_msg);
                    if (tvMsg != null) tvMsg.setText(channelCount + " channels\nimported.");
                    cardSuccess.setAlpha(0f);
                    cardSuccess.setVisibility(View.VISIBLE);
                    // Fade out loading card
                    cardLoading.animate()
                        .alpha(0f)
                        .setDuration(250)
                        .withEndAction(() -> {
                            cardLoading.setVisibility(View.GONE);
                            // Fade in success card
                            cardSuccess.animate()
                                .alpha(1f)
                                .setDuration(350)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                                .withEndAction(() -> {
                                    // Tampil 2 detik lalu fade out seluruh overlay
                                    loadingOverlay.postDelayed(() -> {
                                        fadeOutOverlay(() -> {
                                            hideLoading();
                                            proceedAfterImport();
                                        });
                                    }, 2000);
                                })
                                .start();
                        })
                        .start();
                }, 400);
            });
    }

    /** Parse angka dari teks "123 Ch" → 123 */
    private int parseChCount(CharSequence text) {
        try {
            return Integer.parseInt(text.toString().replace(" Ch", "").trim());
        } catch (Exception e) { return 0; }
    }

    /** Tampilkan card error dengan fade halus */
    private void showErrorCard() {
        isFetching = false;
        setUrlButtonEnabled(true);
        stopDotsAnimation();
        stopProgressAnimation();
        cardError.setAlpha(0f);
        cardError.setVisibility(View.VISIBLE);
        // Fade out loading card
        cardLoading.animate()
            .alpha(0f)
            .setDuration(220)
            .withEndAction(() -> {
                cardLoading.setVisibility(View.GONE);
                // Fade in error card
                cardError.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withEndAction(() -> {
                        // Tampil 2.5 detik lalu fade out
                        loadingOverlay.postDelayed(() -> {
                            fadeOutOverlay(this::hideLoading);
                        }, 2500);
                    })
                    .start();
            })
            .start();
    }

    /** Fade out seluruh overlay lalu callback */
    private void fadeOutOverlay(Runnable onDone) {
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .setInterpolator(new android.view.animation.AccelerateInterpolator())
            .withEndAction(() -> {
                if (onDone != null) onDone.run();
            })
            .start();
    }

    private void hideLoading() {
        stopDotsAnimation();
        stopProgressAnimation();
        loadingOverlay.clearAnimation();
        loadingOverlay.setAlpha(1f);
        loadingOverlay.setVisibility(View.GONE);
        cardLoading.setAlpha(1f);
        cardLoading.setVisibility(View.VISIBLE);
        cardSuccess.setAlpha(0f);
        cardSuccess.setVisibility(View.GONE);
        cardError.setAlpha(0f);
        cardError.setVisibility(View.GONE);
    }

    /** Animasi titik-titik berjalan pada label "+ Adding Playlist..." */
    private void startDotsAnimation() {
        stopDotsAnimation();
        dotsCount = 0;
        dotsRunnable = new Runnable() {
            @Override public void run() {
                dotsCount = (dotsCount + 1) % 4;
                String dots = dotsCount == 0 ? "" : dotsCount == 1 ? "." : dotsCount == 2 ? ".." : "...";
                if (tvLoadingLabel != null)
                    tvLoadingLabel.setText("+ Adding Playlist" + dots);
                dotsHandler.postDelayed(this, 500);
            }
        };
        dotsHandler.post(dotsRunnable);
    }

    private void stopDotsAnimation() {
        if (dotsRunnable != null) dotsHandler.removeCallbacks(dotsRunnable);
        dotsRunnable = null;
    }

    /** Animasi counter angka dari start ke end */
    private void animateCounter(int start, int end, Runnable onDone) {
        if (counterAnimator != null) counterAnimator.cancel();
        counterAnimator = ValueAnimator.ofInt(start, end);
        counterAnimator.setDuration(Math.max(2000, Math.min(3500, end * 15L)));
        counterAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        counterAnimator.addUpdateListener(a -> {
            int val = (int) a.getAnimatedValue();
            if (tvChCountLoading != null) tvChCountLoading.setText(val + " Ch");
        });
        counterAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (onDone != null) onDone.run();
            }
        });
        counterAnimator.start();
    }

    /** Animasi progress bar fill dari posisi saat ini ke 100% */
    private void animateProgressToFull(Runnable onDone) {
        progressFill.post(() -> {
            int trackWidth = ((android.view.View) progressFill.getParent()).getWidth();
            if (trackWidth == 0) trackWidth = (int)(280 * getResources().getDisplayMetrics().density);
            final int target = trackWidth;
            int current = progressFill.getLayoutParams().width;

            if (progressAnimator != null) progressAnimator.cancel();
            progressAnimator = ValueAnimator.ofInt(current, target);
            progressAnimator.setDuration(1400); // lambat ke 100% — nikmati transisi
            progressAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
            progressAnimator.addUpdateListener(a -> {
                int w = (int) a.getAnimatedValue();
                progressFill.getLayoutParams().width = w;
                progressFill.requestLayout();
            });
            progressAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) {
                    if (onDone != null) onDone.run();
                }
            });
            progressAnimator.start();
        });
    }

    /** Animasi progress bar fill mengikuti progress fetch (0-100%) */
    private void animateProgressTo(float fraction) {
        progressFill.post(() -> {
            int trackWidth = ((android.view.View) progressFill.getParent()).getWidth();
            if (trackWidth == 0) return;
            int target = (int)(trackWidth * fraction);
            if (progressAnimator != null) progressAnimator.cancel();
            progressAnimator = ValueAnimator.ofInt(progressFill.getLayoutParams().width, target);
            progressAnimator.setDuration(2800); // lambat agar user bisa menikmati
            progressAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator(0.6f));
            progressAnimator.addUpdateListener(a -> {
                progressFill.getLayoutParams().width = (int) a.getAnimatedValue();
                progressFill.requestLayout();
            });
            progressAnimator.start();
        });
    }

    private void stopProgressAnimation() {
        if (progressAnimator != null) { progressAnimator.cancel(); progressAnimator = null; }
    }

    /** Dipanggil setelah sukses import — fade in ke halaman input nama playlist */
    private void proceedAfterImport() {
        isFetching = false;
        setUrlButtonEnabled(true);
        // Reset field nama & button Done ke state awal
        if (etName != null) {
            etName.setText("");
            etName.setTextColor(0xFFE4EEF0);
            etName.setHintTextColor(0x80E4EEF0);
            etName.clearFocus();
        }
        if (btnNameSave != null) {
            btnNameSave.setBackgroundResource(R.drawable.bg_done_btn);
            ((android.widget.TextView) btnNameSave).setTextColor(0xFF16232A);
        }
        // Fade in halus ke page_name
        showPageWithTransition("name");
    }

    // ===== UTILS =====

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
    /** Update teks nama playlist — titik oranye menempel di akhir nama */
    private void updateSettingsPlaylistName() {
        if (tvSettingsPlaylistName == null) return;
        String name = playlists.isEmpty() ? "My Playlist" : playlists.get(currentPlaylistIdx).name;
        // Titik oranye (#FF5B04) langsung menempel di akhir nama, satu baris
        String full = name + ".";
        android.text.SpannableString span = new android.text.SpannableString(full);
        span.setSpan(
            new android.text.style.ForegroundColorSpan(0xFFFF5B04),
            full.length() - 1, full.length(),
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        tvSettingsPlaylistName.setText(span);
    }

    /** Setup menu button settings dengan logika 2x klik */
    private void setupSettingsMenuButton(View btn, String menuKey) {
        if (btn == null) return;
        btn.setOnClickListener(v -> {
            if (menuKey.equals(settingsSelectedMenu)) {
                // Klik kedua: eksekusi
                settingsSelectedMenu = null;
                resetAllSettingsMenus();
                executeSettingsMenu(menuKey);
            } else {
                // Klik pertama: reset X orange dulu, lalu highlight menu ini
                settingsSelectedMenu = menuKey;
                if (btnSettingsExit != null)
                    btnSettingsExit.setBackgroundResource(R.drawable.bg_exit_btn_normal);
                resetAllSettingsMenus();
                highlightSettingsMenu(btn, menuKey);
            }
        });
    }

    /** Highlight menu terpilih: bg putih, icon ▶ muncul, teks gelap */
    private void highlightSettingsMenu(View btn, String menuKey) {
        if (btn == null) return;
        btn.setBackgroundResource(R.drawable.bg_settings_menu_selected);
        updateMenuLabelColor(menuKey, 0xFF16232A, true);
    }

    /** Reset semua menu ke state normal */
    private void resetAllSettingsMenus() {
        for (String key : new String[]{"start", "playlists", "epg"}) {
            View b = getMenuBtn(key);
            if (b != null) {
                b.setBackgroundResource(R.drawable.bg_settings_menu_normal);
                updateMenuLabelColor(key, 0xFFE4EEF0, false);
            }
        }
    }

    private View getMenuBtn(String key) {
        switch (key) {
            case "start":     return btnStartWatch;
            case "playlists": return btnGoPlaylists;
            case "epg":       return btnEpg;
        }
        return null;
    }

    private void updateMenuLabelColor(String key, int labelColor, boolean showIcon) {
        int labelId, iconId;
        switch (key) {
            case "start":
                labelId = R.id.tv_start_label; iconId = R.id.ic_start_play; break;
            case "playlists":
                labelId = R.id.tv_playlist_label; iconId = R.id.ic_playlist_play; break;
            case "epg":
                labelId = R.id.tv_epg_label; iconId = R.id.ic_epg_play; break;
            default: return;
        }
        android.widget.TextView lbl = pageSettings != null ? pageSettings.findViewById(labelId) : null;
        android.widget.ImageView ico = pageSettings != null ? pageSettings.findViewById(iconId) : null;
        if (lbl != null) lbl.setTextColor(labelColor);
        if (ico != null) {
            ico.setVisibility(showIcon ? android.view.View.VISIBLE : android.view.View.GONE);
            // Tint: gelap saat selected (putih bg), putih saat pressed (orange bg)
            int tint = (labelColor == 0xFF16232A) ? 0xFF16232A : 0xFFE4EEF0;
            ico.setColorFilter(tint, android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }

    /** Setup touch: pressed → orange; released → kembali ke selected/normal */
    private void setupMenuPressedState(View btn, String menuKey) {
        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    // Pressed: orange
                    v.setBackgroundResource(R.drawable.bg_settings_menu_pressed);
                    updateMenuLabelColor(menuKey, 0xFFFFFFFF, true);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    // Kembali ke state sebelum pressed
                    boolean isSelected = menuKey.equals(settingsSelectedMenu);
                    v.setBackgroundResource(isSelected
                        ? R.drawable.bg_settings_menu_selected
                        : R.drawable.bg_settings_menu_normal);
                    updateMenuLabelColor(menuKey,
                        isSelected ? 0xFF16232A : 0xFFE4EEF0,
                        isSelected);
                    if (event.getAction() == android.view.MotionEvent.ACTION_UP)
                        v.performClick();
                    break;
            }
            return true;
        });
    }

    /** Eksekusi aksi menu setelah klik kedua */
    private void executeSettingsMenu(String menuKey) {
        switch (menuKey) {
            case "start":
                startWatching();
                break;
            case "playlists":
                showPageWithTransition("playlists");
                break;
            case "epg":
                showPageWithTransition("app_settings");
                break;
        }
    }

    /** Update tampilan nilai di app settings page */
    private void updateAppSettingsPage() {
        if (tvResolutionValue != null) {
            String res = prefs.getResolution();
            String label;
            if (PrefsManager.RES_LOWEST.equals(res)) label = getString(R.string.appsettings_resolution_lowest);
            else if (PrefsManager.RES_HIGHEST.equals(res)) label = getString(R.string.appsettings_resolution_highest);
            else label = getString(R.string.appsettings_resolution_auto);
            tvResolutionValue.setText(label);
        }
        if (tvBufferValue != null) {
            int secs = prefs.getBufferSecs();
            tvBufferValue.setText(secs + " " + getString(R.string.appsettings_buffer_unit));
        }
        if (switchLoadLast != null) switchLoadLast.setChecked(prefs.getLoadLastChannel());
        if (switchSubtitle  != null) switchSubtitle.setChecked(prefs.getSubtitleEnabled());
    }



    /**
     * Custom picker dialog dengan palette PlayM3U.
     * onItemSelected dipanggil setiap kali user tap item (belum final).
     * onSave dipanggil saat user tekan Save.
     */
    private void showCustomPickerDialog(
            String title,
            String[] items,
            int initialSel,
            java.util.function.Consumer<Integer> onItemSelected,
            Runnable onSave) {

        android.view.View dialogView = getLayoutInflater()
                .inflate(R.layout.dialog_custom_picker, null);

        android.widget.TextView tvTitle = dialogView.findViewById(R.id.tv_picker_title);
        android.widget.ListView lv      = dialogView.findViewById(R.id.lv_picker_items);
        android.widget.TextView btnCancel = dialogView.findViewById(R.id.btn_picker_cancel);
        android.widget.TextView btnSave   = dialogView.findViewById(R.id.btn_picker_save);

        if (tvTitle != null) tvTitle.setText(title);

        final int[] currentSel = {initialSel};

        // Adapter custom
        android.widget.ArrayAdapter<String> adapter =
            new android.widget.ArrayAdapter<String>(this,
                R.layout.item_picker_row, R.id.tv_picker_item_label, items) {
            @Override
            public android.view.View getView(int pos, android.view.View convert,
                    android.view.ViewGroup parent) {
                android.view.View row = super.getView(pos, convert, parent);
                android.view.View dot = row.findViewById(R.id.view_picker_dot);
                if (dot != null) {
                    dot.setBackgroundResource(
                        pos == currentSel[0]
                            ? R.drawable.bg_picker_dot_sel
                            : R.drawable.bg_picker_dot_unsel);
                }
                android.widget.TextView lbl = row.findViewById(R.id.tv_picker_item_label);
                if (lbl != null) {
                    lbl.setTextColor(pos == currentSel[0]
                        ? android.graphics.Color.parseColor("#FF5B04")
                        : android.graphics.Color.parseColor("#E4EEF0"));
                }
                return row;
            }
        };

        if (lv != null) {
            lv.setAdapter(adapter);
            // Scroll ke item terpilih
            lv.post(() -> lv.setSelection(Math.max(0, initialSel - 2)));
        }

        android.app.AlertDialog.Builder builder =
            new android.app.AlertDialog.Builder(this);
        builder.setView(dialogView);
        android.app.AlertDialog dlg = builder.create();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT));
        }
        dlg.show();

        if (lv != null) {
            lv.setOnItemClickListener((parent, v2, pos, id) -> {
                currentSel[0] = pos;
                onItemSelected.accept(pos);
                adapter.notifyDataSetChanged();
            });
        }

        if (btnCancel != null) btnCancel.setOnClickListener(x -> dlg.dismiss());
        if (btnSave != null) btnSave.setOnClickListener(x -> {
            onSave.run();
            dlg.dismiss();
        });
    }

    /** Dialog buffer size 0–60 detik (custom picker) */
    private void showBufferPickerDialog() {
        int current = prefs.getBufferSecs();
        String unit = getString(R.string.appsettings_buffer_unit);
        String[] options = new String[61];
        for (int i = 0; i <= 60; i++) options[i] = i + " " + unit;
        int[] selected = {Math.min(current, 60)};
        showCustomPickerDialog(
            getString(R.string.appsettings_buffer_title),
            options, selected[0],
            which -> {
                selected[0] = which;
            },
            () -> {
                prefs.setBufferSecs(selected[0]);
                updateAppSettingsPage();
            }
        );
    }

    /** Dialog resolusi dari halaman app settings (custom picker) */
    private void showResolutionPickerDialog() {
        String current = prefs.getResolution();
        String[] keys = {PrefsManager.RES_AUTO, PrefsManager.RES_LOWEST, PrefsManager.RES_HIGHEST};
        String[] labels = {
            getString(R.string.appsettings_resolution_auto),
            getString(R.string.appsettings_resolution_lowest),
            getString(R.string.appsettings_resolution_highest)
        };
        int checked = 0;
        for (int i = 0; i < keys.length; i++) { if (keys[i].equals(current)) { checked = i; break; } }
        int[] selected = {checked};
        showCustomPickerDialog(
            getString(R.string.appsettings_resolution_title),
            labels, checked,
            which -> selected[0] = which,
            () -> {
                prefs.setResolution(keys[selected[0]]);
                updateAppSettingsPage();
            }
        );
    }
    private void autoSavePlaylist() {
        // Generate nama dari URL
        String name = pendingUrl;
        try {
            java.net.URL u = new java.net.URL(pendingUrl);
            String host = u.getHost();
            if (host != null && !host.isEmpty()) {
                name = host.replace("www.", "");
            }
        } catch (Exception ignored) {}
        if (name.isEmpty()) name = "Playlist " + (playlists.size() + 1);

        Playlist pl = new Playlist(name, pendingUrl, selectedSource, downloadOnStart);
        pl.channels = new ArrayList<>(pendingChannels);
        pl.lastUpdated = System.currentTimeMillis();

        playlists.add(pl);
        currentPlaylistIdx = playlists.size() - 1;
        prefs.savePlaylists(playlists);
        prefs.setCurrentPlaylistIndex(currentPlaylistIdx);

        pendingChannels.clear();
        pendingUrl = "";

        rebuildPlaylistList();
        autoPlay();
    }

    /**
     * Toggle Yes/No dengan animasi geser halus.
     * - Tap kiri  → Yes
     * - Tap kanan → No
     * - Swipe kiri/kanan → ikuti arah
     */
    private void setupToggleSwipe() {
        if (toggleContainer == null || toggleThumb == null) return;

        // Inisialisasi posisi thumb ke Yes (kiri)
        toggleContainer.post(() -> {
            int thumbW = toggleContainer.getWidth() / 2;
            animateThumb(toggleIsYes ? 0f : (float) thumbW, false);
            selectDownload(toggleIsYes);
        });

        final float[] downX = {0f};
        final boolean[] dragging = {false};
        final float[] thumbStartX = {0f};
        final float SWIPE_THRESHOLD = 30f;

        toggleContainer.setOnTouchListener((v, event) -> {
            int thumbW = toggleContainer.getWidth() / 2;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = event.getX();
                    thumbStartX[0] = toggleThumb.getTranslationX();
                    dragging[0] = false;
                    break;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - downX[0];
                    if (Math.abs(dx) > SWIPE_THRESHOLD || dragging[0]) {
                        dragging[0] = true;
                        // Clamp posisi thumb antara 0 dan thumbW
                        float newX = thumbStartX[0] + dx;
                        newX = Math.max(0f, Math.min((float) thumbW, newX));
                        toggleThumb.setTranslationX(newX);
                        // Alpha teks realtime saat drag
                        float ratio = newX / thumbW;           // 0=Yes, 1=No
                        if (radioYes != null) radioYes.setAlpha(1f - ratio * 0.45f);
                        if (radioNo != null)  radioNo.setAlpha(0.55f + ratio * 0.45f);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging[0]) {
                        // Snap: jika sudah lewat tengah → No, sebaliknya → Yes
                        float currentX = toggleThumb.getTranslationX();
                        boolean goNo = currentX > thumbW * 0.5f;
                        animateThumb(goNo ? (float) thumbW : 0f, true);
                        selectDownload(!goNo);
                    } else {
                        // Tap biasa
                        boolean tapYes = event.getX() < toggleContainer.getWidth() / 2f;
                        animateThumb(tapYes ? 0f : (float) thumbW, true);
                        selectDownload(tapYes);
                    }
                    v.performClick();
                    break;
            }
            return true;
        });
    }

    /** Geser thumb ke posisi target dengan animasi smooth */
    private void animateThumb(float targetX, boolean animate) {
        if (toggleThumb == null) return;
        if (!animate) {
            toggleThumb.setTranslationX(targetX);
            return;
        }
        ObjectAnimator anim = ObjectAnimator.ofFloat(toggleThumb, "translationX",
                toggleThumb.getTranslationX(), targetX);
        anim.setDuration(220);
        anim.setInterpolator(new DecelerateInterpolator(1.5f));
        anim.start();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            android.view.View currentFocus = getCurrentFocus();
            if (currentFocus instanceof android.widget.EditText) {
                // Cek apakah tap berada di luar EditText
                android.graphics.Rect rect = new android.graphics.Rect();
                currentFocus.getGlobalVisibleRect(rect);
                if (!rect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    currentFocus.clearFocus();
                    InputMethodManager imm = (InputMethodManager)
                            getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

}

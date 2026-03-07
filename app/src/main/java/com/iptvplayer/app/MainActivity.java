package com.iptvplayer.app;

import android.app.Activity;
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
    private View pageWelcome, pageSource, pageUrl, pageName, pageSettings, pagePlaylists;

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

    // ===== SETTINGS =====
    private View btnSettingsExit, btnStartWatch, btnGoPlaylists, btnAddPlaylistSettings;

    // ===== PLAYLISTS =====
    private View btnPlaylistsBack, btnUpdatePlaylist, btnSwitchPlaylist;
    private LinearLayout playlistListContainer;
    private TextView tvUpdateSuccess;

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
        btnStartWatch = findViewById(R.id.btn_start_watch);
        btnGoPlaylists = findViewById(R.id.btn_go_playlists);
        btnAddPlaylistSettings = findViewById(R.id.btn_add_playlist_settings);

        // Playlists
        btnPlaylistsBack = findViewById(R.id.btn_playlists_back);
        btnUpdatePlaylist = findViewById(R.id.btn_update_playlist);
        btnSwitchPlaylist = findViewById(R.id.btn_switch_playlist);
        playlistListContainer = findViewById(R.id.playlist_list_container);
        tvUpdateSuccess = findViewById(R.id.tv_update_success);

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
                android.widget.Toast.makeText(this, "Pilih sumber terlebih dahulu", android.widget.Toast.LENGTH_SHORT).show();
            } else if ("url".equals(selectedSource)) {
                showPageWithTransition("url");
            } else if ("file".equals(selectedSource)) {
                filePickerLauncher.launch("*/*");
            }
        });
        sourceUrlItem.setOnClickListener(v -> selectSource("url"));
        sourceFileItem.setOnClickListener(v -> selectSource("file"));

        // URL
        btnUrlBack.setOnClickListener(v -> showPage("source"));
        btnUrlClear.setOnClickListener(v -> { etUrl.setText(""); tvChCountUrl.setText(""); pendingChannels.clear(); });
        btnUrlNext.setOnClickListener(v -> fetchPlaylist());
        btnUrlNext.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    // Pressed: bg orange + teks putih (sesuai gambar 1)
                    v.setBackgroundResource(R.drawable.bg_add_playlist_btn_pressed);
                    ((TextView) v).setTextColor(0xFFFFFFFF);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                    // Released: kembali normal
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
                // Fokus: bg terang, teks gelap
                etUrl.setTextColor(0xFF16232A);
            } else {
                // Unfocus: bg gelap, teks terang #E4EEF0
                etUrl.setTextColor(0xFFE4EEF0);
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
        btnNameBack.setOnClickListener(v -> showPage("url"));
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
        btnSettingsExit.setOnClickListener(v -> confirmExit());
        btnStartWatch.setOnClickListener(v -> startWatching());
        btnGoPlaylists.setOnClickListener(v -> showPage("playlists"));
        btnAddPlaylistSettings.setOnClickListener(v -> showPage("source"));

        // Playlists
        btnPlaylistsBack.setOnClickListener(v -> showPage("settings"));
        btnUpdatePlaylist.setOnClickListener(v -> updateCurrentPlaylist());
        btnSwitchPlaylist.setOnClickListener(v -> showSwitcher());

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
        for (android.view.View v : new android.view.View[]{pageWelcome, pageSource, pageUrl, pageName, pageSettings, pagePlaylists}) {
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
                break;
            case "playlists":
                pagePlaylists.setVisibility(View.VISIBLE);
                rebuildPlaylistList();
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
        int chIdx = prefs.getCurrentChannelIndex();
        if (chIdx >= pl.channels.size()) chIdx = 0;
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("playlist_index", currentPlaylistIdx);
        intent.putExtra("channel_index", chIdx);
        startActivity(intent);
        // Tetap tampilkan settings di belakang agar bisa balik
        showPage("settings");
    }

    private void confirmExit() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Keluar Aplikasi")
            .setMessage("Yakin ingin keluar?")
            .setPositiveButton("Keluar", (d, w) -> finish())
            .setNegativeButton("Batal", null)
            .show();
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
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "Masukkan URL terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingUrl = url;

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
                String content = resp.body().string();
                List<Channel> channels = M3UParser.parse(content);

                handler.post(() -> {
                    pendingChannels = channels;
                    if (!channels.isEmpty()) {
                        // Tampilkan sukses dengan animasi counter + progress penuh
                        showSuccessCard(channels.size());
                    } else {
                        hideLoading();
                        Toast.makeText(this, "Tidak ada channel ditemukan di URL ini", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    showErrorCard();
                });
            }
        });
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
                        showPage("name");
                    } else {
                        Toast.makeText(this, "Tidak ada channel ditemukan", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    hideLoading();
                    Toast.makeText(this, "Gagal baca file: " + e.getMessage(), Toast.LENGTH_LONG).show();
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

        showPage("settings");
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
            Toast.makeText(this, "Tambahkan playlist terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }
        Playlist pl = playlists.get(currentPlaylistIdx);
        if (pl.channels == null || pl.channels.isEmpty()) {
            Toast.makeText(this, "Playlist kosong, coba update", Toast.LENGTH_SHORT).show();
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
        for (int i = 0; i < playlists.size(); i++) {
            final int idx = i;
            Playlist pl = playlists.get(i);

            View item = getLayoutInflater().inflate(R.layout.item_playlist_row, playlistListContainer, false);
            TextView tvName = item.findViewById(R.id.tv_pl_name);
            TextView tvInfo = item.findViewById(R.id.tv_pl_info);

            tvName.setText(pl.name);
            tvInfo.setText(pl.getChannelCount() + " channel • " +
                    (pl.downloadOnStart ? "Auto update" : "Manual"));

            if (i < playlists.size() - 1) {
                // Add divider
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(0x14ffffff);
                playlistListContainer.addView(item);
                playlistListContainer.addView(divider);
            } else {
                playlistListContainer.addView(item);
            }

            item.setOnClickListener(v -> {
                currentPlaylistIdx = idx;
                prefs.setCurrentPlaylistIndex(idx);
            });

            item.setOnLongClickListener(v -> {
                // Long press = delete
                playlists.remove(idx);
                if (currentPlaylistIdx >= playlists.size()) {
                    currentPlaylistIdx = Math.max(0, playlists.size() - 1);
                }
                prefs.savePlaylists(playlists);
                prefs.setCurrentPlaylistIndex(currentPlaylistIdx);
                rebuildPlaylistList();
                if (playlists.isEmpty()) showPage("welcome");
                return true;
            });
        }
    }

    private void updateCurrentPlaylist() {
        if (playlists.isEmpty()) return;
        Playlist pl = playlists.get(currentPlaylistIdx);
        if (pl.url == null || pl.url.isEmpty()) {
            Toast.makeText(this, "Tidak bisa update playlist dari file", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(this, "Gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
    /** Simpan playlist otomatis tanpa meminta nama — nama diambil dari URL atau auto-generate */
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

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
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
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

    // ===== SOURCE =====
    private View btnSourceBack, sourceUrlItem, sourceFileItem;
    private View checkboxUrl, checkboxFile;
    private TextView tvSourceNote;
    private String selectedSource = null; // "url" or "file"

    // ===== URL =====
    private View btnUrlBack, btnUrlClear, btnUrlNext;
    private EditText etUrl;
    private TextView tvChCountUrl;
    private List<Channel> pendingChannels = new ArrayList<>();

    // ===== NAME =====
    private View btnNameBack, btnNameClear, btnNameSave;
    private EditText etName;
    private TextView tvChCountName;
    private View radioYes, radioNo, radioBoxYes, radioBoxNo;
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
        setContentView(R.layout.activity_main);

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
        btnSourceBack = findViewById(R.id.btn_source_back);
        sourceUrlItem = findViewById(R.id.source_url_item);
        sourceFileItem = findViewById(R.id.source_file_item);
        checkboxUrl = findViewById(R.id.checkbox_url);
        checkboxFile = findViewById(R.id.checkbox_file);
        tvSourceNote = findViewById(R.id.tv_source_note);

        // URL
        btnUrlBack = findViewById(R.id.btn_url_back);
        btnUrlClear = findViewById(R.id.btn_url_clear);
        btnUrlNext = findViewById(R.id.btn_url_next);
        etUrl = findViewById(R.id.et_url);
        tvChCountUrl = findViewById(R.id.tv_ch_count_url);

        // Name
        btnNameBack = findViewById(R.id.btn_name_back);
        btnNameClear = findViewById(R.id.btn_name_clear);
        btnNameSave = findViewById(R.id.btn_name_save);
        etName = findViewById(R.id.et_name);
        tvChCountName = findViewById(R.id.tv_ch_count_name);
        radioYes = findViewById(R.id.radio_yes);
        radioNo = findViewById(R.id.radio_no);
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
    }

    private void setupListeners() {
        // Welcome
        btnWelcomeAdd.setOnClickListener(v -> showPage("source"));

        // Source
        btnSourceBack.setOnClickListener(v -> goBack());
        sourceUrlItem.setOnClickListener(v -> selectSource("url"));
        sourceFileItem.setOnClickListener(v -> selectSource("file"));

        // URL
        btnUrlBack.setOnClickListener(v -> showPage("source"));
        btnUrlClear.setOnClickListener(v -> { etUrl.setText(""); tvChCountUrl.setText(""); pendingChannels.clear(); });
        btnUrlNext.setOnClickListener(v -> fetchPlaylist());
        etUrl.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) {
                if (s.length() == 0) tvChCountUrl.setText("");
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
        btnNameSave.setOnClickListener(v -> saveName());
        radioYes.setOnClickListener(v -> selectDownload(true));
        radioNo.setOnClickListener(v -> selectDownload(false));
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
        showPage(prev);
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
        checkboxUrl.setBackgroundResource(
                "url".equals(source) ? R.drawable.bg_checkbox_checked : R.drawable.bg_checkbox_empty);
        checkboxFile.setBackgroundResource(
                "file".equals(source) ? R.drawable.bg_checkbox_checked : R.drawable.bg_checkbox_empty);

        if ("url".equals(source)) {
            tvSourceNote.setText("Masukkan URL playlist M3U/M3U8 dari internet.\nPastikan link aktif dan dapat diakses.");
            showPage("url");
        } else if ("file".equals(source)) {
            tvSourceNote.setText("Pilih file M3U dari penyimpanan perangkat atau USB.");
            filePickerLauncher.launch("*/*");
        } else {
            tvSourceNote.setText("Pilih sumber playlist M3U Anda.");
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
                    hideLoading();
                    pendingChannels = channels;
                    tvChCountUrl.setText(channels.size() + " channel ditemukan");
                    if (!channels.isEmpty()) {
                        showPage("name");
                    } else {
                        Toast.makeText(this, "Tidak ada channel ditemukan di URL ini", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    hideLoading();
                    Toast.makeText(this, "Gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
        radioBoxYes.setBackgroundResource(yes ? R.drawable.bg_checkbox_checked : R.drawable.bg_checkbox_empty);
        radioBoxNo.setBackgroundResource(!yes ? R.drawable.bg_checkbox_checked : R.drawable.bg_checkbox_empty);
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
        tvLoadingText.setText(text);
        tvLoadingSub.setText(sub);
        loadingOverlay.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        loadingOverlay.setVisibility(View.GONE);
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
}

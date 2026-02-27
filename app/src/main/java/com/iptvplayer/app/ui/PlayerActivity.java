package com.iptvplayer.app.ui;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.iptvplayer.app.R;
import com.iptvplayer.app.data.Channel;
import com.iptvplayer.app.data.Playlist;
import com.iptvplayer.app.utils.PrefsManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private View osdLayout, chListBackdrop, chListPanel;
    private LinearLayout layoutBuffering;
    private TextView tvStatus, tvChannelName, tvChannelGroup, tvChannelNumber;
    private TextView tvPanelPlaylistName, swipeUpFb, swipeDownFb;
    private ImageView ivChannelLogo;
    private RecyclerView rvPanelChannels, rvPanelGroups;
    private EditText etPanelSearch;

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideOsdRunnable;

    private List<Channel> channels = new ArrayList<>();
    private List<Channel> filteredPanelChannels = new ArrayList<>();
    private List<String> panelGroups = new ArrayList<>();
    private String currentPanelGroup = "SEMUA";
    private int currentIdx = 0;
    private boolean panelOpen = false;
    private boolean isRetrying = false;
    private PrefsManager prefs;

    // Swipe detection
    private float touchStartX, touchStartY;
    private long touchStartTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
        setContentView(R.layout.activity_player);

        prefs = new PrefsManager(this);
        int playlistIdx = getIntent().getIntExtra("playlist_idx", 0);
        currentIdx = getIntent().getIntExtra("channel_idx", 0);

        List<Playlist> playlists = prefs.loadPlaylists();
        if (playlistIdx < playlists.size() && playlists.get(playlistIdx).channels != null) {
            channels = playlists.get(playlistIdx).channels;
            String plName = playlists.get(playlistIdx).name;
            if (tvPanelPlaylistName != null) tvPanelPlaylistName.setText(plName);
        }

        initViews();
        initPlayer();
        setupPanelGroups();

        if (!channels.isEmpty()) playChannel(currentIdx);
    }

    private void initViews() {
        playerView = findViewById(R.id.player_view);
        osdLayout = findViewById(R.id.osd_layout);
        layoutBuffering = findViewById(R.id.layout_buffering);
        tvStatus = findViewById(R.id.tv_status);
        tvChannelName = findViewById(R.id.tv_channel_name);
        tvChannelGroup = findViewById(R.id.tv_channel_group);
        tvChannelNumber = findViewById(R.id.tv_channel_number);
        ivChannelLogo = findViewById(R.id.iv_channel_logo);
        chListBackdrop = findViewById(R.id.ch_list_backdrop);
        chListPanel = findViewById(R.id.ch_list_panel);
        tvPanelPlaylistName = findViewById(R.id.tv_panel_playlist_name);
        swipeUpFb = findViewById(R.id.swipe_up_fb);
        swipeDownFb = findViewById(R.id.swipe_down_fb);
        rvPanelChannels = findViewById(R.id.rv_panel_channels);
        rvPanelGroups = findViewById(R.id.rv_panel_groups);
        etPanelSearch = findViewById(R.id.et_panel_search);

        playerView.setUseController(false);
        playerView.setOnClickListener(v -> { if (!panelOpen) toggleOsd(); });

        // Channel list button di OSD
        TextView btnChList = findViewById(R.id.btn_channel_list);
        if (btnChList != null) btnChList.setOnClickListener(v -> openPanel());

        // Close panel
        TextView btnClose = findViewById(R.id.btn_close_panel);
        if (btnClose != null) btnClose.setOnClickListener(v -> closePanel());
        if (chListBackdrop != null) chListBackdrop.setOnClickListener(v -> closePanel());

        // Search panel
        if (etPanelSearch != null) {
            etPanelSearch.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                public void onTextChanged(CharSequence s, int st, int b, int c) { filterPanel(s.toString()); }
                public void afterTextChanged(Editable s) {}
            });
        }

        // Panel group adapter
        if (rvPanelGroups != null) {
            rvPanelGroups.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        }
        if (rvPanelChannels != null) {
            rvPanelChannels.setLayoutManager(new LinearLayoutManager(this));
        }
    }

    private void setupPanelGroups() {
        LinkedHashMap<String, Integer> groupMap = new LinkedHashMap<>();
        for (Channel ch : channels) {
            String g = ch.group != null && !ch.group.isEmpty() ? ch.group : "Umum";
            groupMap.put(g, groupMap.getOrDefault(g, 0) + 1);
        }
        panelGroups.clear();
        panelGroups.add("SEMUA");
        panelGroups.addAll(groupMap.keySet());

        if (rvPanelGroups != null) {
            MainActivity.GroupAdapter ga = new MainActivity.GroupAdapter(panelGroups, group -> {
                currentPanelGroup = group;
                filterPanel(etPanelSearch != null ? etPanelSearch.getText().toString() : "");
            });
            rvPanelGroups.setAdapter(ga);
        }

        filterPanel("");
    }

    private void filterPanel(String query) {
        filteredPanelChannels.clear();
        String q = query.toLowerCase().trim();
        for (Channel ch : channels) {
            String g = ch.group != null ? ch.group : "Umum";
            boolean groupMatch = "SEMUA".equals(currentPanelGroup) || g.equals(currentPanelGroup);
            boolean searchMatch = q.isEmpty() || ch.name.toLowerCase().contains(q)
                || (ch.group != null && ch.group.toLowerCase().contains(q));
            if (groupMatch && searchMatch) filteredPanelChannels.add(ch);
        }
        if (rvPanelChannels != null) {
            MainActivity.ChannelAdapter ca = new MainActivity.ChannelAdapter(filteredPanelChannels, ch -> {
                int realIdx = channels.indexOf(ch);
                closePanel();
                handler.postDelayed(() -> playChannel(realIdx), 300);
            });
            rvPanelChannels.setAdapter(ca);
            // Scroll ke channel aktif
            for (int i = 0; i < filteredPanelChannels.size(); i++) {
                if (channels.indexOf(filteredPanelChannels.get(i)) == currentIdx) {
                    int finalI = i;
                    rvPanelChannels.post(() -> rvPanelChannels.scrollToPosition(finalI));
                    break;
                }
            }
        }
    }

    private void openPanel() {
        if (panelOpen) return;
        panelOpen = true;
        osdLayout.setVisibility(View.GONE);
        handler.removeCallbacks(hideOsdRunnable != null ? hideOsdRunnable : () -> {});

        chListBackdrop.setVisibility(View.VISIBLE);
        chListPanel.setVisibility(View.VISIBLE);
        ObjectAnimator.ofFloat(chListPanel, "translationX", -300f * getResources().getDisplayMetrics().density, 0f)
            .setDuration(250).start();
        filterPanel("");
    }

    private void closePanel() {
        if (!panelOpen) return;
        panelOpen = false;
        float width = 300f * getResources().getDisplayMetrics().density;
        ObjectAnimator anim = ObjectAnimator.ofFloat(chListPanel, "translationX", 0f, -width);
        anim.setDuration(220);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.start();
        handler.postDelayed(() -> {
            chListBackdrop.setVisibility(View.GONE);
            chListPanel.setVisibility(View.GONE);
        }, 220);
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                switch (state) {
                    case Player.STATE_BUFFERING:
                        showBuffering("Buffering...");
                        break;
                    case Player.STATE_READY:
                        hideBuffering();
                        isRetrying = false;
                        break;
                    case Player.STATE_ENDED:
                        showBuffering("Stream berakhir");
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (!isRetrying) {
                    isRetrying = true;
                    showBuffering("Mencoba ulang...");
                    handler.postDelayed(() -> { if (!isFinishing()) playChannel(currentIdx); }, 2000);
                } else {
                    isRetrying = false;
                    showBuffering(getReadableError(error));
                }
            }
        });
    }

    private void showBuffering(String msg) {
        layoutBuffering.setVisibility(View.VISIBLE);
        tvStatus.setText(msg);
    }

    private void hideBuffering() {
        layoutBuffering.setVisibility(View.GONE);
    }

    private void playChannel(int idx) {
        if (channels.isEmpty()) return;
        idx = ((idx % channels.size()) + channels.size()) % channels.size();
        currentIdx = idx;
        isRetrying = false;
        prefs.saveLastPosition(0, idx);

        Channel ch = channels.get(idx);

        // Update OSD
        tvChannelName.setText(ch.name);
        tvChannelGroup.setText(ch.group != null ? ch.group : "");
        tvChannelNumber.setText(String.valueOf(idx + 1));
        if (ch.logo != null && !ch.logo.isEmpty()) {
            Glide.with(this).load(ch.logo)
                .placeholder(R.drawable.ic_channel_placeholder)
                .error(R.drawable.ic_channel_placeholder)
                .into(ivChannelLogo);
        } else {
            ivChannelLogo.setImageResource(R.drawable.ic_channel_placeholder);
        }

        showOsd(3000);
        showBuffering("Memuat...");

        // Build data source dengan custom headers
        DefaultHttpDataSource.Factory dsFactory = new DefaultHttpDataSource.Factory()
            .setUserAgent(ch.userAgent != null && !ch.userAgent.isEmpty()
                ? ch.userAgent
                : "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 Chrome/96.0 Mobile Safari/537.36")
            .setDefaultRequestProperties(buildHeaders(ch))
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true);

        MediaItem mediaItem = MediaItem.fromUri(ch.url);
        String urlLower = ch.url.toLowerCase().split("\\?")[0];
        MediaSource mediaSource;

        if (urlLower.contains(".m3u8") || urlLower.contains("m3u8") || urlLower.contains("hls")
            || urlLower.contains("chunklist") || urlLower.contains("/live/") || urlLower.contains("playlist")) {
            mediaSource = new HlsMediaSource.Factory(dsFactory).createMediaSource(mediaItem);
        } else if (urlLower.contains(".mpd") || urlLower.contains("dash")) {
            mediaSource = new DashMediaSource.Factory(dsFactory).createMediaSource(mediaItem);
        } else {
            mediaSource = new DefaultMediaSourceFactory(dsFactory).createMediaSource(mediaItem);
        }

        player.stop();
        player.clearMediaItems();
        player.setMediaSource(mediaSource);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private java.util.HashMap<String, String> buildHeaders(Channel ch) {
        java.util.HashMap<String, String> h = new java.util.HashMap<>();
        if (ch.referrer != null && !ch.referrer.isEmpty()) {
            h.put("Referer", ch.referrer);
            h.put("Origin", ch.referrer.replaceAll("/$", ""));
        }
        return h;
    }

    private String getReadableError(PlaybackException e) {
        switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED: return "Tidak ada koneksi";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT: return "Timeout";
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS: return "Stream tidak tersedia";
            default: return "Gagal memutar";
        }
    }

    private void showOsd(long durationMs) {
        osdLayout.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOsdRunnable != null ? hideOsdRunnable : () -> {});
        hideOsdRunnable = () -> osdLayout.setVisibility(View.GONE);
        handler.postDelayed(hideOsdRunnable, durationMs);
    }

    private void toggleOsd() {
        if (osdLayout.getVisibility() == View.VISIBLE) {
            osdLayout.setVisibility(View.GONE);
            if (hideOsdRunnable != null) handler.removeCallbacks(hideOsdRunnable);
        } else {
            showOsd(4000);
        }
    }

    private void showSwipeFeedback(boolean up) {
        TextView fb = up ? swipeUpFb : swipeDownFb;
        if (fb == null) return;
        fb.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> fb.setVisibility(View.GONE), 500);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (panelOpen) return super.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getX();
                touchStartY = event.getY();
                touchStartTime = System.currentTimeMillis();
                break;
            case MotionEvent.ACTION_UP:
                float dx = event.getX() - touchStartX;
                float dy = event.getY() - touchStartY;
                long dt = System.currentTimeMillis() - touchStartTime;
                float absDx = Math.abs(dx), absDy = Math.abs(dy);
                if (dt < 400) {
                    if (absDy > 80 && absDy / Math.max(absDx, 1) > 1.5f) {
                        if (dy < 0) { showSwipeFeedback(true); playChannel(currentIdx + 1); }
                        else { showSwipeFeedback(false); playChannel(currentIdx - 1); }
                    } else if (absDx > 80 && dx < 0 && absDx / Math.max(absDy, 1) > 1.5f) {
                        openPanel();
                    }
                }
                break;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (panelOpen) {
            if (keyCode == KeyEvent.KEYCODE_BACK) { closePanel(); return true; }
            return super.onKeyDown(keyCode, event);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_DPAD_UP:
                playChannel(currentIdx - 1); return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                playChannel(currentIdx + 1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                openPanel(); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                toggleOsd(); return true;
            case KeyEvent.KEYCODE_INFO:
                showOsd(5000); return true;
            case KeyEvent.KEYCODE_BACK:
                finish(); return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override protected void onResume() { super.onResume(); if (player != null) player.setPlayWhenReady(true); }
    @Override protected void onPause() { super.onPause(); if (player != null) player.setPlayWhenReady(false); }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
    }
}

package com.iptvplayer.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends Activity {

    // Views
    private PlayerView playerView;
    private View videoLoading, blackFlash;
    private TextView tvLoadingMsg;

    // Channel info OSD
    private View channelInfo;
    private TextView tvChPlaylistName, tvChNum, tvChName, tvChGroup;
    private ImageView ivChLogo;
    private TextView tvChLogoFallback;
    private FrameLayout chLogoContainer;

    // Num overlay (TV)
    private TextView numOverlay;

    // Channel list panel
    private View chListPanel, chListBackdrop;
    private EditText etChSearch;
    private androidx.recyclerview.widget.RecyclerView rvChList;
    private TextView tvPanelTitle;

    // Swipe feedback
    private View swipeHint;
    private TextView swipeFbUp, swipeFbDown;

    // Player
    private ExoPlayer player;

    // State
    private List<Channel> channels = new ArrayList<>();
    private int currentChannelIdx = 0;
    private int playlistIdx = 0;
    private String playlistName = "";
    private boolean panelOpen = false;
    private String numBuffer = "";

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable chInfoHideRunnable;
    private Runnable numClearRunnable;
    private Runnable swipeHintHideRunnable;

    private PrefsManager prefs;
    private ChannelAdapter channelAdapter;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        setContentView(R.layout.activity_player);

        prefs = new PrefsManager(this);

        // Get playlist data
        playlistIdx = getIntent().getIntExtra("playlist_index", 0);
        currentChannelIdx = getIntent().getIntExtra("channel_index", 0);

        List<Playlist> playlists = prefs.loadPlaylists();
        if (playlistIdx < playlists.size()) {
            Playlist pl = playlists.get(playlistIdx);
            channels = pl.channels != null ? pl.channels : new ArrayList<>();
            playlistName = pl.name;
        }

        if (channels.isEmpty()) {
            finish();
            return;
        }

        bindViews();
        setupPlayer();
        setupChannelPanel();
        setupGestures();
        setupListeners();

        // Start playing first channel
        playChannel(currentChannelIdx, false);

        // Show swipe hint briefly
        showSwipeHint();
    }

    private void bindViews() {
        playerView = findViewById(R.id.player_view);
        videoLoading = findViewById(R.id.video_loading);
        blackFlash = findViewById(R.id.black_flash);
        tvLoadingMsg = findViewById(R.id.tv_loading_msg);

        channelInfo = findViewById(R.id.channel_info);
        tvChPlaylistName = findViewById(R.id.tv_ch_playlist_name);
        tvChNum = findViewById(R.id.tv_ch_num);
        tvChName = findViewById(R.id.tv_ch_name);
        tvChGroup = findViewById(R.id.tv_ch_group);
        ivChLogo = findViewById(R.id.iv_ch_logo);
        tvChLogoFallback = findViewById(R.id.tv_ch_logo_fallback);
        chLogoContainer = findViewById(R.id.ch_logo_container);

        numOverlay = findViewById(R.id.num_overlay);

        chListPanel = findViewById(R.id.ch_list_panel);
        chListBackdrop = findViewById(R.id.ch_list_backdrop);
        etChSearch = findViewById(R.id.et_ch_search);
        rvChList = findViewById(R.id.rv_ch_list);
        tvPanelTitle = findViewById(R.id.tv_panel_title);

        swipeHint = findViewById(R.id.swipe_hint);
        swipeFbUp = findViewById(R.id.swipe_fb_up);
        swipeFbDown = findViewById(R.id.swipe_fb_down);
    }

    private void setupPlayer() {
        player = new ExoPlayer.Builder(this).build();

        // Hide default controls - we manage ourselves
        playerView.setUseController(false);
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    videoLoading.setVisibility(View.VISIBLE);
                } else if (state == Player.STATE_READY) {
                    videoLoading.setVisibility(View.GONE);
                } else if (state == Player.STATE_ENDED) {
                    videoLoading.setVisibility(View.VISIBLE);
                    tvLoadingMsg.setText("Stream berakhir...");
                }
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                videoLoading.setVisibility(View.VISIBLE);
                tvLoadingMsg.setText("Error: " + error.getMessage());
            }
        });
    }

    private void setupChannelPanel() {
        tvPanelTitle.setText(playlistName.isEmpty() ? "SEMUA SALURAN" : playlistName.toUpperCase());

        channelAdapter = new ChannelAdapter(idx -> {
            playChannel(idx, true);
            hidePanel();
        });
        channelAdapter.setChannels(channels);
        channelAdapter.setActiveIndex(currentChannelIdx);

        rvChList.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        rvChList.setAdapter(channelAdapter);

        etChSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) {
                channelAdapter.applyFilter(s.toString());
            }
        });
    }

    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 80;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                if (e1 == null || e2 == null) return false;
                float dY = e2.getY() - e1.getY();
                float dX = e2.getX() - e1.getX();

                if (Math.abs(dX) > Math.abs(dY)) {
                    // Horizontal swipe
                    if (dX < -SWIPE_THRESHOLD && Math.abs(vX) > SWIPE_VELOCITY_THRESHOLD) {
                        // Swipe left → open channel list
                        openPanel();
                        return true;
                    }
                } else {
                    // Vertical swipe
                    if (Math.abs(dY) > SWIPE_THRESHOLD && Math.abs(vY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (dY < 0) {
                            // Swipe up = next channel
                            showSwipeFeedback(true);
                            playChannel(currentChannelIdx + 1, true);
                        } else {
                            // Swipe down = previous channel
                            showSwipeFeedback(false);
                            playChannel(currentChannelIdx - 1, true);
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (panelOpen) {
                    hidePanel();
                } else {
                    toggleChInfo();
                }
                return true;
            }
        });
    }

    private void setupListeners() {
        // Tap anywhere on player to toggle OSD
        playerView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });

        // Backdrop closes panel
        chListBackdrop.setOnClickListener(v -> hidePanel());
        chListBackdrop.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) hidePanel();
            return true;
        });
    }

    // ===== PLAY CHANNEL =====

    private void playChannel(int idx, boolean withFlash) {
        if (channels.isEmpty()) return;

        // Wrap around
        if (idx < 0) idx = channels.size() - 1;
        if (idx >= channels.size()) idx = 0;

        currentChannelIdx = idx;
        prefs.setCurrentChannelIndex(idx);

        Channel ch = channels.get(idx);

        if (withFlash) {
            blackFlash();
        }

        // Show loading
        videoLoading.setVisibility(View.VISIBLE);
        tvLoadingMsg.setText("Memuat...");

        // Update OSD
        updateChInfo(ch, idx);

        // Play
        try {
            player.stop();
            MediaItem media = MediaItem.fromUri(ch.url);
            player.setMediaItem(media);
            player.prepare();
            player.play();
        } catch (Exception e) {
            tvLoadingMsg.setText("Gagal: " + e.getMessage());
        }

        // Update panel active
        channelAdapter.setActiveIndex(idx);

        // Show ch info briefly
        showChInfo();
    }

    // ===== CHANNEL INFO OSD =====

    private void updateChInfo(Channel ch, int idx) {
        tvChNum.setText(String.valueOf(idx + 1));
        tvChName.setText(ch.name);
        tvChGroup.setText(ch.group);
        tvChPlaylistName.setText(playlistName);

        // Logo
        if (ch.logoUrl != null && !ch.logoUrl.isEmpty()) {
            ivChLogo.setVisibility(View.VISIBLE);
            tvChLogoFallback.setVisibility(View.GONE);
            Glide.with(this)
                    .load(ch.logoUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.bg_ch_logo)
                    .error(null)
                    .into(ivChLogo);
        } else {
            ivChLogo.setVisibility(View.GONE);
            tvChLogoFallback.setVisibility(View.VISIBLE);
            String initials = ch.name.isEmpty() ? "TV" :
                    ch.name.substring(0, Math.min(2, ch.name.length())).toUpperCase();
            tvChLogoFallback.setText(initials);
        }
    }

    private void showChInfo() {
        channelInfo.setVisibility(View.VISIBLE);
        channelInfo.setAlpha(1f);
        channelInfo.setTranslationX(0f);

        // Auto hide after 4s
        if (chInfoHideRunnable != null) handler.removeCallbacks(chInfoHideRunnable);
        chInfoHideRunnable = () -> hideChInfo();
        handler.postDelayed(chInfoHideRunnable, 4000);
    }

    private void hideChInfo() {
        channelInfo.animate()
                .alpha(0f)
                .translationX(80f)
                .setDuration(500)
                .withEndAction(() -> channelInfo.setVisibility(View.GONE))
                .start();
    }

    private void toggleChInfo() {
        if (channelInfo.getVisibility() == View.VISIBLE) {
            hideChInfo();
        } else {
            showChInfo();
        }
    }

    // ===== PANEL =====

    private void openPanel() {
        if (panelOpen) return;
        panelOpen = true;
        chListBackdrop.setVisibility(View.VISIBLE);
        chListBackdrop.animate().alpha(1f).setDuration(250).start();
        chListPanel.animate().translationX(0f).setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start();

        // Scroll to active
        if (currentChannelIdx >= 0) {
            rvChList.scrollToPosition(currentChannelIdx);
        }
    }

    private void hidePanel() {
        if (!panelOpen) return;
        panelOpen = false;
        float panelWidth = chListPanel.getWidth();
        chListPanel.animate().translationX(panelWidth).setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start();
        chListBackdrop.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> chListBackdrop.setVisibility(View.INVISIBLE)).start();
        etChSearch.setText("");
    }

    // ===== SWIPE FEEDBACK =====

    private void showSwipeFeedback(boolean up) {
        TextView fb = up ? swipeFbUp : swipeFbDown;
        fb.animate().alpha(1f).setDuration(150).withEndAction(() ->
                fb.animate().alpha(0f).setDuration(300).start()
        ).start();
    }

    // ===== SWIPE HINT =====

    private void showSwipeHint() {
        swipeHint.animate().alpha(1f).setDuration(500).start();
        if (swipeHintHideRunnable != null) handler.removeCallbacks(swipeHintHideRunnable);
        swipeHintHideRunnable = () ->
                swipeHint.animate().alpha(0f).setDuration(800).start();
        handler.postDelayed(swipeHintHideRunnable, 3000);
    }

    // ===== BLACK FLASH =====

    private void blackFlash() {
        blackFlash.setVisibility(View.VISIBLE);
        blackFlash.setAlpha(1f);
        blackFlash.animate().alpha(0f).setDuration(150)
                .withEndAction(() -> blackFlash.setVisibility(View.INVISIBLE))
                .start();
    }

    // ===== TV REMOTE D-PAD =====

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                if (!panelOpen) {
                    showSwipeFeedback(true);
                    playChannel(currentChannelIdx + 1, true);
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (!panelOpen) {
                    showSwipeFeedback(false);
                    playChannel(currentChannelIdx - 1, true);
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (panelOpen) {
                    hidePanel();
                } else {
                    openPanel();
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (panelOpen) hidePanel();
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!panelOpen) toggleChInfo();
                return true;

            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (panelOpen) {
                    hidePanel();
                } else {
                    finish();
                }
                return true;

            default:
                // Number keys for channel direct input (TV remote)
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
            if (targetCh >= 0 && targetCh < channels.size()) {
                playChannel(targetCh, true);
            }
        };
        handler.postDelayed(numClearRunnable, 1500);
    }

    // ===== LIFECYCLE =====

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) player.play();
        // Re-apply fullscreen
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onBackPressed() {
        if (panelOpen) {
            hidePanel();
        } else {
            super.onBackPressed();
        }
    }
}

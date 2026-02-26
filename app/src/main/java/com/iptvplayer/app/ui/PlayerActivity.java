package com.iptvplayer.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
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
import com.bumptech.glide.Glide;
import com.iptvplayer.app.R;
import com.iptvplayer.app.data.Channel;
import com.iptvplayer.app.data.Playlist;
import com.iptvplayer.app.utils.PrefsManager;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView tvChannelName, tvChannelGroup, tvChannelNumber, tvStatus;
    private ImageView ivChannelLogo;
    private View osdLayout;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideOsdRunnable;

    private List<Channel> channels;
    private int currentIdx = 0;
    private PrefsManager prefs;
    private boolean isRetrying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen & keep screen on
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_player);

        prefs = new PrefsManager(this);

        // Get channel data
        int playlistIdx = getIntent().getIntExtra("playlist_idx", 0);
        currentIdx = getIntent().getIntExtra("channel_idx", 0);

        List<Playlist> playlists = prefs.loadPlaylists();
        if (playlistIdx < playlists.size() && playlists.get(playlistIdx).channels != null) {
            channels = playlists.get(playlistIdx).channels;
        }

        initViews();
        initPlayer();

        if (channels != null && !channels.isEmpty()) {
            playChannel(currentIdx);
        }
    }

    private void initViews() {
        playerView = findViewById(R.id.player_view);
        osdLayout = findViewById(R.id.osd_layout);
        tvChannelName = findViewById(R.id.tv_channel_name);
        tvChannelGroup = findViewById(R.id.tv_channel_group);
        tvChannelNumber = findViewById(R.id.tv_channel_number);
        tvStatus = findViewById(R.id.tv_status);
        ivChannelLogo = findViewById(R.id.iv_channel_logo);

        playerView.setUseController(false); // Pakai OSD custom kita
        playerView.setOnClickListener(v -> toggleOsd());
    }

    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                switch (state) {
                    case Player.STATE_BUFFERING:
                        tvStatus.setText("Buffering...");
                        tvStatus.setVisibility(View.VISIBLE);
                        break;
                    case Player.STATE_READY:
                        tvStatus.setVisibility(View.GONE);
                        isRetrying = false;
                        break;
                    case Player.STATE_ENDED:
                        tvStatus.setText("Stream berakhir");
                        tvStatus.setVisibility(View.VISIBLE);
                        break;
                    case Player.STATE_IDLE:
                        break;
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (!isRetrying) {
                    isRetrying = true;
                    tvStatus.setText("Mencoba ulang...");
                    tvStatus.setVisibility(View.VISIBLE);
                    // Retry setelah 2 detik
                    handler.postDelayed(() -> {
                        if (!isFinishing()) playChannel(currentIdx);
                    }, 2000);
                } else {
                    isRetrying = false;
                    tvStatus.setText("Gagal: " + getReadableError(error));
                    tvStatus.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void playChannel(int idx) {
        if (channels == null || channels.isEmpty()) return;
        idx = ((idx % channels.size()) + channels.size()) % channels.size();
        currentIdx = idx;
        isRetrying = false;

        Channel ch = channels.get(idx);
        prefs.saveLastPosition(0, idx);

        // Update OSD
        tvChannelName.setText(ch.name);
        tvChannelGroup.setText(ch.group != null ? ch.group : "");
        tvChannelNumber.setText(String.valueOf(idx + 1));
        tvStatus.setText("Memuat...");
        tvStatus.setVisibility(View.VISIBLE);

        if (ch.logo != null && !ch.logo.isEmpty()) {
            Glide.with(this).load(ch.logo)
                .placeholder(R.drawable.ic_channel_placeholder)
                .error(R.drawable.ic_channel_placeholder)
                .into(ivChannelLogo);
        } else {
            ivChannelLogo.setImageResource(R.drawable.ic_channel_placeholder);
        }

        showOsd(3000);

        // Build ExoPlayer MediaSource dengan custom headers
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
            .setUserAgent(ch.userAgent != null && !ch.userAgent.isEmpty()
                ? ch.userAgent
                : "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 Chrome/96.0 Mobile Safari/537.36")
            .setDefaultRequestProperties(buildHeaders(ch))
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true);

        MediaItem mediaItem = MediaItem.fromUri(ch.url);
        MediaSource mediaSource;
        String url = ch.url.toLowerCase().split("\\?")[0];

        if (url.contains(".m3u8") || url.contains("m3u8") || url.contains("hls")
            || url.contains("chunklist") || url.contains("/live/")) {
            mediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem);
        } else if (url.contains(".mpd") || url.contains("dash")) {
            mediaSource = new DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem);
        } else {
            mediaSource = new DefaultMediaSourceFactory(dataSourceFactory)
                .createMediaSource(mediaItem);
        }

        player.stop();
        player.clearMediaItems();
        player.setMediaSource(mediaSource);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private java.util.HashMap<String, String> buildHeaders(Channel ch) {
        java.util.HashMap<String, String> headers = new java.util.HashMap<>();
        if (ch.referrer != null && !ch.referrer.isEmpty()) {
            headers.put("Referer", ch.referrer);
            headers.put("Origin", ch.referrer.replaceAll("/$", ""));
        }
        return headers;
    }

    private String getReadableError(PlaybackException error) {
        switch (error.errorCode) {
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED:
                return "Tidak ada koneksi";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT:
                return "Koneksi timeout";
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS:
                return "Stream tidak tersedia";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED:
                return "Format tidak didukung";
            default:
                return "Error " + error.errorCode;
        }
    }

    // ===== OSD =====

    private void showOsd(long durationMs) {
        osdLayout.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOsdRunnable != null ? hideOsdRunnable : () -> {});
        hideOsdRunnable = () -> osdLayout.setVisibility(View.GONE);
        handler.postDelayed(hideOsdRunnable, durationMs);
    }

    private void toggleOsd() {
        if (osdLayout.getVisibility() == View.VISIBLE) {
            osdLayout.setVisibility(View.GONE);
            handler.removeCallbacks(hideOsdRunnable);
        } else {
            showOsd(4000);
        }
    }

    // ===== KEY HANDLING =====

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_DPAD_UP:
                playChannel(currentIdx - 1);
                return true;

            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                playChannel(currentIdx + 1);
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                toggleOsd();
                return true;

            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;

            case KeyEvent.KEYCODE_INFO:
                showOsd(5000);
                return true;

            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    // ===== LIFECYCLE =====

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) player.setPlayWhenReady(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.setPlayWhenReady(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
    }
}

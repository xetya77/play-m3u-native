package com.iptvplayer.app.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.iptvplayer.app.R;
import com.iptvplayer.app.data.Channel;
import com.iptvplayer.app.data.Playlist;
import com.iptvplayer.app.utils.M3UParser;
import com.iptvplayer.app.utils.PrefsManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private RecyclerView rvPlaylists, rvChannels, rvGroups;
    private TextView tvPlaylistName, tvStatus, tvEmpty;
    private ProgressBar progressBar;
    private PrefsManager prefs;
    private List<Playlist> playlists = new ArrayList<>();
    private List<Channel> currentChannels = new ArrayList<>();
    private List<Channel> filteredChannels = new ArrayList<>();
    private List<String> groups = new ArrayList<>();
    private String currentGroup = "SEMUA";
    private int currentPlaylistIdx = 0;
    private ChannelAdapter channelAdapter;
    private GroupAdapter groupAdapter;
    private PlaylistAdapter playlistAdapter;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new PrefsManager(this);
        playlists = prefs.loadPlaylists();

        initViews();
        setupAdapters();

        if (playlists.isEmpty()) {
            showAddPlaylistDialog();
        } else {
            currentPlaylistIdx = prefs.getLastPlaylist();
            if (currentPlaylistIdx >= playlists.size()) currentPlaylistIdx = 0;
            loadPlaylist(currentPlaylistIdx);
        }
    }

    private void initViews() {
        rvPlaylists = findViewById(R.id.rv_playlists);
        rvChannels = findViewById(R.id.rv_channels);
        rvGroups = findViewById(R.id.rv_groups);
        tvPlaylistName = findViewById(R.id.tv_playlist_name);
        tvStatus = findViewById(R.id.tv_status);
        tvEmpty = findViewById(R.id.tv_empty);
        progressBar = findViewById(R.id.progress_bar);

        findViewById(R.id.btn_add_playlist).setOnClickListener(v -> showAddPlaylistDialog());
        findViewById(R.id.btn_refresh).setOnClickListener(v -> {
            if (!playlists.isEmpty()) loadPlaylist(currentPlaylistIdx);
        });
    }

    private void setupAdapters() {
        // Playlist adapter
        playlistAdapter = new PlaylistAdapter(playlists, idx -> {
            currentPlaylistIdx = idx;
            loadPlaylist(idx);
        }, idx -> confirmDeletePlaylist(idx));
        rvPlaylists.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvPlaylists.setAdapter(playlistAdapter);

        // Group adapter
        groupAdapter = new GroupAdapter(groups, group -> {
            currentGroup = group;
            filterByGroup(group);
        });
        rvGroups.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvGroups.setAdapter(groupAdapter);

        // Channel adapter
        channelAdapter = new ChannelAdapter(filteredChannels, ch -> openPlayer(ch));
        rvChannels.setLayoutManager(new LinearLayoutManager(this));
        rvChannels.setAdapter(channelAdapter);
    }

    private void loadPlaylist(int idx) {
        if (idx >= playlists.size()) return;
        Playlist pl = playlists.get(idx);
        tvPlaylistName.setText(pl.name);
        tvStatus.setText("Memuat playlist...");
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                Request req = new Request.Builder().url(pl.url).build();
                Response resp = httpClient.newCall(req).execute();
                String body = resp.body() != null ? resp.body().string() : "";
                List<Channel> channels = M3UParser.parse(body);
                pl.channels = channels;
                prefs.savePlaylists(playlists);

                handler.post(() -> {
                    currentChannels = channels;
                    buildGroups(channels);
                    currentGroup = "SEMUA";
                    filterByGroup("SEMUA");
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText(channels.size() + " channel");
                    if (channels.isEmpty()) tvEmpty.setVisibility(View.VISIBLE);
                });
            } catch (IOException e) {
                handler.post(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("Gagal memuat");
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    // Try cached channels
                    if (pl.channels != null && !pl.channels.isEmpty()) {
                        currentChannels = pl.channels;
                        buildGroups(currentChannels);
                        filterByGroup("SEMUA");
                        tvStatus.setText(pl.channels.size() + " channel (cache)");
                    }
                });
            }
        });
    }

    private void buildGroups(List<Channel> channels) {
        LinkedHashMap<String, Integer> groupMap = new LinkedHashMap<>();
        for (Channel ch : channels) {
            String g = ch.group != null && !ch.group.isEmpty() ? ch.group : "Umum";
            groupMap.put(g, groupMap.getOrDefault(g, 0) + 1);
        }
        groups.clear();
        groups.add("SEMUA");
        groups.addAll(groupMap.keySet());
        groupAdapter.notifyDataSetChanged();
    }

    private void filterByGroup(String group) {
        filteredChannels.clear();
        if ("SEMUA".equals(group)) {
            filteredChannels.addAll(currentChannels);
        } else {
            for (Channel ch : currentChannels) {
                String g = ch.group != null ? ch.group : "Umum";
                if (g.equals(group)) filteredChannels.add(ch);
            }
        }
        channelAdapter.notifyDataSetChanged();
    }

    private void openPlayer(Channel channel) {
        int idx = filteredChannels.indexOf(channel);
        prefs.saveLastPosition(currentPlaylistIdx, idx);
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("channel_idx", idx);
        intent.putExtra("playlist_idx", currentPlaylistIdx);
        startActivity(intent);
    }

    private void showAddPlaylistDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_playlist, null);
        EditText etName = view.findViewById(R.id.et_playlist_name);
        EditText etUrl = view.findViewById(R.id.et_playlist_url);

        new AlertDialog.Builder(this)
            .setTitle("Tambah Playlist")
            .setView(view)
            .setPositiveButton("Tambah", (d, w) -> {
                String name = etName.getText().toString().trim();
                String url = etUrl.getText().toString().trim();
                if (name.isEmpty()) name = "Playlist " + (playlists.size() + 1);
                if (!url.isEmpty()) {
                    playlists.add(new Playlist(name, url));
                    prefs.savePlaylists(playlists);
                    playlistAdapter.notifyDataSetChanged();
                    currentPlaylistIdx = playlists.size() - 1;
                    loadPlaylist(currentPlaylistIdx);
                }
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    private void confirmDeletePlaylist(int idx) {
        new AlertDialog.Builder(this)
            .setTitle("Hapus Playlist")
            .setMessage("Hapus \"" + playlists.get(idx).name + "\"?")
            .setPositiveButton("Hapus", (d, w) -> {
                playlists.remove(idx);
                prefs.savePlaylists(playlists);
                playlistAdapter.notifyDataSetChanged();
                if (playlists.isEmpty()) {
                    currentChannels.clear();
                    filteredChannels.clear();
                    channelAdapter.notifyDataSetChanged();
                    showAddPlaylistDialog();
                } else {
                    currentPlaylistIdx = 0;
                    loadPlaylist(0);
                }
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showAddPlaylistDialog();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ===== ADAPTERS =====

    static class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.VH> {
        private final List<Channel> channels;
        private final OnClickListener listener;
        interface OnClickListener { void onClick(Channel ch); }

        ChannelAdapter(List<Channel> channels, OnClickListener listener) {
            this.channels = channels;
            this.listener = listener;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Channel ch = channels.get(pos);
            h.tvName.setText(ch.name);
            h.tvGroup.setText(ch.group != null ? ch.group : "");
            if (ch.logo != null && !ch.logo.isEmpty()) {
                Glide.with(h.ivLogo.getContext()).load(ch.logo)
                    .placeholder(R.drawable.ic_channel_placeholder)
                    .error(R.drawable.ic_channel_placeholder)
                    .into(h.ivLogo);
            } else {
                h.ivLogo.setImageResource(R.drawable.ic_channel_placeholder);
            }
            h.itemView.setOnClickListener(v -> listener.onClick(ch));
            if (ch.hasDRM) {
                h.tvName.setAlpha(0.5f);
                h.tvGroup.setText((ch.group != null ? ch.group + " · " : "") + "DRM");
            } else {
                h.tvName.setAlpha(1f);
            }
        }

        @Override public int getItemCount() { return channels.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView ivLogo;
            TextView tvName, tvGroup;
            VH(View v) {
                super(v);
                ivLogo = v.findViewById(R.id.iv_logo);
                tvName = v.findViewById(R.id.tv_name);
                tvGroup = v.findViewById(R.id.tv_group);
            }
        }
    }

    static class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.VH> {
        private final List<String> groups;
        private final OnClickListener listener;
        private int selected = 0;
        interface OnClickListener { void onClick(String group); }

        GroupAdapter(List<String> groups, OnClickListener listener) {
            this.groups = groups;
            this.listener = listener;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            h.tv.setText(groups.get(pos));
            h.itemView.setSelected(pos == selected);
            h.itemView.setOnClickListener(v -> {
                selected = pos;
                notifyDataSetChanged();
                listener.onClick(groups.get(pos));
            });
        }

        @Override public int getItemCount() { return groups.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(View v) { super(v); tv = v.findViewById(R.id.tv_group_name); }
        }
    }

    static class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.VH> {
        private final List<Playlist> playlists;
        private final OnSelectListener selectListener;
        private final OnDeleteListener deleteListener;
        interface OnSelectListener { void onSelect(int idx); }
        interface OnDeleteListener { void onDelete(int idx); }

        PlaylistAdapter(List<Playlist> playlists, OnSelectListener s, OnDeleteListener d) {
            this.playlists = playlists;
            this.selectListener = s;
            this.deleteListener = d;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            h.tv.setText(playlists.get(pos).name);
            h.itemView.setOnClickListener(v -> selectListener.onSelect(pos));
            h.itemView.setOnLongClickListener(v -> { deleteListener.onDelete(pos); return true; });
        }

        @Override public int getItemCount() { return playlists.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(View v) { super(v); tv = v.findViewById(R.id.tv_playlist_name); }
        }
    }
}

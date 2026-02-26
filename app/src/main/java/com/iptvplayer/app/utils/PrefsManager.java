package com.iptvplayer.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.iptvplayer.app.data.Playlist;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PrefsManager {
    private static final String PREFS_NAME = "iptv_prefs";
    private static final String KEY_PLAYLISTS = "playlists";
    private static final String KEY_LAST_PLAYLIST = "last_playlist";
    private static final String KEY_LAST_CHANNEL = "last_channel";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public PrefsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void savePlaylists(List<Playlist> playlists) {
        prefs.edit().putString(KEY_PLAYLISTS, gson.toJson(playlists)).apply();
    }

    public List<Playlist> loadPlaylists() {
        String json = prefs.getString(KEY_PLAYLISTS, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<Playlist>>(){}.getType();
        List<Playlist> result = gson.fromJson(json, type);
        return result != null ? result : new ArrayList<>();
    }

    public void saveLastPosition(int playlistIdx, int channelIdx) {
        prefs.edit()
            .putInt(KEY_LAST_PLAYLIST, playlistIdx)
            .putInt(KEY_LAST_CHANNEL, channelIdx)
            .apply();
    }

    public int getLastPlaylist() { return prefs.getInt(KEY_LAST_PLAYLIST, 0); }
    public int getLastChannel() { return prefs.getInt(KEY_LAST_CHANNEL, 0); }
}

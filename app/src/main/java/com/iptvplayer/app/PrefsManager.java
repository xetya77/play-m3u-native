package com.iptvplayer.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PrefsManager {
    private static final String PREF_NAME = "playm3u_prefs";
    private static final String KEY_PLAYLISTS = "playlists";
    private static final String KEY_CURRENT_PLAYLIST = "current_playlist";
    private static final String KEY_CURRENT_CHANNEL = "current_channel";

    private final SharedPreferences prefs;
    private final Gson gson;

    public PrefsManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public List<Playlist> loadPlaylists() {
        String json = prefs.getString(KEY_PLAYLISTS, null);
        if (json == null) return new ArrayList<>();
        try {
            Type type = new TypeToken<List<Playlist>>() {}.getType();
            List<Playlist> list = gson.fromJson(json, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void savePlaylists(List<Playlist> playlists) {
        try {
            String json = gson.toJson(playlists);
            prefs.edit().putString(KEY_PLAYLISTS, json).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getCurrentPlaylistIndex() {
        return prefs.getInt(KEY_CURRENT_PLAYLIST, 0);
    }

    public void setCurrentPlaylistIndex(int idx) {
        prefs.edit().putInt(KEY_CURRENT_PLAYLIST, idx).apply();
    }

    public int getCurrentChannelIndex() {
        return prefs.getInt(KEY_CURRENT_CHANNEL, 0);
    }

    public void setCurrentChannelIndex(int idx) {
        prefs.edit().putInt(KEY_CURRENT_CHANNEL, idx).apply();
    }
}

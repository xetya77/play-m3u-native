package com.iptvplayer.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Singleton EPG Manager — menyimpan URL EPG, data programme,
 * dan menyediakan query "program sedang tayang sekarang" berdasarkan tvg-id atau nama channel.
 */
public class EpgManager {

    private static EpgManager instance;
    private static final String PREF_NAME   = "playm3u_prefs";
    private static final String KEY_EPG_URL = "epg_url";

    private final SharedPreferences prefs;

    // Map: channelId (lowercase) → list programme
    private final Map<String, List<EpgEntry>> epgData = new HashMap<>();
    private boolean loaded = false;

    private EpgManager(Context ctx) {
        prefs = ctx.getApplicationContext()
                   .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static EpgManager get(Context ctx) {
        if (instance == null) instance = new EpgManager(ctx);
        return instance;
    }

    // ===== URL =====
    public String getEpgUrl() {
        return prefs.getString(KEY_EPG_URL, "");
    }

    public void setEpgUrl(String url) {
        prefs.edit().putString(KEY_EPG_URL, url).apply();
    }

    // ===== LOAD DATA =====
    public void loadEpg(List<EpgEntry> entries) {
        epgData.clear();
        for (EpgEntry e : entries) {
            if (e.channelId == null) continue;
            String key = e.channelId.trim().toLowerCase(Locale.US);
            if (!epgData.containsKey(key)) epgData.put(key, new ArrayList<>());
            epgData.get(key).add(e);
        }
        loaded = true;
    }

    public boolean isLoaded() { return loaded && !epgData.isEmpty(); }

    public int getProgrammeCount() {
        int total = 0;
        for (List<EpgEntry> list : epgData.values()) total += list.size();
        return total;
    }

    // ===== QUERY =====
    /**
     * Cari program yang sedang tayang sekarang untuk channel ini.
     * Coba cocokkan tvg-id dulu, lalu fallback ke nama channel.
     * Return format: "Judul Program (HH:mm–HH:mm)" atau null.
     */
    public String getNowPlaying(Channel ch) {
        if (!loaded || ch == null) return null;
        long now = System.currentTimeMillis();

        // 1. Coba tvg-id
        if (ch.tvgId != null && !ch.tvgId.isEmpty()) {
            String key = ch.tvgId.trim().toLowerCase(Locale.US);
            EpgEntry e = findNow(key, now);
            if (e != null) return formatEntry(e);
        }

        // 2. Fallback: nama channel (exact & partial)
        if (ch.name != null && !ch.name.isEmpty()) {
            String nameLow = ch.name.trim().toLowerCase(Locale.US);
            // Exact match
            EpgEntry e = findNow(nameLow, now);
            if (e != null) return formatEntry(e);

            // Partial: cari key yang mengandung nama channel atau sebaliknya
            for (String key : epgData.keySet()) {
                if (key.contains(nameLow) || nameLow.contains(key)) {
                    e = findNow(key, now);
                    if (e != null) return formatEntry(e);
                }
            }
        }
        return null;
    }

    private EpgEntry findNow(String key, long nowMs) {
        List<EpgEntry> list = epgData.get(key);
        if (list == null) return null;
        for (EpgEntry e : list) {
            if (e.startMs <= nowMs && (e.stopMs == 0 || nowMs < e.stopMs))
                return e;
        }
        return null;
    }

    private String formatEntry(EpgEntry e) {
        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
        String start = sdf.format(new java.util.Date(e.startMs));
        String stop  = e.stopMs > 0 ? "–" + sdf.format(new java.util.Date(e.stopMs)) : "";
        return e.title + "  " + start + stop;
    }
}

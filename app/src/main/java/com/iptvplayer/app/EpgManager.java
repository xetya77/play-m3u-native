package com.iptvplayer.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Singleton EPG Manager.
 * Matching berlapis: tvg-id exact → tvg-id normalized → nama channel → fallback closest.
 */
public class EpgManager {

    private static final String TAG = "EpgManager";
    private static EpgManager instance;
    private static final String PREF_NAME   = "playm3u_prefs";
    private static final String KEY_EPG_URL = "epg_url";

    private final SharedPreferences prefs;
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

    public String getEpgUrl() { return prefs.getString(KEY_EPG_URL, ""); }
    public void setEpgUrl(String url) { prefs.edit().putString(KEY_EPG_URL, url).apply(); }

    public void loadEpg(List<EpgEntry> entries) {
        epgData.clear();
        for (EpgEntry e : entries) {
            if (e.channelId == null) continue;
            String key = e.channelId.trim().toLowerCase(Locale.US);
            if (!epgData.containsKey(key)) epgData.put(key, new ArrayList<>());
            epgData.get(key).add(e);
        }
        // Sort per channel berdasarkan startMs
        for (List<EpgEntry> list : epgData.values()) {
            Collections.sort(list, new Comparator<EpgEntry>() {
                @Override public int compare(EpgEntry a, EpgEntry b) {
                    return Long.compare(a.startMs, b.startMs);
                }
            });
        }
        loaded = true;
        Log.d(TAG, "EPG loaded: " + epgData.size() + " channels, " + entries.size() + " entries");
    }

    public boolean isLoaded() { return loaded && !epgData.isEmpty(); }

    public int getProgrammeCount() {
        int t = 0;
        for (List<EpgEntry> l : epgData.values()) t += l.size();
        return t;
    }

    /**
     * Cari program sekarang/terdekat untuk channel ini.
     * Urutan matching: tvg-id exact → tvg-id normalized → nama channel
     * Fallback: programme terdekat dalam ±4 jam.
     */
    public String getNowPlaying(Channel ch) {
        if (!loaded || ch == null) return null;
        long now = System.currentTimeMillis();

        List<String> candidates = buildCandidateKeys(ch);

        // Coba "now" dari semua kandidat
        for (String key : candidates) {
            EpgEntry e = findNow(key, now);
            if (e != null) {
                Log.d(TAG, "EPG hit NOW: ch=" + ch.name + " key=" + key + " title=" + e.title);
                return formatEntry(e, false);
            }
        }

        // Fallback: programme terdekat (±4 jam)
        EpgEntry closest = null;
        long closestDelta = Long.MAX_VALUE;
        for (String key : candidates) {
            EpgEntry e = findClosest(key, now, 4 * 3600_000L);
            if (e != null) {
                long delta = e.startMs > now
                        ? e.startMs - now
                        : (e.stopMs > 0 ? now - e.stopMs : now - e.startMs);
                if (delta < closestDelta) { closestDelta = delta; closest = e; }
            }
        }
        if (closest != null) {
            boolean future = closest.startMs > now;
            Log.d(TAG, "EPG hit CLOSEST: ch=" + ch.name + " future=" + future
                    + " title=" + closest.title);
            return formatEntry(closest, future);
        }

        // Tidak ada sama sekali
        if (!candidates.isEmpty()) {
            Log.d(TAG, "EPG no match: ch=" + ch.name + " tvgId=" + ch.tvgId
                    + " candidates=" + candidates);
        } else {
            Log.d(TAG, "EPG no candidates: ch=" + ch.name + " tvgId=" + ch.tvgId
                    + " sample keys=" + getSampleKeys(6));
        }
        return null;
    }

    private List<String> buildCandidateKeys(Channel ch) {
        List<String> out = new ArrayList<>();

        // 1. tvg-id exact
        if (ch.tvgId != null && !ch.tvgId.trim().isEmpty()) {
            String tvgLow  = ch.tvgId.trim().toLowerCase(Locale.US);
            String tvgNorm = tvgLow.replaceAll("[^a-z0-9]", "");
            addIfKey(out, tvgLow);

            // tvg-id normalized exact
            for (String k : epgData.keySet()) {
                if (out.contains(k)) continue;
                if (k.replaceAll("[^a-z0-9]", "").equals(tvgNorm)) addIfKey(out, k);
            }

            // tvg-id partial (minimum 3 karakter)
            if (tvgNorm.length() >= 3) {
                for (String k : epgData.keySet()) {
                    if (out.contains(k)) continue;
                    String kn = k.replaceAll("[^a-z0-9]", "");
                    if (kn.length() >= 3 && (kn.contains(tvgNorm) || tvgNorm.contains(kn)))
                        addIfKey(out, k);
                }
            }
        }

        // 2. Nama channel
        if (ch.name != null && !ch.name.trim().isEmpty()) {
            String nameLow  = ch.name.trim().toLowerCase(Locale.US);
            String nameNorm = nameLow.replaceAll("[^a-z0-9]", "");
            addIfKey(out, nameLow);

            // nama normalized exact
            for (String k : epgData.keySet()) {
                if (out.contains(k)) continue;
                if (k.replaceAll("[^a-z0-9]", "").equals(nameNorm)) addIfKey(out, k);
            }

            // nama partial (minimum 4 karakter)
            if (nameNorm.length() >= 4) {
                String best = null; int bestLen = 0;
                for (String k : epgData.keySet()) {
                    if (out.contains(k)) continue;
                    String kn = k.replaceAll("[^a-z0-9]", "");
                    if (kn.length() >= 4 && (kn.contains(nameNorm) || nameNorm.contains(kn))
                            && kn.length() > bestLen) {
                        best = k; bestLen = kn.length();
                    }
                }
                if (best != null) addIfKey(out, best);
            }
        }
        return out;
    }

    private void addIfKey(List<String> list, String key) {
        if (key != null && !key.isEmpty() && epgData.containsKey(key) && !list.contains(key))
            list.add(key);
    }

    private EpgEntry findNow(String key, long nowMs) {
        List<EpgEntry> list = epgData.get(key);
        if (list == null) return null;
        for (EpgEntry e : list) {
            if (e.startMs <= nowMs && (e.stopMs == 0 || nowMs < e.stopMs)) return e;
        }
        return null;
    }

    private EpgEntry findClosest(String key, long nowMs, long maxDelta) {
        List<EpgEntry> list = epgData.get(key);
        if (list == null || list.isEmpty()) return null;

        EpgEntry bestPrev = null, bestNext = null;
        long prevDelta = Long.MAX_VALUE, nextDelta = Long.MAX_VALUE;

        for (EpgEntry e : list) {
            if (e.startMs <= nowMs) {
                long d = nowMs - (e.stopMs > 0 ? e.stopMs : e.startMs);
                if (d >= 0 && d < prevDelta && d <= maxDelta) { prevDelta = d; bestPrev = e; }
            } else {
                long d = e.startMs - nowMs;
                if (d < nextDelta && d <= maxDelta) { nextDelta = d; bestNext = e; }
            }
        }
        // Tampilkan "next" jika dalam 30 menit, lainnya tampilkan "prev"
        if (bestNext != null && bestPrev != null)
            return nextDelta <= 30 * 60_000L ? bestNext : bestPrev;
        return bestNext != null ? bestNext : bestPrev;
    }

    private String formatEntry(EpgEntry e, boolean future) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String start  = sdf.format(new Date(e.startMs));
        String stop   = e.stopMs > 0 ? "\u2013" + sdf.format(new Date(e.stopMs)) : "";
        String prefix = future ? "\u25b6 " : "";   // ▶ untuk jadwal berikutnya
        return prefix + e.title + "  " + start + stop;
    }

    private String getSampleKeys(int n) {
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (String k : epgData.keySet()) { if (i++ >= n) break; sb.append(k).append(","); }
        return sb.append("]").toString();
    }
}

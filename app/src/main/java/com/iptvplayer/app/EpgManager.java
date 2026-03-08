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
 * Matching strategy (dari paling ketat ke paling longgar):
 *   1. tvg-id exact  →  programme now
 *   2. tvg-id normalized (alphanum only)  →  programme now
 *   3. channel name normalized  →  programme now
 *   4. Jika tidak ada "now", ambil programme TERDEKAT (±4 jam) dan beri label waktu
 */
public class EpgManager {

    private static final String TAG = "EpgManager";
    private static EpgManager instance;
    private static final String PREF_NAME   = "playm3u_prefs";
    private static final String KEY_EPG_URL = "epg_url";

    private final SharedPreferences prefs;

    /** Map: channelId lowercase-trimmed → sorted list of EpgEntry (sort by startMs) */
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
    public String getEpgUrl() { return prefs.getString(KEY_EPG_URL, ""); }
    public void setEpgUrl(String url) { prefs.edit().putString(KEY_EPG_URL, url).apply(); }

    // ===== LOAD DATA =====
    public void loadEpg(List<EpgEntry> entries) {
        epgData.clear();
        for (EpgEntry e : entries) {
            if (e.channelId == null) continue;
            String key = e.channelId.trim().toLowerCase(Locale.US);
            if (!epgData.containsKey(key)) epgData.put(key, new ArrayList<>());
            epgData.get(key).add(e);
        }
        // Sort setiap list berdasarkan startMs agar binary search bisa dipakai
        for (List<EpgEntry> list : epgData.values()) {
            Collections.sort(list, new Comparator<EpgEntry>() {
                @Override public int compare(EpgEntry a, EpgEntry b) {
                    return Long.compare(a.startMs, b.startMs);
                }
            });
        }
        loaded = true;
        Log.d(TAG, "EPG loaded: " + epgData.size() + " channels, " + entries.size() + " programmes");
    }

    public boolean isLoaded() { return loaded && !epgData.isEmpty(); }

    public int getProgrammeCount() {
        int total = 0;
        for (List<EpgEntry> list : epgData.values()) total += list.size();
        return total;
    }

    // ===== QUERY =====
    /**
     * Cari program yang sedang/akan/telah tayang untuk channel ini.
     * Strategi matching berlapis + fallback ke programme terdekat.
     */
    public String getNowPlaying(Channel ch) {
        if (!loaded || ch == null) return null;
        long now = System.currentTimeMillis();

        // Kumpulkan kandidat key untuk channel ini
        List<String> candidates = buildCandidateKeys(ch);

        // Coba cari programme "now" dari semua kandidat
        for (String key : candidates) {
            EpgEntry e = findNow(key, now);
            if (e != null) {
                Log.d(TAG, "EPG match NOW: ch=" + ch.name + " key=" + key + " title=" + e.title);
                return formatEntry(e, false);
            }
        }

        // Tidak ada programme "now" — cari yang terdekat (next/prev dalam ±4 jam)
        EpgEntry closest = null;
        long closestDelta = Long.MAX_VALUE;
        for (String key : candidates) {
            EpgEntry e = findClosest(key, now, 4 * 60 * 60 * 1000L);
            if (e != null) {
                long delta = Math.min(
                    Math.abs(now - e.startMs),
                    e.stopMs > 0 ? Math.abs(now - e.stopMs) : Long.MAX_VALUE
                );
                if (delta < closestDelta) {
                    closestDelta = delta;
                    closest = e;
                }
            }
        }
        if (closest != null) {
            boolean isFuture = closest.startMs > now;
            Log.d(TAG, "EPG match CLOSEST: ch=" + ch.name + " future=" + isFuture + " title=" + closest.title);
            return formatEntry(closest, isFuture);
        }

        // Debug: log untuk bantu diagnosa
        if (!candidates.isEmpty()) {
            Log.d(TAG, "EPG no match for ch=" + ch.name + " tvgId=" + ch.tvgId
                + " candidates=" + candidates + " epgKeys_sample=" + getSampleKeys(5));
        }
        return null;
    }

    /** Bangun daftar kandidat key untuk matching, dari paling spesifik ke paling umum */
    private List<String> buildCandidateKeys(Channel ch) {
        List<String> out = new ArrayList<>();

        // 1. tvg-id exact (lowercase)
        if (ch.tvgId != null && !ch.tvgId.trim().isEmpty()) {
            String tvgLow = ch.tvgId.trim().toLowerCase(Locale.US);
            addIfExists(out, tvgLow);

            // 2. tvg-id normalisasi (hanya alphanum)
            String tvgNorm = tvgLow.replaceAll("[^a-z0-9]", "");
            if (!tvgNorm.isEmpty()) {
                // Cari key EPG yang normalisasinya cocok
                for (String k : epgData.keySet()) {
                    String kNorm = k.replaceAll("[^a-z0-9]", "");
                    if (!out.contains(k) && !kNorm.isEmpty() && kNorm.equals(tvgNorm)) {
                        addIfExists(out, k);
                    }
                }
                // Cari partial match tvg-id
                for (String k : epgData.keySet()) {
                    if (out.contains(k)) continue;
                    String kNorm = k.replaceAll("[^a-z0-9]", "");
                    if (!kNorm.isEmpty() && (kNorm.contains(tvgNorm) || tvgNorm.contains(kNorm))
                            && kNorm.length() >= 3 && tvgNorm.length() >= 3) {
                        addIfExists(out, k);
                    }
                }
            }
        }

        // 3. Nama channel
        if (ch.name != null && !ch.name.trim().isEmpty()) {
            String nameLow  = ch.name.trim().toLowerCase(Locale.US);
            String nameNorm = nameLow.replaceAll("[^a-z0-9]", "");

            addIfExists(out, nameLow);

            if (!nameNorm.isEmpty()) {
                for (String k : epgData.keySet()) {
                    if (out.contains(k)) continue;
                    String kNorm = k.replaceAll("[^a-z0-9]", "");
                    if (!kNorm.isEmpty() && kNorm.equals(nameNorm)) {
                        addIfExists(out, k);
                    }
                }
                // Partial nama — hanya jika cukup panjang (hindari false positive)
                if (nameNorm.length() >= 4) {
                    String bestKey = null;
                    int bestLen = 0;
                    for (String k : epgData.keySet()) {
                        if (out.contains(k)) continue;
                        String kNorm = k.replaceAll("[^a-z0-9]", "");
                        if (kNorm.length() >= 4
                                && (kNorm.contains(nameNorm) || nameNorm.contains(kNorm))) {
                            if (kNorm.length() > bestLen) {
                                bestLen = kNorm.length();
                                bestKey = k;
                            }
                        }
                    }
                    if (bestKey != null) addIfExists(out, bestKey);
                }
            }
        }
        return out;
    }

    private void addIfExists(List<String> list, String key) {
        if (key != null && !key.isEmpty() && epgData.containsKey(key) && !list.contains(key)) {
            list.add(key);
        }
    }

    /** Cari programme yang sedang tayang sekarang (startMs <= now < stopMs) */
    private EpgEntry findNow(String key, long nowMs) {
        List<EpgEntry> list = epgData.get(key);
        if (list == null || list.isEmpty()) return null;
        for (EpgEntry e : list) {
            if (e.startMs <= nowMs && (e.stopMs == 0 || nowMs < e.stopMs)) return e;
        }
        return null;
    }

    /**
     * Cari programme terdekat dengan waktu sekarang dalam rentang maxDeltaMs.
     * Prioritas: programme yang baru saja lewat (prev) atau yang akan segera mulai (next).
     */
    private EpgEntry findClosest(String key, long nowMs, long maxDeltaMs) {
        List<EpgEntry> list = epgData.get(key);
        if (list == null || list.isEmpty()) return null;

        EpgEntry bestPrev = null; // programme terakhir sebelum now
        EpgEntry bestNext = null; // programme pertama setelah now
        long prevDelta = Long.MAX_VALUE;
        long nextDelta = Long.MAX_VALUE;

        for (EpgEntry e : list) {
            if (e.startMs <= nowMs) {
                // Programme sudah lewat atau sedang berjalan (stopMs=0)
                long delta = nowMs - (e.stopMs > 0 ? e.stopMs : e.startMs);
                if (delta >= 0 && delta < prevDelta && delta <= maxDeltaMs) {
                    prevDelta = delta;
                    bestPrev = e;
                }
            } else {
                // Programme belum mulai
                long delta = e.startMs - nowMs;
                if (delta < nextDelta && delta <= maxDeltaMs) {
                    nextDelta = delta;
                    bestNext = e;
                }
            }
        }

        // Prioritaskan "next" (yang akan datang) agar user tahu apa yang akan tayang
        // Tapi jika next masih > 30 menit lagi, tampilkan prev dulu
        if (bestNext != null && bestPrev != null) {
            return (nextDelta <= 30 * 60 * 1000L) ? bestNext : bestPrev;
        }
        return bestNext != null ? bestNext : bestPrev;
    }

    private String formatEntry(EpgEntry e, boolean isFuture) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String start = sdf.format(new Date(e.startMs));
        String stop  = e.stopMs > 0 ? "\u2013" + sdf.format(new Date(e.stopMs)) : "";
        String prefix = isFuture ? "\u25b6 " : ""; // ▶ untuk programme berikutnya
        return prefix + e.title + "  " + start + stop;
    }

    private String getSampleKeys(int n) {
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (String k : epgData.keySet()) {
            if (i++ >= n) break;
            sb.append(k).append(", ");
        }
        sb.append("]");
        return sb.toString();
    }
}

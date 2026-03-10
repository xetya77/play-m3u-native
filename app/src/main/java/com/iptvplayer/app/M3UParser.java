package com.iptvplayer.app;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser M3U yang menangani semua variasi urutan metadata di playlist:
 *
 *   Pola A (standar):  #EXTINF → [EXTVLCOPT/KODIPROP] → URL
 *   Pola B (umum):     #EXTINF → URL → [EXTVLCOPT/KODIPROP]
 *   Pola C (ada):      [EXTVLCOPT/KODIPROP] → #EXTINF → URL
 *
 * Semua pola di-assign ke channel yang tepat tanpa cross-contamination.
 */
public class M3UParser {

    public static List<Channel> parse(String content) {
        List<Channel> channels = new ArrayList<>();
        if (content == null || content.isEmpty()) return channels;

        String[] rawLines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        int total = rawLines.length;
        String[] lines = new String[total];
        for (int i = 0; i < total; i++) lines[i] = rawLines[i].trim();

        // ── Pass 1: temukan posisi semua #EXTINF ──
        List<Integer> extinfPos = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            if (lines[i].startsWith("#EXTINF")) extinfPos.add(i);
        }
        if (extinfPos.isEmpty()) return channels;
        int numEntries = extinfPos.size();

        // ── Pass 2: untuk setiap #EXTINF, temukan URL-nya ──
        int[] urlAt = new int[numEntries]; // -1 jika tidak ada
        for (int ei = 0; ei < numEntries; ei++) {
            int extinfI = extinfPos.get(ei);
            int nextExtinf = (ei + 1 < numEntries) ? extinfPos.get(ei + 1) : total;
            urlAt[ei] = -1;
            for (int k = extinfI + 1; k < nextExtinf; k++) {
                if (isUrl(lines[k])) { urlAt[ei] = k; break; }
            }
        }

        // ── Pass 3: bangun setiap channel ──
        for (int ei = 0; ei < numEntries; ei++) {
            if (urlAt[ei] < 0) continue; // tidak ada URL, skip

            int extinfI = extinfPos.get(ei);
            int urlI    = urlAt[ei];
            int nextExtinf = (ei + 1 < numEntries) ? extinfPos.get(ei + 1) : total;

            String extinfLine = lines[extinfI];

            // ── Nama channel: selalu dari display name setelah koma terakhir ──
            // Sengaja ABAIKAN tvg-name karena sering berisi ID teknis bukan nama tampilan.
            int comma = extinfLine.lastIndexOf(',');
            String name = (comma >= 0 && comma < extinfLine.length() - 1)
                    ? extinfLine.substring(comma + 1).trim() : "Channel";
            if (name.isEmpty()) name = "Channel";

            String logo  = extractAttr(extinfLine, "tvg-logo");
            String group = extractAttr(extinfLine, "group-title");

            // ── Kumpulkan metadata dari tiga rentang ──
            //
            //  Rentang A (Pola A): antara EXTINF dan URL
            //  Rentang B (Pola B): antara URL dan EXTINF berikutnya
            //  Rentang C (Pola C): antara URL_sebelumnya dan EXTINF ini
            //    → hanya diambil jika channel sebelumnya TIDAK punya Pola A
            //      (sehingga zona itu memang metadata milik channel ini, bukan Pola B sebelumnya)

            String ua = null, ref = null, drmType = null, drmKey = null;

            // Rentang A: extinf+1 .. url-1
            for (int k = extinfI + 1; k < urlI; k++) {
                MetaResult r = parseMeta(lines[k]);
                if (r != null) { ua = merge(ua, r.ua); ref = merge(ref, r.ref);
                    drmType = merge(drmType, r.drmType); drmKey = merge(drmKey, r.drmKey); }
            }

            // Rentang B: url+1 .. nextExtinf-1
            for (int k = urlI + 1; k < nextExtinf; k++) {
                MetaResult r = parseMeta(lines[k]);
                if (r != null) { ua = merge(ua, r.ua); ref = merge(ref, r.ref);
                    drmType = merge(drmType, r.drmType); drmKey = merge(drmKey, r.drmKey); }
            }

            // Rentang C (Pola C): hanya jika channel sebelumnya tidak punya Pola A
            if (ei > 0 && urlAt[ei - 1] >= 0) {
                int prevUrlI    = urlAt[ei - 1];
                int prevExtinfI = extinfPos.get(ei - 1);

                // Apakah channel sebelumnya punya Pola A? (ada meta antara extinf_prev dan url_prev)
                boolean prevHasPolaA = hasMeta(lines, prevExtinfI + 1, prevUrlI);

                if (!prevHasPolaA) {
                    // Zona antara prevUrl dan extinfI mungkin milik channel ini (Pola C)
                    // Ambil hanya jika ada metadata di zona itu
                    for (int k = prevUrlI + 1; k < extinfI; k++) {
                        MetaResult r = parseMeta(lines[k]);
                        if (r != null) { ua = merge(ua, r.ua); ref = merge(ref, r.ref);
                            drmType = merge(drmType, r.drmType); drmKey = merge(drmKey, r.drmKey); }
                    }
                }
            }

            // Inline attrs di EXTINF sebagai fallback
            if (ua == null || ua.isEmpty()) {
                String v = extractAttr(extinfLine, "tvg-user-agent");
                if (v != null && !v.isEmpty()) ua = v;
            }
            if (ref == null || ref.isEmpty()) {
                String v = extractAttr(extinfLine, "http-referrer");
                if (v != null && !v.isEmpty()) ref = v;
            }

            Channel ch = new Channel(name, lines[urlI], logo, group);
            ch.userAgent = (ua   != null && !ua.isEmpty())  ? ua  : null;
            ch.referrer  = (ref  != null && !ref.isEmpty()) ? ref : null;
            ch.drmType   = drmType;
            ch.drmKey    = drmKey;
            ch.isDrm     = (drmType != null);
            channels.add(ch);
        }
        return channels;
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private static boolean isUrl(String l) {
        return !l.startsWith("#") && !l.isEmpty()
                && (l.startsWith("http") || l.startsWith("rtmp") || l.startsWith("rtsp"));
    }

    private static boolean hasMeta(String[] lines, int from, int to) {
        for (int k = from; k < to; k++) {
            if (lines[k].startsWith("#EXTVLCOPT") || lines[k].startsWith("#KODIPROP")) return true;
        }
        return false;
    }

    private static String merge(String existing, String candidate) {
        return (existing == null && candidate != null) ? candidate : existing;
    }

    /** Membaca satu baris metadata. Mengembalikan null jika bukan baris metadata. */
    private static MetaResult parseMeta(String line) {
        if (line.startsWith("#EXTVLCOPT:http-user-agent=")) {
            String v = line.substring("#EXTVLCOPT:http-user-agent=".length());
            return v.isEmpty() ? null : new MetaResult(v, null, null, null);
        }
        if (line.startsWith("#EXTVLCOPT:http-referrer=")) {
            String v = line.substring("#EXTVLCOPT:http-referrer=".length());
            return v.isEmpty() ? null : new MetaResult(null, v, null, null);
        }
        if (line.startsWith("#KODIPROP:inputstream.adaptive.license_type=")) {
            String t = line.substring("#KODIPROP:inputstream.adaptive.license_type=".length());
            String dt = null;
            if (t.contains("clearkey") || t.equals("org.w3.clearkey")) dt = "clearkey";
            else if (t.contains("widevine")) dt = "widevine";
            return dt != null ? new MetaResult(null, null, dt, null) : null;
        }
        if (line.startsWith("#KODIPROP:inputstream.adaptive.license_key=")) {
            String v = line.substring("#KODIPROP:inputstream.adaptive.license_key=".length());
            return v.isEmpty() ? null : new MetaResult(null, null, null, v);
        }
        return null;
    }

    private static String extractAttr(String line, String attr) {
        Pattern p = Pattern.compile(attr + "=[\"']([^\"']*)[\"']");
        Matcher m = p.matcher(line);
        return m.find() ? m.group(1).trim() : null;
    }

    private static class MetaResult {
        final String ua, ref, drmType, drmKey;
        MetaResult(String ua, String ref, String drmType, String drmKey) {
            this.ua = ua; this.ref = ref; this.drmType = drmType; this.drmKey = drmKey;
        }
    }
}

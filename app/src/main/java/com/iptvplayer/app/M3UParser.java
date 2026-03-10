package com.iptvplayer.app;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser M3U yang menangani semua variasi urutan metadata:
 *
 *   Pola A (standar):  #EXTINF → [meta] → URL
 *   Pola B (umum):     #EXTINF → URL → [meta]
 *   Pola C (ada):      [meta] → #EXTINF → URL
 *
 * Untuk zona abu-abu (metadata di antara dua channel), digunakan heuristik
 * "blok terdekat ke EXTINF": setiap blok metadata berurutan diberikan ke
 * channel yang EXTINF-nya lebih dekat ke awal blok tersebut.
 *
 * Nama channel selalu diambil dari display name setelah koma terakhir di
 * #EXTINF — tvg-name sengaja diabaikan karena sering berisi ID teknis.
 */
public class M3UParser {

    public static List<Channel> parse(String content) {
        List<Channel> channels = new ArrayList<>();
        if (content == null || content.isEmpty()) return channels;

        String[] rawLines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        int total = rawLines.length;
        String[] lines = new String[total];
        for (int i = 0; i < total; i++) lines[i] = rawLines[i].trim();

        // ── Pass 1: posisi semua #EXTINF ──
        List<Integer> extinfPos = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            if (lines[i].startsWith("#EXTINF")) extinfPos.add(i);
        }
        if (extinfPos.isEmpty()) return channels;
        int numE = extinfPos.size();

        // ── Pass 2: URL terdekat setelah setiap #EXTINF ──
        int[] urlAt = new int[numE];
        for (int ei = 0; ei < numE; ei++) {
            int extinfI = extinfPos.get(ei);
            int nextExtinf = (ei + 1 < numE) ? extinfPos.get(ei + 1) : total;
            urlAt[ei] = -1;
            for (int k = extinfI + 1; k < nextExtinf; k++) {
                if (isUrl(lines[k])) { urlAt[ei] = k; break; }
            }
        }

        // ── Pass 3: bangun channel ──
        for (int ei = 0; ei < numE; ei++) {
            if (urlAt[ei] < 0) continue;

            int extinfI    = extinfPos.get(ei);
            int urlI       = urlAt[ei];
            int nextExtinf = (ei + 1 < numE) ? extinfPos.get(ei + 1) : total;

            String extinfLine = lines[extinfI];

            // Nama dari display name setelah koma terakhir (abaikan tvg-name)
            int comma = extinfLine.lastIndexOf(',');
            String name = (comma >= 0 && comma < extinfLine.length() - 1)
                    ? extinfLine.substring(comma + 1).trim() : "Channel";
            if (name.isEmpty()) name = "Channel";

            String logo  = extractAttr(extinfLine, "tvg-logo");
            String group = extractAttr(extinfLine, "group-title");

            String ua = null, ref = null, drmType = null, drmKey = null;

            // ── Pola A: metadata antara EXTINF dan URL → selalu milik channel ini ──
            for (int k = extinfI + 1; k < urlI; k++) {
                MetaResult r = parseMeta(lines[k]);
                if (r != null) { ua = merge(ua, r.ua); ref = merge(ref, r.ref);
                    drmType = merge(drmType, r.drmType); drmKey = merge(drmKey, r.drmKey); }
            }

            // ── Pola B: blok metadata antara URL dan EXTINF berikutnya ──
            // Setiap blok metadata berurutan diberikan ke channel ini HANYA JIKA
            // awal blok lebih dekat ke EXTINF ini daripada ke EXTINF berikutnya.
            {
                int k = urlI + 1;
                while (k < nextExtinf) {
                    if (isMeta(lines[k])) {
                        int blockStart = k;
                        List<String> block = new ArrayList<>();
                        while (k < nextExtinf && isMeta(lines[k])) {
                            block.add(lines[k]); k++;
                        }
                        int distToCurr = blockStart - extinfI;
                        int distToNext = nextExtinf - blockStart;
                        if (distToCurr <= distToNext) {
                            for (String bl : block) {
                                MetaResult r = parseMeta(bl);
                                if (r != null) { ua = merge(ua, r.ua); ref = merge(ref, r.ref);
                                    drmType = merge(drmType, r.drmType); drmKey = merge(drmKey, r.drmKey); }
                            }
                        }
                        // else: blok lebih dekat ke EXTINF berikutnya → biarkan (Pola C berikutnya)
                    } else {
                        k++;
                    }
                }
            }

            // ── Pola C: blok metadata dari zona channel sebelumnya ──
            // Diambil HANYA JIKA blok lebih dekat ke EXTINF ini daripada ke EXTINF sebelumnya.
            if (ei > 0 && urlAt[ei - 1] >= 0) {
                int prevUrlI   = urlAt[ei - 1];
                int prevExtinf = extinfPos.get(ei - 1);
                int k = prevUrlI + 1;
                while (k < extinfI) {
                    if (isMeta(lines[k])) {
                        int blockStart = k;
                        List<String> block = new ArrayList<>();
                        while (k < extinfI && isMeta(lines[k])) {
                            block.add(lines[k]); k++;
                        }
                        int distToPrev = blockStart - prevExtinf;
                        int distToCurr = extinfI - blockStart;
                        if (distToCurr < distToPrev) {
                            for (String bl : block) {
                                MetaResult r = parseMeta(bl);
                                if (r != null) { ua = merge(ua, r.ua); ref = merge(ref, r.ref);
                                    drmType = merge(drmType, r.drmType); drmKey = merge(drmKey, r.drmKey); }
                            }
                        }
                    } else {
                        k++;
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
            ch.userAgent = (ua      != null && !ua.isEmpty())      ? ua      : null;
            ch.referrer  = (ref     != null && !ref.isEmpty())     ? ref     : null;
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

    private static boolean isMeta(String l) {
        return l.startsWith("#EXTVLCOPT") || l.startsWith("#KODIPROP");
    }

    private static String merge(String existing, String candidate) {
        return (existing == null && candidate != null) ? candidate : existing;
    }

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

package com.iptvplayer.app;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser M3U dengan dukungan tiga pola urutan metadata:
 *
 *   Pola A (standar):  #EXTINF → [meta] → URL
 *   Pola B (umum di playlist ini): #EXTINF → URL → [meta]
 *   Pola C (ada di playlist ini):  [meta] → #EXTINF → URL
 *
 * Pola A dan C ditangani oleh state machine (seperti parser asli).
 * Pola B ditangani dengan scan tambahan setelah URL ditemukan,
 * menggunakan heuristik "blok terdekat": blok metadata setelah URL
 * hanya diambil jika awal blok lebih dekat ke URL saat ini daripada
 * ke #EXTINF channel berikutnya — sehingga tidak mencemari channel lain.
 */
public class M3UParser {

    public static List<Channel> parse(String content) {
        List<Channel> channels = new ArrayList<>();
        if (content == null || content.isEmpty()) return channels;

        String[] rawLines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        int total = rawLines.length;

        // State machine (sama dengan parser asli)
        String name = null, logo = null, group = null;
        String userAgent = null, referrer = null;
        String drmType = null, drmKey = null;

        for (int i = 0; i < total; i++) {
            String line = rawLines[i].trim();

            if (line.startsWith("#EXTINF")) {
                // Nama selalu dari display name setelah koma terakhir di #EXTINF.
                // tvg-name sengaja diabaikan — sering berisi ID teknis (misal "Indosiar.id")
                // bukan nama tampilan yang sebenarnya.
                int comma = line.lastIndexOf(',');
                String n = (comma >= 0 && comma < line.length() - 1)
                        ? line.substring(comma + 1).trim() : null;
                name = (n != null && !n.isEmpty()) ? n : "Channel";
                logo  = extractAttr(line, "tvg-logo");
                group = extractAttr(line, "group-title");

                String inlineUa = extractAttr(line, "tvg-user-agent");
                if (inlineUa != null && !inlineUa.isEmpty()) userAgent = inlineUa;
                String inlineRef = extractAttr(line, "http-referrer");
                if (inlineRef != null && !inlineRef.isEmpty()) referrer = inlineRef;

            } else if (line.startsWith("#EXTVLCOPT:http-user-agent=")) {
                userAgent = line.substring("#EXTVLCOPT:http-user-agent=".length()).trim();

            } else if (line.startsWith("#EXTVLCOPT:http-referrer=")) {
                referrer = line.substring("#EXTVLCOPT:http-referrer=".length()).trim();

            } else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_type=")) {
                String t = line.substring("#KODIPROP:inputstream.adaptive.license_type=".length()).trim();
                if (t.contains("clearkey") || t.equals("org.w3.clearkey")) drmType = "clearkey";
                else if (t.contains("widevine"))                            drmType = "widevine";

            } else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_key=")) {
                drmKey = line.substring("#KODIPROP:inputstream.adaptive.license_key=".length()).trim();

            } else if (!line.startsWith("#") && !line.isEmpty()
                    && (line.startsWith("http") || line.startsWith("rtmp") || line.startsWith("rtsp"))) {

                if (name == null || name.isEmpty()) name = "Channel";
                Channel ch = new Channel(name, line, logo, group);
                ch.userAgent = userAgent;
                ch.referrer  = referrer;
                ch.drmType   = drmType;
                ch.drmKey    = drmKey;
                ch.isDrm     = (drmType != null);
                channels.add(ch);

                // ── Pola B: scan metadata setelah URL ──────────────────────────
                // Cari posisi #EXTINF berikutnya agar tahu batas scan
                int urlPos       = i;
                int nextExtinfPos = total;
                for (int k = i + 1; k < total && k < i + 60; k++) {
                    if (rawLines[k].trim().startsWith("#EXTINF")) {
                        nextExtinfPos = k;
                        break;
                    }
                }

                // Scan blok-blok metadata di antara URL dan #EXTINF berikutnya
                int j = i + 1;
                while (j < nextExtinfPos) {
                    String nl = rawLines[j].trim();
                    if (isMeta(nl)) {
                        // Temukan seluruh blok metadata berurutan
                        int blockStart = j;
                        List<String> block = new ArrayList<>();
                        while (j < nextExtinfPos && isMeta(rawLines[j].trim())) {
                            block.add(rawLines[j].trim());
                            j++;
                        }
                        // Heuristik: ambil blok ini untuk channel saat ini HANYA jika
                        // awal blok lebih dekat ke URL saat ini daripada ke EXTINF berikutnya
                        int distFromUrl    = blockStart - urlPos;
                        int distToNextExtinf = nextExtinfPos - blockStart;
                        if (distFromUrl <= distToNextExtinf) {
                            for (String bl : block) {
                                applyMeta(bl, ch);
                            }
                            ch.isDrm = (ch.drmType != null);
                        }
                        // else: blok lebih dekat ke EXTINF berikutnya
                        //       → biarkan untuk ditangkap sebagai Pola C/A channel berikutnya
                    } else {
                        j++;
                    }
                }
                // ─────────────────────────────────────────────────────────────

                // Reset state
                name = null; logo = null; group = null;
                userAgent = null; referrer = null;
                drmType = null; drmKey = null;
            }
        }
        return channels;
    }

    /** Terapkan satu baris metadata ke channel, hanya jika field masih null. */
    private static void applyMeta(String line, Channel ch) {
        if (line.startsWith("#EXTVLCOPT:http-user-agent=")) {
            String v = line.substring("#EXTVLCOPT:http-user-agent=".length());
            if (!v.isEmpty() && ch.userAgent == null) ch.userAgent = v;
        } else if (line.startsWith("#EXTVLCOPT:http-referrer=")) {
            String v = line.substring("#EXTVLCOPT:http-referrer=".length());
            if (!v.isEmpty() && ch.referrer == null) ch.referrer = v;
        } else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_type=")) {
            if (ch.drmType == null) {
                String t = line.substring("#KODIPROP:inputstream.adaptive.license_type=".length());
                if (t.contains("clearkey") || t.equals("org.w3.clearkey")) ch.drmType = "clearkey";
                else if (t.contains("widevine"))                            ch.drmType = "widevine";
            }
        } else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_key=")) {
            String v = line.substring("#KODIPROP:inputstream.adaptive.license_key=".length());
            if (!v.isEmpty() && ch.drmKey == null) ch.drmKey = v;
        }
    }

    private static boolean isMeta(String line) {
        return line.startsWith("#EXTVLCOPT") || line.startsWith("#KODIPROP");
    }

    private static String extractAttr(String line, String attr) {
        Pattern p = Pattern.compile(attr + "=[\"']([^\"']*)[\"']");
        Matcher m = p.matcher(line);
        if (m.find()) return m.group(1).trim();
        return null;
    }
}

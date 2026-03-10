package com.iptvplayer.app;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3UParser {

    public static List<Channel> parse(String content) {
        List<Channel> channels = new ArrayList<>();
        if (content == null || content.isEmpty()) return channels;

        // Normalisasi line ending
        String[] lines = content.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        int total = lines.length;

        int i = 0;
        while (i < total) {
            String line = lines[i].trim();

            if (!line.startsWith("#EXTINF")) {
                i++;
                continue;
            }

            // ── Ambil display name dari bagian setelah koma terakhir ──
            // Selalu gunakan display name, ABAIKAN tvg-name
            // (tvg-name sering berisi ID teknis bukan nama tampilan)
            String name;
            int comma = line.lastIndexOf(',');
            if (comma >= 0 && comma < line.length() - 1) {
                name = line.substring(comma + 1).trim();
            } else {
                name = "Channel";
            }
            String logo  = extractAttr(line, "tvg-logo");
            String group = extractAttr(line, "group-title");

            // UA / referrer yang inline di EXTINF (jarang, tapi ada)
            String ua  = extractAttr(line, "tvg-user-agent");
            String ref = extractAttr(line, "http-referrer");

            String drmType = null, drmKey = null;
            String url = null;

            // ── Scan maju dari baris setelah EXTINF ──
            // Kumpulkan semua metadata sampai ketemu EXTINF berikutnya atau EOF
            // Ini menangani dua pola yang ada di playlist:
            //   Pola A (benar):  EXTINF → KODIPROP → EXTVLCOPT → URL
            //   Pola B (umum):   EXTINF → URL → KODIPROP → EXTVLCOPT
            int j = i + 1;
            // Pass 1: temukan URL
            int urlLine = -1;
            for (int k = j; k < total && k < j + 20; k++) {
                String kl = lines[k].trim();
                if (kl.startsWith("#EXTINF")) break; // EXTINF berikutnya = batas
                if (!kl.startsWith("#") && !kl.isEmpty()
                        && (kl.startsWith("http") || kl.startsWith("rtmp") || kl.startsWith("rtsp"))) {
                    url = kl;
                    urlLine = k;
                    break;
                }
            }

            if (url == null) { i++; continue; } // tidak ada URL, skip

            // Pass 2: kumpulkan semua metadata di antara EXTINF dan EXTINF berikutnya
            // Termasuk baris SETELAH URL (Pola B)
            int scanEnd = urlLine + 1;
            // Cari EXTINF berikutnya untuk tahu batas scan
            for (int k = urlLine + 1; k < total && k < urlLine + 15; k++) {
                if (lines[k].trim().startsWith("#EXTINF")) break;
                scanEnd = k + 1;
            }

            for (int k = j; k < scanEnd; k++) {
                String kl = lines[k].trim();
                if (kl.startsWith("#EXTVLCOPT:http-user-agent=")) {
                    String val = kl.substring("#EXTVLCOPT:http-user-agent=".length()).trim();
                    if (!val.isEmpty()) ua = val;
                } else if (kl.startsWith("#EXTVLCOPT:http-referrer=")) {
                    String val = kl.substring("#EXTVLCOPT:http-referrer=".length()).trim();
                    if (!val.isEmpty()) ref = val;
                } else if (kl.startsWith("#KODIPROP:inputstream.adaptive.license_type=")) {
                    String t = kl.substring("#KODIPROP:inputstream.adaptive.license_type=".length()).trim();
                    if (t.contains("clearkey") || t.equals("org.w3.clearkey")) drmType = "clearkey";
                    else if (t.contains("widevine")) drmType = "widevine";
                } else if (kl.startsWith("#KODIPROP:inputstream.adaptive.license_key=")) {
                    String val = kl.substring("#KODIPROP:inputstream.adaptive.license_key=".length()).trim();
                    if (!val.isEmpty()) drmKey = val;
                }
            }

            // Buat channel
            if (name.isEmpty()) name = "Channel";
            Channel ch = new Channel(name, url, logo, group);
            ch.userAgent = (ua   != null && !ua.isEmpty())  ? ua  : null;
            ch.referrer  = (ref  != null && !ref.isEmpty()) ? ref : null;
            ch.drmType   = drmType;
            ch.drmKey    = drmKey;
            ch.isDrm     = (drmType != null);
            channels.add(ch);

            // Lanjut dari baris setelah URL terakhir yang di-scan
            i = scanEnd;
        }
        return channels;
    }

    private static String extractAttr(String line, String attr) {
        Pattern p = Pattern.compile(attr + "=[\"']([^\"']*)[\"']");
        Matcher m = p.matcher(line);
        if (m.find()) return m.group(1).trim();
        return null;
    }
}

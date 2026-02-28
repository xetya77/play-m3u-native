package com.iptvplayer.app;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3UParser {

    public static List<Channel> parse(String content) {
        List<Channel> channels = new ArrayList<>();
        if (content == null || content.isEmpty()) return channels;

        String[] lines = content.split("\n");

        // Buffer metadata — di-reset hanya saat ketemu URL (channel selesai)
        String name = null, logo = null, group = null;
        String userAgent = null, referrer = null;
        String drmType = null, drmKey = null;

        for (String rawLine : lines) {
            String line = rawLine.trim();

            if (line.startsWith("#EXTINF")) {
                // Hanya reset field yang ada di EXTINF, biarkan
                // userAgent/referrer yang sudah di-set sebelumnya tetap
                String n = extractAttr(line, "tvg-name");
                if (n == null || n.isEmpty()) {
                    int comma = line.lastIndexOf(',');
                    if (comma >= 0 && comma < line.length() - 1)
                        n = line.substring(comma + 1).trim();
                }
                name  = n;
                logo  = extractAttr(line, "tvg-logo");
                group = extractAttr(line, "group-title");

                // User-agent kadang inline di EXTINF
                String inlineUa = extractAttr(line, "tvg-user-agent");
                if (inlineUa != null && !inlineUa.isEmpty()) userAgent = inlineUa;

                // Referrer kadang inline di EXTINF
                String inlineRef = extractAttr(line, "http-referrer");
                if (inlineRef != null && !inlineRef.isEmpty()) referrer = inlineRef;

            } else if (line.startsWith("#EXTVLCOPT:http-user-agent=")) {
                userAgent = line.substring("#EXTVLCOPT:http-user-agent=".length()).trim();

            } else if (line.startsWith("#EXTVLCOPT:http-referrer=")) {
                referrer = line.substring("#EXTVLCOPT:http-referrer=".length()).trim();

            } else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_type=")) {
                String type = line.substring("#KODIPROP:inputstream.adaptive.license_type=".length()).trim();
                if (type.equals("org.w3.clearkey"))      drmType = "clearkey";
                else if (type.contains("widevine"))       drmType = "widevine";

            } else if (line.startsWith("#KODIPROP:inputstream.adaptive.license_key=")) {
                drmKey = line.substring("#KODIPROP:inputstream.adaptive.license_key=".length()).trim();

            } else if (!line.startsWith("#") && !line.isEmpty()
                    && (line.startsWith("http") || line.startsWith("rtmp") || line.startsWith("rtsp"))) {
                // Ini adalah URL channel
                if (name == null || name.isEmpty()) name = "Channel";
                Channel ch = new Channel(name, line, logo, group);
                ch.userAgent = userAgent;
                ch.referrer  = referrer;
                ch.drmType   = drmType;
                ch.drmKey    = drmKey;
                ch.isDrm     = (drmType != null);
                channels.add(ch);

                // Reset semua setelah channel tersimpan
                name = null; logo = null; group = null;
                userAgent = null; referrer = null;
                drmType = null; drmKey = null;
            }
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

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
        String name = null, logo = null, group = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (line.startsWith("#EXTINF")) {
                name = extractAttr(line, "tvg-name");
                if (name == null || name.isEmpty()) {
                    // Ambil dari koma terakhir
                    int comma = line.lastIndexOf(',');
                    if (comma >= 0 && comma < line.length() - 1) {
                        name = line.substring(comma + 1).trim();
                    }
                }
                logo = extractAttr(line, "tvg-logo");
                group = extractAttr(line, "group-title");

            } else if (!line.startsWith("#") && !line.isEmpty() && name != null) {
                Channel ch = new Channel(name, line, logo, group);
                channels.add(ch);
                name = null;
                logo = null;
                group = null;
            }
        }
        return channels;
    }

    private static String extractAttr(String line, String attr) {
        // Pattern: attr="value" or attr='value'
        Pattern p = Pattern.compile(attr + "=[\"']([^\"']*)[\"']");
        Matcher m = p.matcher(line);
        if (m.find()) return m.group(1).trim();
        return null;
    }
}

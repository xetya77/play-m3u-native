package com.iptvplayer.app.utils;

import com.iptvplayer.app.data.Channel;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3UParser {

    public static List<Channel> parse(String content) {
        List<Channel> channels = new ArrayList<>();
        String[] lines = content.split("\n");

        Channel current = null;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (line.startsWith("#EXTINF")) {
                current = new Channel();
                current.referrer = "";
                current.userAgent = "";
                current.hasDRM = false;

                // Parse logo
                Matcher logoM = Pattern.compile("tvg-logo=\"([^\"]*)\"").matcher(line);
                if (logoM.find()) current.logo = logoM.group(1);

                // Parse name (after last comma)
                int lastComma = line.lastIndexOf(',');
                if (lastComma >= 0 && lastComma < line.length() - 1) {
                    current.name = line.substring(lastComma + 1).trim();
                } else {
                    current.name = "Channel";
                }

                // Parse group
                Matcher groupM = Pattern.compile("group-title=\"([^\"]*)\"").matcher(line);
                if (groupM.find()) current.group = groupM.group(1);

                // Parse tvg-id
                Matcher idM = Pattern.compile("tvg-id=\"([^\"]*)\"").matcher(line);
                if (idM.find()) current.tvgId = idM.group(1);

                // Parse inline referrer
                Matcher refInline = Pattern.compile("http-referrer=\"([^\"]*)\"").matcher(line);
                if (refInline.find()) current.referrer = refInline.group(1);

            } else if (current != null && line.startsWith("#EXTVLCOPT")) {
                // Parse referrer
                Matcher refM = Pattern.compile("http-referrer=(.+)").matcher(line);
                if (refM.find()) current.referrer = refM.group(1).trim().replaceAll("[\"']", "");

                // Parse user-agent
                Matcher uaM = Pattern.compile("http-user-agent=(.+)").matcher(line);
                if (uaM.find()) current.userAgent = uaM.group(1).trim().replaceAll("[\"']", "");

            } else if (current != null && line.startsWith("#KODIPROP")) {
                if (line.contains("widevine") || line.contains("clearkey")) {
                    current.hasDRM = true;
                }

            } else if (current != null && !line.startsWith("#") && !line.isEmpty()) {
                current.url = line;
                if (current.name == null) current.name = "Channel";
                if (current.logo == null) current.logo = "";
                if (current.group == null) current.group = "Umum";
                channels.add(current);
                current = null;
            }
        }

        return channels;
    }
}

package com.iptvplayer.app;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Parser XMLTV sederhana — hanya ambil programme channel/start/stop/title.
 * Format tanggal XMLTV: "20240315120000 +0700" atau "20240315120000 +0000"
 */
public class EpgParser {

    // Dibuat per-call agar thread-safe (SimpleDateFormat tidak thread-safe)

    public static List<EpgEntry> parse(String xmlContent) {
        List<EpgEntry> entries = new ArrayList<>();
        if (xmlContent == null || xmlContent.trim().isEmpty()) return entries;

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(xmlContent));

            String channelId = null;
            long startMs = 0, stopMs = 0;
            String title = null;
            boolean inTitle = false;

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if ("programme".equals(tag)) {
                        channelId = parser.getAttributeValue(null, "channel");
                        startMs = parseTime(parser.getAttributeValue(null, "start"));
                        stopMs  = parseTime(parser.getAttributeValue(null, "stop"));
                        title = null;
                        inTitle = false;
                    } else if ("title".equals(tag)) {
                        inTitle = true;
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    if (inTitle && title == null) {
                        title = parser.getText();
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    String tag = parser.getName();
                    if ("title".equals(tag)) {
                        inTitle = false;
                    } else if ("programme".equals(tag)) {
                        if (channelId != null && title != null && startMs > 0) {
                            entries.add(new EpgEntry(channelId, title, startMs, stopMs));
                        }
                        channelId = null; title = null;
                    }
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            // Parsing gagal sebagian — kembalikan apa yang sudah berhasil
        }
        return entries;
    }

    /** Parse waktu XMLTV menjadi epoch ms. Thread-safe, support berbagai format. */
    private static long parseTime(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            s = s.trim();
            if (s.length() < 14) return 0;

            // Ambil 14 digit pertama (yyyyMMddHHmmss)
            String digits = s.substring(0, 14);

            // Cari timezone: karakter setelah digit bisa berupa spasi, +, atau -
            // Format: "20240315120000 +0700", "20240315120000+0700", "20240315120000 -0500"
            String tz = "";
            if (s.length() > 14) {
                tz = s.substring(14).trim();
            }

            if (!tz.isEmpty()) {
                // Pastikan format tz benar: harus diawali + atau -
                if (!tz.startsWith("+") && !tz.startsWith("-")) tz = "";
            }

            if (!tz.isEmpty()) {
                // Parse dengan timezone — buat SDF baru (thread-safe)
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
                sdf.setLenient(false);
                Date d = sdf.parse(digits + " " + tz);
                return d != null ? d.getTime() : 0;
            } else {
                // Tanpa timezone — asumsikan UTC
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                sdf.setLenient(false);
                Date d = sdf.parse(digits);
                return d != null ? d.getTime() : 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }
}

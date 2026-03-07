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

    private static final SimpleDateFormat SDF_TZ =
            new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
    private static final SimpleDateFormat SDF_NOTZ =
            new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);

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

    /** Parse waktu XMLTV menjadi epoch ms. Support format dengan/tanpa timezone. */
    private static long parseTime(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            // Normalisasi: hapus semua karakter selain digit dan + - spasi
            s = s.trim();
            // Format: "20240315120000 +0700" (dengan spasi) atau "20240315120000+0700"
            if (s.length() >= 19 && (s.charAt(14) == ' ' || s.contains("+")||s.contains("-"))) {
                // Pastikan ada spasi sebelum timezone
                String num = s.substring(0, 14);
                String tz  = s.substring(14).trim();
                if (!tz.isEmpty()) {
                    Date d = SDF_TZ.parse(num + " " + tz);
                    return d != null ? d.getTime() : 0;
                }
            }
            // Tanpa timezone
            Date d = SDF_NOTZ.parse(s.substring(0, Math.min(14, s.length())));
            return d != null ? d.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}

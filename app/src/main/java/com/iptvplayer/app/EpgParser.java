package com.iptvplayer.app;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Parser XMLTV streaming — membaca langsung dari InputStream tanpa buffer String.
 * Aman untuk file besar (100MB+). Thread-safe.
 */
public class EpgParser {

    /** Parse dari InputStream (mendukung file besar, GZIP sudah di-unwrap di caller). */
    public static List<EpgEntry> parse(InputStream inputStream) {
        List<EpgEntry> entries = new ArrayList<>();
        if (inputStream == null) return entries;
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new InputStreamReader(inputStream, "UTF-8"));
            parseInternal(parser, entries);
        } catch (Exception e) {
            android.util.Log.w("EpgParser", "parse(InputStream) partial: " + e.getMessage());
        }
        return entries;
    }

    /** Parse dari String (untuk EPG URL yang sudah di-download ke String). */
    public static List<EpgEntry> parse(String xmlContent) {
        List<EpgEntry> entries = new ArrayList<>();
        if (xmlContent == null || xmlContent.trim().isEmpty()) return entries;
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new java.io.StringReader(xmlContent));
            parseInternal(parser, entries);
        } catch (Exception e) {
            android.util.Log.w("EpgParser", "parse(String) partial: " + e.getMessage());
        }
        return entries;
    }

    private static void parseInternal(XmlPullParser parser, List<EpgEntry> entries)
            throws Exception {
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
                    startMs   = parseTime(parser.getAttributeValue(null, "start"));
                    stopMs    = parseTime(parser.getAttributeValue(null, "stop"));
                    title     = null;
                    inTitle   = false;
                } else if ("title".equals(tag)) {
                    inTitle = true;
                }
            } else if (eventType == XmlPullParser.TEXT) {
                if (inTitle && title == null) title = parser.getText();
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
    }

    static long parseTime(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            s = s.trim();
            if (s.length() < 14) return 0;
            String digits = s.substring(0, 14);
            String tz = s.length() > 14 ? s.substring(14).trim() : "";
            if (!tz.isEmpty() && !tz.startsWith("+") && !tz.startsWith("-")) tz = "";

            if (!tz.isEmpty()) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
                sdf.setLenient(false);
                Date d = sdf.parse(digits + " " + tz);
                return d != null ? d.getTime() : 0;
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                sdf.setLenient(false);
                Date d = sdf.parse(digits);
                return d != null ? d.getTime() : 0;
            }
        } catch (Exception e) { return 0; }
    }
}

package com.iptvplayer.app;

public class EpgEntry {
    public String channelId;   // tvg-id dari XMLTV
    public String title;       // judul program
    public long startMs;       // epoch milliseconds
    public long stopMs;        // epoch milliseconds

    public EpgEntry(String channelId, String title, long startMs, long stopMs) {
        this.channelId = channelId;
        this.title = title;
        this.startMs = startMs;
        this.stopMs = stopMs;
    }
}

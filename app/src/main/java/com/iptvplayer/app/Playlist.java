package com.iptvplayer.app;

import java.util.ArrayList;
import java.util.List;

public class Playlist {
    public String name;
    public String url;
    public String type; // "url" or "file"
    public boolean downloadOnStart;
    public List<Channel> channels;
    public long lastUpdated;

    public Playlist(String name, String url, String type, boolean downloadOnStart) {
        this.name = name;
        this.url = url;
        this.type = type;
        this.downloadOnStart = downloadOnStart;
        this.channels = new ArrayList<>();
        this.lastUpdated = System.currentTimeMillis();
    }

    public int getChannelCount() {
        return channels != null ? channels.size() : 0;
    }
}

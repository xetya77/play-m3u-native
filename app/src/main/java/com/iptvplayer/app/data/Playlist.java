package com.iptvplayer.app.data;

import java.util.List;

public class Playlist {
    public String name;
    public String url;
    public List<Channel> channels;

    public Playlist(String name, String url) {
        this.name = name;
        this.url = url;
    }
}

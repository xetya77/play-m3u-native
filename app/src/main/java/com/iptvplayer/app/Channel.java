package com.iptvplayer.app;

public class Channel {
    public String name;
    public String url;
    public String logoUrl;
    public String group;
    public boolean isDrm;

    public Channel(String name, String url, String logoUrl, String group) {
        this.name = name;
        this.url = url;
        this.logoUrl = logoUrl;
        this.group = group != null ? group : "";
        this.isDrm = false;
    }
}

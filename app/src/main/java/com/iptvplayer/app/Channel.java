package com.iptvplayer.app;

public class Channel {
    public String name;
    public String url;
    public String logoUrl;
    public String group;
    public String tvgId;     // tvg-id dari M3U, digunakan untuk matching EPG
    public boolean isDrm;
    public String userAgent;
    public String referrer;
    public String drmType;   // "clearkey" atau "widevine"
    public String drmKey;    // untuk clearkey: "keyid:key"

    public Channel(String name, String url, String logoUrl, String group) {
        this.name = name;
        this.url = url;
        this.logoUrl = logoUrl;
        this.group = group != null ? group : "";
        this.isDrm = false;
    }
}

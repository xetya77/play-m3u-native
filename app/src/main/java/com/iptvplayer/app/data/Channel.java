package com.iptvplayer.app.data;

public class Channel {
    public String name;
    public String url;
    public String logo;
    public String group;
    public String tvgId;
    public String referrer;
    public String userAgent;
    public boolean hasDRM;

    public Channel() {}

    public Channel(String name, String url, String logo, String group) {
        this.name = name;
        this.url = url;
        this.logo = logo;
        this.group = group;
        this.referrer = "";
        this.userAgent = "";
        this.hasDRM = false;
    }
}

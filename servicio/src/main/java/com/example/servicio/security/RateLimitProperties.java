package com.example.servicio.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private int getPerMinute = 120;
    private int postPerMinute = 30;
    private int putPerMinute = 30;
    private int deletePerMinute = 20;

    public int getGetPerMinute() { return getPerMinute; }
    public void setGetPerMinute(int getPerMinute) { this.getPerMinute = getPerMinute; }

    public int getPostPerMinute() { return postPerMinute; }
    public void setPostPerMinute(int postPerMinute) { this.postPerMinute = postPerMinute; }

    public int getPutPerMinute() { return putPerMinute; }
    public void setPutPerMinute(int putPerMinute) { this.putPerMinute = putPerMinute; }

    public int getDeletePerMinute() { return deletePerMinute; }
    public void setDeletePerMinute(int deletePerMinute) { this.deletePerMinute = deletePerMinute; }
}

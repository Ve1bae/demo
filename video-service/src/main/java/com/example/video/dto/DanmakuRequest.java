package com.example.video.dto;

public class DanmakuRequest {
    private String content;
    private Integer timeSeconds;
    private String color;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getTimeSeconds() { return timeSeconds; }
    public void setTimeSeconds(Integer timeSeconds) { this.timeSeconds = timeSeconds; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}

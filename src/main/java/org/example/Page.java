package org.example;

public class Page {
    private String url;
    private String content;

    public Page(String url, String content) {
        this.url = url;
        this.content = content;
    }

    public String getContent() {
        return content;
    }
}

/*
    MIT License

    Copyright (c) 2017 mr-notorious

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.
 */

package com.notorious.smoothproxy;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;

class SmoothProxy extends NanoHTTPD {
    private final String host;
    private final int port;
    private final Pipe pipe;
    private String username;
    private String password;
    private String service;
    private String server;
    private String auth;
    private long time;

    SmoothProxy(String host, int port, Pipe pipe) {
        super(host, port);
        this.host = host;
        this.port = port;
        this.pipe = pipe;
    }

    @Override
    public Response serve(IHTTPSession session) {
        Response res = super.serve(session);
        String uri = session.getUri();
        if (uri.equals("/epg.xml")) {
            pipe.setNotification("Now serving: EPG");
            res = newFixedLengthResponse(Response.Status.REDIRECT, "application/xml", null);
            res.addHeader("Location", "http://sstv.fog.pt/feed.xml");
        } else if (uri.startsWith("/playlist.m3u8")) {
            List<String> ch = session.getParameters().get("ch");
            if (ch == null) {
                pipe.setNotification("Now serving: Playlist");
                res = newFixedLengthResponse(Response.Status.OK, "application/x-mpegURL", getPlay());
                res.addHeader("Content-Disposition", "attachment; filename=\"playlist.m3u8\"");
            } else {
                pipe.setNotification("Now serving: Channel " + ch.get(0));
                res = newFixedLengthResponse(Response.Status.REDIRECT, "application/x-mpegURL", null);
                res.addHeader("Location", String.format("http://%s.smoothstreams.tv:9100/%s/ch%sq1.stream/playlist.m3u8?wmsAuthSign=%s==",
                        server, service, ch.get(0), getAuth()));
            }
        }
        return res;
    }

    private String getAuth() {
        long now = System.currentTimeMillis();
        if (auth == null || time < now) {
            auth = getJson(String.format("http://auth.smoothstreams.tv/hash_api.php?username=%s&password=%s&site=%s",
                    username, password, service)).getAsJsonPrimitive("hash").getAsString();
            time = now + 14100000;
        }
        return auth;
    }

    private String getPlay() {
        JsonObject chan = getJson("http://sstv.fog.pt/utc/chanlist.json");
        JsonObject feed = getJson("http://cdn.smoothstreams.tv/schedule/feed.json");

        StringBuilder play = new StringBuilder("#EXTM3U\n");
        for (String id : chan.keySet()) {
            String ch = chan.getAsJsonPrimitive(id).getAsString();

            JsonObject object = feed.getAsJsonObject(ch);

            String name = object.getAsJsonPrimitive("name").getAsString().substring(5).trim();
            if (name.isEmpty()) name = "Empty";

            String img = object.getAsJsonPrimitive("img").getAsString();
            if (!img.endsWith("png")) img = "http://mystreams.tv/wp-content/themes/mystreams/img/video-player.png";

            play.append(String.format("#EXTINF:-1 tvg-id=\"%s\" tvg-name=\"%s\" tvg-logo=\"%s\" channel-id=\"%s\",%s\nhttp://%s:%s/playlist.m3u8?ch=%s\n",
                    id, ch, img, ch, name, host, port, ch.length() == 1 ? "0" + ch : ch));
        }
        return play.toString();
    }

    private JsonObject getJson(String url) {
        try {
            return new Gson().fromJson(
                    new OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .build()
                            .newCall(new Request.Builder()
                                    .url(HttpUrl.parse(url))
                                    .build())
                            .execute()
                            .body()
                            .string(), JsonObject.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    void setUsername(String username) {
        this.username = username;
    }

    void setPassword(String password) {
        this.password = password;
    }

    void setService(String service) {
        this.service = service;
    }

    void setServer(String server) {
        this.server = server;
    }
}

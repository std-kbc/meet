package com.std.chat.rtc.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

public class IceServerResponse {

    private List<IceServer> iceServers;

    public IceServerResponse(List<IceServer> iceServers) {
        this.iceServers = iceServers;
    }

    public List<IceServer> getIceServers() {
        return iceServers;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IceServer {
        private String urls;
        private String username;
        private String credential;

        public IceServer(String urls, String username, String credential) {
            this.urls = urls;
            this.username = username;
            this.credential = credential;
        }

        public String getUrls() {
            return urls;
        }

        public String getUsername() {
            return username;
        }

        public String getCredential() {
            return credential;
        }
    }
}

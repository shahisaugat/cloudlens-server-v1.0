package com.cloudlenshq.server.meeting.controller;

import io.agora.media.RtcTokenBuilder2;
import io.agora.media.RtcTokenBuilder2.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/agora")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class AgoraTokenController {

    @Value("${AGORA_APP_ID:}")
    private String appId;

    @Value("${AGORA_APP_CERTIFICATE:}")
    private String appCertificate;

    /**
     * Generate a temporary RTC token for a given channel.
     * The token is valid for 1 hour (3600 seconds).
     */
    @GetMapping("/token")
    public ResponseEntity<Map<String, String>> getToken(
            @RequestParam String channelName,
            @RequestParam(defaultValue = "0") int uid) {

        if (appId.isEmpty() || appCertificate.isEmpty()) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Agora credentials not configured on server")
            );
        }

        int tokenExpirationSeconds = 3600; // 1 hour
        int privilegeExpirationSeconds = 3600;

        RtcTokenBuilder2 tokenBuilder = new RtcTokenBuilder2();
        String token = tokenBuilder.buildTokenWithUid(
                appId,
                appCertificate,
                channelName,
                uid,
                Role.ROLE_PUBLISHER,
                tokenExpirationSeconds,
                privilegeExpirationSeconds
        );

        return ResponseEntity.ok(Map.of("token", token));
    }
}

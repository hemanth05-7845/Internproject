package com.mafia.client;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class EventServiceClient {

    private static final Logger log = LoggerFactory.getLogger(EventServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public EventServiceClient(RestTemplate restTemplate,
                              @Value("${gin.event.base-url:http://localhost:8081/api}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public Map<String, Object> getTimer(String roomId) {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                baseUrl + "/timer/" + roomId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception e) {
            log.warn("getTimer failed for room {}: {}", roomId, e.getMessage());
            return Map.of();
        }
    }

    public int getTimerRemainingSeconds(String roomId) {
        Map<String, Object> snap = getTimer(roomId);
        Object val = snap.get("remainingSeconds");
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    public void startPhaseTimer(String roomId, String phase, int durationSec) {
        try {
            restTemplate.postForEntity(
                baseUrl + "/phase/" + roomId + "/start",
                Map.of("phase", phase, "durationSeconds", durationSec),
                Map.class
            );
        } catch (Exception e) {
            log.warn("startPhaseTimer failed for room {}: {}", roomId, e.getMessage());
        }
    }

    public void cancelPhaseTimer(String roomId) {
        try {
            restTemplate.postForEntity(
                baseUrl + "/phase/" + roomId + "/cancel",
                Map.of(),
                Map.class
            );
        } catch (Exception e) {
            log.warn("cancelPhaseTimer failed for room {}: {}", roomId, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getEvents(String roomId) {
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                baseUrl + "/events/" + roomId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            List<Map<String, Object>> body = response.getBody();
            return body != null ? body : List.of();
        } catch (Exception e) {
            log.warn("getEvents failed for room {}: {}", roomId, e.getMessage());
            return List.of();
        }
    }

    public void pushEvent(String roomId, String type, String description) {
        try {
            restTemplate.postForEntity(
                baseUrl + "/events/" + roomId,
                Map.of("type", type, "description", description),
                Map.class
            );
        } catch (Exception e) {
            log.warn("pushEvent failed for room {}: {}", roomId, e.getMessage());
        }
    }
}
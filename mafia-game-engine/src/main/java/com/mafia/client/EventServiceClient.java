package com.mafia.client;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class EventServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String baseUrl = System.getenv("GIN_EVENT_BASE_URL") == null
            ? "http://localhost:8081/api"
            : System.getenv("GIN_EVENT_BASE_URL");

    public Map<String, Object> getTimer(String roomId) {
        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) restTemplate
                    .getForEntity(baseUrl + "/timer/" + roomId, Map.class);
            return response.getBody() != null ? response.getBody() : Map.of();
        } catch (Exception e) {
            return Map.of("remaining", 0);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getEvents(String roomId) {
        try {
            ResponseEntity<?> response = restTemplate.getForEntity(
                    baseUrl + "/events/" + roomId,
                    List.class);
            List<?> body = (List<?>) response.getBody();
            return body != null ? (List<Map<String, Object>>) body : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}

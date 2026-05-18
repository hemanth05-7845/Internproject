package com.mafia.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_returnsOk() throws Exception {
        mockMvc.perform(get("/internal/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("mafia-game-engine"));
    }

    @Test
    void snapshot_returnsDemoInfo() throws Exception {
        mockMvc.perform(get("/internal/snapshot/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("LOBBY"))
                .andExpect(jsonPath("$.source").value("spring-engine-demo"));
    }
}

package com.payshield.ops;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ops")
public class OpsHealthController {

    private final JdbcTemplate jdbcTemplate;

    public OpsHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        boolean dbUp = false;
        try {
            Integer res = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            dbUp = res != null && res == 1;
        } catch (Exception ignored) {
            dbUp = false;
        }
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        return Map.of(
                "status", dbUp ? "UP" : "DEGRADED",
                "database", dbUp ? "UP" : "DOWN",
                "uptimeSeconds", uptimeMs / 1000,
                "timestamp", Instant.now().toString()
        );
    }
}

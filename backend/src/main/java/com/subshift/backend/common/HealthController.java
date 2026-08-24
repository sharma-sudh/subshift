package com.subshift.backend.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sanity-check endpoint. Hit http://localhost:8080/api/health once the app
 * is running to confirm the project actually boots correctly before
 * building anything real on top of it.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public String health() {
        return "SubShift backend is running";
    }
}

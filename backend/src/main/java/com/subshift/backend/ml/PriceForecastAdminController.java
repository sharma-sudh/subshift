package com.subshift.backend.ml;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Admin only: enforced by the /api/admin/** URL rule in SecurityConfig AND by @PreAuthorize. */
@RestController
@RequestMapping("/api/admin/forecast")
@PreAuthorize("hasRole('ADMIN')")
public class PriceForecastAdminController {

    private final PriceForecastService forecastService;

    public PriceForecastAdminController(PriceForecastService forecastService) {
        this.forecastService = forecastService;
    }

    /** Retrains on the current price history and returns the cross-validated RMSE for each model. */
    @PostMapping("/train")
    public PriceForecastMetrics train() {
        return forecastService.train();
    }

    @GetMapping("/metrics")
    public PriceForecastMetrics metrics() {
        return forecastService.metrics()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The forecast model has not been trained yet"));
    }
}

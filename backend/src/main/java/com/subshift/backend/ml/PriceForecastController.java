package com.subshift.backend.ml;

import com.subshift.backend.ml.PriceForecastService.ForecastResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class PriceForecastController {

    private final PriceForecastService forecastService;

    /** Predicts the next price change of one of the caller's subscriptions. */
    @GetMapping("/{id}/forecast")
    public ForecastResponse forecast(@PathVariable Integer id, Authentication authentication) {
        return forecastService.forecast(authentication.getName(), id);
    }
}

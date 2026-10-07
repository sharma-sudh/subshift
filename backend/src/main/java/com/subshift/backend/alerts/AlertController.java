package com.subshift.backend.alerts;

import com.subshift.backend.alerts.AlertService.AlertResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public List<AlertResponse> list(Authentication authentication) {
        return alertService.list(authentication.getName());
    }

    @PatchMapping("/{id}/read")
    public AlertResponse markRead(@PathVariable Integer id, Authentication authentication) {
        return alertService.markRead(authentication.getName(), id);
    }
}

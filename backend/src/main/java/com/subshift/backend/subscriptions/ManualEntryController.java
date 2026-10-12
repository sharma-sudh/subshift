package com.subshift.backend.subscriptions;

import com.subshift.backend.subscriptions.dto.ManualEntryRequest;
import com.subshift.backend.subscriptions.dto.ManualEntryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class ManualEntryController {

    private final ManualEntryService manualEntryService;

    @PostMapping("/manual")
    public ResponseEntity<ManualEntryResponse> addManualEntry(
            @Valid @RequestBody ManualEntryRequest request,
            Authentication authentication) {

        ManualEntryService.Result result =
                manualEntryService.addManualEntry(authentication.getName(), request);

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }
}

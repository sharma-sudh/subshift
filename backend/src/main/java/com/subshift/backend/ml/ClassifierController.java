package com.subshift.backend.ml;

import com.subshift.backend.ml.EmailClassifierService.Prediction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Admin only: enforced by the /api/admin/** URL rule in SecurityConfig AND by @PreAuthorize. */
@RestController
@RequestMapping("/api/admin/classifier")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ClassifierController {

    public record PredictRequest(@NotBlank @Size(max = 20000) String text) {}

    private final EmailClassifierService classifierService;

    /** Retrains from the current training data and returns the cross-validated metrics. */
    @PostMapping("/train")
    public ClassifierMetrics train() {
        return classifierService.train();
    }

    @GetMapping("/metrics")
    public ClassifierMetrics metrics() {
        return classifierService.metrics()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The classifier has not been trained yet"));
    }

    /** Try any text: shows the renewal probability and whether the sync would skip it. */
    @PostMapping("/predict")
    public Prediction predict(@Valid @RequestBody PredictRequest request) {
        return classifierService.predict(request.text());
    }
}

package com.subshift.backend.training;

import com.subshift.backend.training.TrainingEmailService.BatchRequest;
import com.subshift.backend.training.TrainingEmailService.BatchResult;
import com.subshift.backend.training.TrainingEmailService.Detail;
import com.subshift.backend.training.TrainingEmailService.PageResponse;
import com.subshift.backend.training.TrainingEmailService.Stats;
import com.subshift.backend.training.TrainingEmailService.TrainingRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Admin only: enforced by the /api/admin/** URL rule in SecurityConfig AND by @PreAuthorize. */
@RestController
@RequestMapping("/api/admin/training")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class TrainingEmailController {

    private final TrainingEmailService trainingEmailService;

    @GetMapping
    public PageResponse list(@RequestParam(required = false) String label,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        return trainingEmailService.list(label, page, size);
    }

    @GetMapping("/stats")
    public Stats stats() {
        return trainingEmailService.stats();
    }

    @GetMapping("/{id}")
    public Detail get(@PathVariable Integer id) {
        return trainingEmailService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Detail create(@Valid @RequestBody TrainingRequest request, Authentication authentication) {
        return trainingEmailService.create(authentication.getName(), request);
    }

    @PostMapping("/batch")
    public BatchResult createBatch(@Valid @RequestBody BatchRequest request, Authentication authentication) {
        return trainingEmailService.createBatch(authentication.getName(), request);
    }

    @PutMapping("/{id}")
    public Detail update(@PathVariable Integer id, @Valid @RequestBody TrainingRequest request,
                         Authentication authentication) {
        return trainingEmailService.update(authentication.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer id) {
        trainingEmailService.delete(id);
    }
}

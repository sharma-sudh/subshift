package com.subshift.backend.training;

import com.subshift.backend.common.CurrentUserService;
import com.subshift.backend.users.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Admin tooling for the labeled emails the classifier is trained on. */
@Service
@RequiredArgsConstructor
public class TrainingEmailService {

    private static final int PREVIEW_CHARS = 160;
    private static final int MAX_PAGE_SIZE = 100;

    public record TrainingRequest(
            @NotBlank @Size(max = 20000) String emailText,
            @NotNull @Pattern(regexp = "RENEWAL|NOT_RENEWAL", message = "must be RENEWAL or NOT_RENEWAL") String label) {}

    public record BatchRequest(@NotEmpty @Size(max = 200) List<@Valid TrainingRequest> items) {}

    public record Summary(Integer id, String label, String preview, String labeledBy, LocalDateTime createdAt) {}

    public record Detail(Integer id, String label, String emailText, String labeledBy, LocalDateTime createdAt) {}

    public record PageResponse(List<Summary> items, int page, int size, long totalItems, int totalPages) {}

    public record Stats(long total, long renewal, long notRenewal) {}

    public record BatchResult(int added, int skippedDuplicates) {}

    private final ClassifierTrainingEmailRepository repository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public PageResponse list(String label, int page, int size) {
        if (label != null && !label.equals("RENEWAL") && !label.equals("NOT_RENEWAL")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "label must be RENEWAL or NOT_RENEWAL");
        }
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)),
                Sort.by(Sort.Direction.DESC, "id"));
        Page<ClassifierTrainingEmail> result = label == null
                ? repository.findAll(pageRequest)
                : repository.findByLabel(label, pageRequest);
        return new PageResponse(result.getContent().stream().map(TrainingEmailService::toSummary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Detail get(Integer id) {
        return toDetail(find(id));
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        long renewal = repository.countByLabel("RENEWAL");
        long notRenewal = repository.countByLabel("NOT_RENEWAL");
        return new Stats(renewal + notRenewal, renewal, notRenewal);
    }

    @Transactional
    public Detail create(String adminEmail, TrainingRequest request) {
        User admin = currentUserService.require(adminEmail);
        String text = request.emailText().trim();
        if (repository.existsByEmailText(text)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An identical email is already in the training set");
        }
        return toDetail(repository.save(newEntry(text, request.label(), admin)));
    }

    /** All or nothing. Exact duplicates (already stored, or repeated inside the batch) are skipped. */
    @Transactional
    public BatchResult createBatch(String adminEmail, BatchRequest request) {
        User admin = currentUserService.require(adminEmail);
        Set<String> seen = new HashSet<>();
        int added = 0;
        int skipped = 0;
        for (TrainingRequest item : request.items()) {
            String text = item.emailText().trim();
            if (!seen.add(text) || repository.existsByEmailText(text)) {
                skipped++;
                continue;
            }
            repository.save(newEntry(text, item.label(), admin));
            added++;
        }
        return new BatchResult(added, skipped);
    }

    /** Used while spot-checking labels. The admin who edits an entry becomes its labeler. */
    @Transactional
    public Detail update(String adminEmail, Integer id, TrainingRequest request) {
        User admin = currentUserService.require(adminEmail);
        ClassifierTrainingEmail entry = find(id);
        String text = request.emailText().trim();
        if (!text.equals(entry.getEmailText()) && repository.existsByEmailText(text)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An identical email is already in the training set");
        }
        entry.setEmailText(text);
        entry.setLabel(request.label());
        entry.setLabeledBy(admin);
        return toDetail(entry);
    }

    @Transactional
    public void delete(Integer id) {
        repository.delete(find(id));
    }

    private ClassifierTrainingEmail find(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Training email not found"));
    }

    private static ClassifierTrainingEmail newEntry(String text, String label, User admin) {
        ClassifierTrainingEmail entry = new ClassifierTrainingEmail();
        entry.setEmailText(text);
        entry.setLabel(label);
        entry.setLabeledBy(admin);
        return entry;
    }

    private static Summary toSummary(ClassifierTrainingEmail e) {
        String flat = e.getEmailText().replaceAll("\\s+", " ").trim();
        String preview = flat.length() <= PREVIEW_CHARS ? flat : flat.substring(0, PREVIEW_CHARS) + "...";
        return new Summary(e.getId(), e.getLabel(), preview, e.getLabeledBy().getEmail(), e.getCreatedAt());
    }

    private static Detail toDetail(ClassifierTrainingEmail e) {
        return new Detail(e.getId(), e.getLabel(), e.getEmailText(), e.getLabeledBy().getEmail(), e.getCreatedAt());
    }
}

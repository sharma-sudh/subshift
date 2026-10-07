package com.subshift.backend.catalog;

import com.subshift.backend.catalog.ServiceController.ServiceRequest;
import com.subshift.backend.catalog.ServiceController.ServiceResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Admin-only catalog changes. Enforced by the /api/admin/** URL rule AND @PreAuthorize. */
@RestController
@RequestMapping("/api/admin/services")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminServiceController {

    private final ServiceRepository serviceRepository;
    private final CategoryRepository categoryRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ServiceResponse create(@Valid @RequestBody ServiceRequest req) {
        Service s = new Service();
        s.setName(req.name().trim());
        s.setCategory(findCategory(req.categoryId()));
        return save(s);
    }

    @PutMapping("/{id}")
    @Transactional
    public ServiceResponse update(@PathVariable Integer id, @Valid @RequestBody ServiceRequest req) {
        Service s = find(id);
        s.setName(req.name().trim());
        s.setCategory(findCategory(req.categoryId()));
        return save(s);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Integer id) {
        Service s = find(id);
        try {
            serviceRepository.delete(s);
            serviceRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Service is referenced by subscriptions");
        }
    }

    private ServiceResponse save(Service s) {
        try {
            return ServiceResponse.from(serviceRepository.saveAndFlush(s));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Service name already exists in this category");
        }
    }

    private Service find(Integer id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
    }

    private Category findCategory(Integer id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown categoryId"));
    }
}

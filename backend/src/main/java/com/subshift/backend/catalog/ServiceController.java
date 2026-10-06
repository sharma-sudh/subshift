package com.subshift.backend.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceRepository serviceRepository;
    private final CategoryRepository categoryRepository;

    public record ServiceRequest(@NotBlank String name, @NotNull Integer categoryId) {}

    public record ServiceResponse(Integer id, String name, Integer categoryId, String categoryName) {
        static ServiceResponse from(Service s) {
            return new ServiceResponse(s.getId(), s.getName(),
                    s.getCategory().getId(), s.getCategory().getName());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ServiceResponse> list() {
        return serviceRepository.findAll().stream().map(ServiceResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ServiceResponse get(@PathVariable Integer id) {
        return ServiceResponse.from(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ServiceResponse create(@Valid @RequestBody ServiceRequest req) {
        Service s = new Service();
        s.setName(req.name());
        s.setCategory(findCategory(req.categoryId()));
        return save(s);
    }

    @PutMapping("/{id}")
    @Transactional
    public ServiceResponse update(@PathVariable Integer id, @Valid @RequestBody ServiceRequest req) {
        Service s = find(id);
        s.setName(req.name());
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

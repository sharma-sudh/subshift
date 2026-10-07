package com.subshift.backend.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Read-only catalog for any logged-in user. Changes live in AdminServiceController. */
@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceRepository serviceRepository;

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
        return serviceRepository.findById(id)
                .map(ServiceResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found"));
    }
}

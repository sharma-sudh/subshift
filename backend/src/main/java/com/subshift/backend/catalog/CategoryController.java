package com.subshift.backend.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public record CategoryResponse(Integer id, String name) {}

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryRepository.findAll(Sort.by("name")).stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName())).toList();
    }
}

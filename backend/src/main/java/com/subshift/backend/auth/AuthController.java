package com.subshift.backend.auth;

import com.subshift.backend.users.Role;
import com.subshift.backend.users.RoleRepository;
import com.subshift.backend.users.User;
import com.subshift.backend.users.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public record MeResponse(String email, List<String> authorities) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        String email = normalize(req.email());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        // Self-registration always creates a normal USER; admins are promoted directly in the DB.
        Role role = roleRepository.findByName("USER")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Role USER missing - was the schema.sql seed data applied?"));

        User user = new User();
        user.setName(req.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(role);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        return toResponse(user);
    }

    @PostMapping("/login")
    @Transactional(readOnly = true)
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        // Same message for unknown email and wrong password, so emails can't be probed.
        User user = userRepository.findByEmail(normalize(req.email()))
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return toResponse(user);
    }

    /** Smoke-test endpoint: shows what the JWT filter put into the Authentication. */
    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();
        return new MeResponse(authentication.getName(), authorities);
    }

    private AuthResponse toResponse(User user) {
        String role = user.getRole().getName();
        return new AuthResponse(jwtService.generateToken(user.getEmail(), role),
                user.getName(), user.getEmail(), role);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}

package com.subshift.backend.auth;

public record AuthResponse(String token, String name, String email, String role) {}

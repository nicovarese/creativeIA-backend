package com.ryn.creativeai.api.controller;

import com.ryn.creativeai.api.dto.AuthDtos.AuthResponse;
import com.ryn.creativeai.api.dto.AuthDtos.LoginRequest;
import com.ryn.creativeai.api.dto.AuthDtos.RegisterRequest;
import com.ryn.creativeai.api.dto.AuthDtos.UserDto;
import com.ryn.creativeai.security.AuthService;
import com.ryn.creativeai.security.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUserService currentUser;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @GetMapping("/me")
    public UserDto me() {
        return authService.me(currentUser.requireUser());
    }
}

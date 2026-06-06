package com.ryn.creativeai.security;

import com.ryn.creativeai.api.dto.AuthDtos.AuthResponse;
import com.ryn.creativeai.api.dto.AuthDtos.LoginRequest;
import com.ryn.creativeai.api.dto.AuthDtos.RegisterRequest;
import com.ryn.creativeai.api.dto.AuthDtos.UserDto;
import com.ryn.creativeai.core.domain.model.Role;
import com.ryn.creativeai.core.domain.model.User;
import com.ryn.creativeai.infra.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthResponse register(RegisterRequest req) {
        String email = normalizeEmail(req.email());
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(BAD_REQUEST, "Email already registered");
        }

        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(Role.USER);
        users.save(u);

        return buildResponse(u);
    }

    public AuthResponse login(LoginRequest req) {
        String email = normalizeEmail(req.email());
        User u = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid credentials"));

        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid credentials");
        }

        return buildResponse(u);
    }

    public UserDto me(User u) {
        return new UserDto(u.getId().toString(), u.getEmail(), u.getRole().name());
    }

    private AuthResponse buildResponse(User u) {
        String token = jwt.generate(u.getEmail(), Map.of("role", u.getRole().name()));
        return new AuthResponse(token, me(u));
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}

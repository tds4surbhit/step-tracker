package org.example.steptracker.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.steptracker.dto.AccessTokenResponse;
import org.example.steptracker.dto.AuthResponse;
import org.example.steptracker.dto.GoogleAuthRequest;
import org.example.steptracker.dto.RefreshRequest;
import org.example.steptracker.operation.AuthOperation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthOperation authOperation;

    @PostMapping("/google")
    public AuthResponse loginWithGoogle(@Valid @RequestBody GoogleAuthRequest request) {
        return authOperation.loginWithGoogle(request);
    }

    @PostMapping("/refresh")
    public AccessTokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authOperation.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authOperation.logout(request);
    }
}

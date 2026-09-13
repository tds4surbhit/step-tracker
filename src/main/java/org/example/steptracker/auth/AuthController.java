package org.example.steptracker.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.steptracker.auth.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/google")
    public AuthResponse loginWithGoogle(@Valid @RequestBody GoogleAuthRequest request) {
        return authService.loginWithGoogle(request);
    }

    @PostMapping("/refresh")
    public AccessTokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
    }
}

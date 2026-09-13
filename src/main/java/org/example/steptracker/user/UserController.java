package org.example.steptracker.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.steptracker.common.CurrentUser;
import org.example.steptracker.user.dto.ProfileResponse;
import org.example.steptracker.user.dto.ProfileUpdateRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ProfileResponse getProfile() {
        return userService.getProfile(CurrentUser.id());
    }

    @PutMapping("/me")
    public ProfileResponse updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return userService.updateProfile(CurrentUser.id(), request);
    }
}

package org.example.steptracker.user;

import lombok.RequiredArgsConstructor;
import org.example.steptracker.common.NotFoundException;
import org.example.steptracker.user.dto.ProfileResponse;
import org.example.steptracker.user.dto.ProfileUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    public ProfileResponse getProfile(UUID userId) {
        return toResponse(getById(userId));
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, ProfileUpdateRequest request) {
        User user = getById(userId);
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
        }
        if (request.heightCm() != null) {
            user.setHeightCm(request.heightCm());
        }
        if (request.weightKg() != null) {
            user.setWeightKg(request.weightKg());
        }
        if (request.gender() != null) {
            user.setGender(request.gender());
        }
        return toResponse(user);
    }

    private ProfileResponse toResponse(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getDateOfBirth(),
                user.getHeightCm(),
                user.getWeightKg(),
                user.getGender()
        );
    }
}

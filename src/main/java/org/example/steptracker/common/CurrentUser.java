package org.example.steptracker.common;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id() {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        return UUID.fromString(name);
    }
}

package org.example.steptracker.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.google")
@Getter
@Setter
public class GoogleAuthProperties {
    /** OAuth client IDs (Android + iOS) the ID token's audience must match. */
    private List<String> clientIds;
}

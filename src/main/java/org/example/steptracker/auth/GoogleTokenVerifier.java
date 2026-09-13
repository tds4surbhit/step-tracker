package org.example.steptracker.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.example.steptracker.common.UnauthorizedException;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.io.IOException;

/**
 * Verifies Google ID tokens handed to us by the mobile clients after they complete
 * Google Sign-In natively. We never talk to Google's OAuth endpoints ourselves —
 * this only checks the token's signature, issuer, audience and expiry.
 */
@Service
@RequiredArgsConstructor
public class GoogleTokenVerifier {

    private final GoogleAuthProperties googleAuthProperties;

    public GoogleIdToken.Payload verify(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(googleAuthProperties.getClientIds())
                    .build();
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new UnauthorizedException("Invalid Google ID token");
            }
            return idToken.getPayload();
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid Google ID token");
        }
    }
}

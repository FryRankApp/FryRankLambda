package com.fryrank.util;

import java.io.IOException;
import java.security.GeneralSecurityException;

import com.fryrank.Constants;
import com.fryrank.model.exceptions.AuthorizationDisabledException;
import com.fryrank.model.exceptions.NotAuthorizedException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class Authorizer {

    private final GoogleIdTokenVerifier verifier;
    private final boolean authDisabled;

    public Authorizer(GoogleIdTokenVerifier verifier) {
        this(verifier, false);
    }

    public Authorizer(GoogleIdTokenVerifier verifier, boolean authDisabled) {
        this.verifier = verifier;
        this.authDisabled = authDisabled;
    }

    /**
     * Authorizes a bearer token and returns the account ID from the token.
     * @param token The bearer token to authorize (can be null)
     * @return The account ID from the token's subject claim
     * @throws NotAuthorizedException if the token is null, invalid, or authorization fails
     */
    public String authorizeAndGetAccountId(String token) throws NotAuthorizedException, AuthorizationDisabledException {
        if (authDisabled) {
            log.info("Authorization is disabled, skipping token verification");
            throw new AuthorizationDisabledException("Authorization is disabled");
        }

        if (token == null || token.isEmpty()) {
            throw new NotAuthorizedException(Constants.AUTH_ERROR_MISSING_OR_INVALID_HEADER);
        }

        try {
            final GoogleIdToken idToken = verifier.verify(token);
            if (idToken == null) {
                throw new NotAuthorizedException(Constants.AUTH_ERROR_INVALID_TOKEN);
            }
            return idToken.getPayload().getSubject();
        } catch (GeneralSecurityException | IOException e) {
            log.error("Authorization failed", e);
            throw new NotAuthorizedException(Constants.AUTH_ERROR_VERIFICATION_FAILED);
        }
    }
}

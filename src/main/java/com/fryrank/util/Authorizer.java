package com.fryrank.util;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

import com.fryrank.Constants;
import com.fryrank.model.exceptions.NotAuthorizedException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class Authorizer {

    private final HttpTransport transport;
    private final JsonFactory jsonFactory;
    private final GoogleIdTokenVerifier verifier;

    public Authorizer() {
        this.transport = new NetHttpTransport();
        this.jsonFactory = GsonFactory.getDefaultInstance();
        this.verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
            .setAudience(Collections.singletonList(SSMParameterStore.getGoogleClientIdFromSSM()))
            .build();
    }

    public Authorizer(GoogleIdTokenVerifier verifier) {
        this.transport = new NetHttpTransport();
        this.jsonFactory = GsonFactory.getDefaultInstance();
        this.verifier = verifier;
    }

    /**
     * Authorizes a bearer token and throws appropriate exceptions for failure cases.
     * @param token The bearer token to authorize (can be null)
     * @throws NotAuthorizedException if the token is null, invalid, or authorization fails
     */
    public String authorizeToken(String token) throws NotAuthorizedException {
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

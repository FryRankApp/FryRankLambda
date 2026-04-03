package com.fryrank.util;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.EnumMap;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import com.fryrank.Constants;
import com.fryrank.model.exceptions.AuthorizationDisabledException;
import com.fryrank.model.exceptions.ForbiddenException;
import com.fryrank.model.exceptions.NotAuthorizedException;
import com.fryrank.util.auth.AuthorizationContext;
import com.fryrank.util.auth.DeleteReviewContext;
import com.fryrank.util.auth.Operation;
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
    private final boolean authDisabled;

    private static final Map<Operation, AuthorizationRule> OPERATION_RULES = buildOperationRules();

    public Authorizer() {
        this.transport = new NetHttpTransport();
        this.jsonFactory = GsonFactory.getDefaultInstance();
        this.authDisabled = "true".equals(SSMParameterStore.getDisableAuthFromSSM());
        this.verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
            .setAudience(Collections.singletonList(SSMParameterStore.getGoogleClientIdFromSSM()))
            .build();
    }

    public Authorizer(GoogleIdTokenVerifier verifier) {
        this(verifier, false);
    }

    public Authorizer(GoogleIdTokenVerifier verifier, boolean authDisabled) {
        this.transport = new NetHttpTransport();
        this.jsonFactory = GsonFactory.getDefaultInstance();
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

        return verifyTokenAndGetAccountId(token);
    }

    public boolean authorize(
        Operation operation,
        Supplier<String> tokenSupplier,
        Supplier<? extends AuthorizationContext> contextSupplier
    ) throws NotAuthorizedException, ForbiddenException {
        if (authDisabled) {
            log.info("Authorization is disabled, skipping token verification and authorization checks");
            return true;
        }

        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }

        final String token = tokenSupplier != null ? tokenSupplier.get() : null;
        final String callerAccountId = verifyTokenAndGetAccountId(token);

        final AuthorizationRule rule = OPERATION_RULES.get(operation);
        if (rule == null) {
            throw new ForbiddenException("Forbidden: Operation not permitted");
        }

        final AuthorizationContext context = contextSupplier != null ? contextSupplier.get() : null;
        rule.check(callerAccountId, context);
        return true;
    }

    private String verifyTokenAndGetAccountId(String token) throws NotAuthorizedException {
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

    @FunctionalInterface
    private interface AuthorizationRule {
        void check(String callerAccountId, AuthorizationContext context) throws ForbiddenException;
    }

    private static Map<Operation, AuthorizationRule> buildOperationRules() {
        final EnumMap<Operation, AuthorizationRule> rules = new EnumMap<>(Operation.class);
        rules.put(Operation.DELETE_REVIEW, (callerAccountId, context) -> {
            if (!(context instanceof DeleteReviewContext deleteReviewContext)) {
                throw new IllegalArgumentException("DeleteReviewContext is required for DELETE_REVIEW authorization");
            }
            if (deleteReviewContext.reviewOwnerAccountId() == null || deleteReviewContext.reviewOwnerAccountId().isBlank()) {
                throw new ForbiddenException("Forbidden: Not authorized to delete this review");
            }
            if (!callerAccountId.equals(deleteReviewContext.reviewOwnerAccountId())) {
                throw new ForbiddenException("Forbidden: Not authorized to delete this review");
            }
        });
        return Collections.unmodifiableMap(rules);
    }
}

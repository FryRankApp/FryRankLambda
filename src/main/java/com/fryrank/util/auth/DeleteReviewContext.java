package com.fryrank.util.auth;

public record DeleteReviewContext(String reviewOwnerAccountId) implements AuthorizationContext {}


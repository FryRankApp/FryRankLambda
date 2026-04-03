package com.fryrank.model;

/**
 * Request model for paginated get-all-reviews API (query params).
 */
public record GetAllReviewsRequest(
        String restaurantId,
        String accountId,
        String limit,
        String cursor
) {}

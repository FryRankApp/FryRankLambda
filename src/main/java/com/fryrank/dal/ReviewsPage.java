package com.fryrank.dal;

import com.fryrank.model.Review;

import java.util.List;

/**
 * Paginated review rows from DynamoDB (DAL layer — not an API response type).
 */
public record ReviewsPage(List<Review> reviews, String nextCursor) {

    public ReviewsPage {
        reviews = reviews == null ? List.of() : List.copyOf(reviews);
    }

    public ReviewsPage(List<Review> reviews) {
        this(reviews, null);
    }
}

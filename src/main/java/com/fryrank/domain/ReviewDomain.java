package com.fryrank.domain;

import com.fryrank.dal.ReviewDAL;
import com.fryrank.model.GetAllReviewsOutput;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ReviewDomain {

    private ReviewDAL reviewDAL;

    public GetAllReviewsOutput getAllReviews(final String restaurantId, final String accountId) {
        if (restaurantId != null) {
            return reviewDAL.getAllReviewsByRestaurantId(restaurantId);
        } else if (accountId != null) {
            return reviewDAL.getAllReviewsByAccountId(accountId);
        } else {
            throw new NullPointerException("At least one of restaurantId and accountId must not be null.");
        }
    }
}

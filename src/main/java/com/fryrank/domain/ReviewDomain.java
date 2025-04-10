package com.fryrank.domain;

import com.fryrank.dal.ReviewDAL;
import com.fryrank.model.GetAllReviewsOutput;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@AllArgsConstructor
public class ReviewDomain {

    ReviewDAL reviewDAL;

    public GetAllReviewsOutput getAllReviews(final String restaurantId, final String accountId) {

        log.info("Getting all reviews{}{}",
                restaurantId != null ? " for restaurantId: " + restaurantId : "",
                accountId != null ? " for accountId: " + accountId : "");

        if (restaurantId != null) {
            return reviewDAL.getAllReviewsByRestaurantId(restaurantId);
        } else if (accountId != null) {
            return reviewDAL.getAllReviewsByAccountId(accountId);
        } else {
            throw new NullPointerException("At least one of restaurantId and accountId must not be null.");
        }
    }

    public GetAllReviewsOutput getTopReviews(final Integer count) {
        return reviewDAL.getTopMostRecentReviews(count);
    }
}

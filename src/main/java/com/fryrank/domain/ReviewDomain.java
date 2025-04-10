package com.fryrank.domain;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.fryrank.dal.ReviewDAL;
import com.fryrank.model.AggregateReviewFilter;
import com.fryrank.model.GetAggregateReviewInformationOutput;
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

    public GetAggregateReviewInformationOutput getAggregateReviewInformationForRestaurants(
            String ids,
            Boolean includeRating
    ) {
        List<String> parsedIDs = Arrays.stream(ids.split(",")).sorted().collect(Collectors.toList());
        AggregateReviewFilter filter = new AggregateReviewFilter(includeRating != null ? includeRating : false);
        return reviewDAL.getAggregateReviewInformationForRestaurants(parsedIDs, filter);
    }
}

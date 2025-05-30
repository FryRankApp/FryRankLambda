package com.fryrank.domain;

import static com.fryrank.Constants.REVIEW_VALIDATOR_ERRORS_OBJECT_NAME;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.fryrank.dal.ReviewDAL;
import com.fryrank.model.AggregateReviewFilter;
import com.fryrank.model.GetAggregateReviewInformationOutput;
import com.fryrank.model.GetAllReviewsOutput;
import com.fryrank.model.Review;
import com.fryrank.validator.ReviewValidator;
import com.fryrank.validator.ValidatorException;
import com.fryrank.validator.ValidatorUtils;

import lombok.AllArgsConstructor;
import lombok.NonNull;
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

    public GetAllReviewsOutput getRecentReviews(final Integer count) {
        return reviewDAL.getRecentReviews(count);
    }

    public GetAggregateReviewInformationOutput getAggregateReviewInformationForRestaurants(
            String ids,
            Boolean includeRating
    ) {
        List<String> parsedIDs = Arrays.stream(ids.split(",")).sorted().collect(Collectors.toList());
        AggregateReviewFilter filter = new AggregateReviewFilter(includeRating != null ? includeRating : false);
        return reviewDAL.getAggregateReviewInformationForRestaurants(parsedIDs, filter);
    }

    public Review addNewReviewForRestaurant(@NonNull final Review review) throws ValidatorException {
        ValidatorUtils.validateAndThrow(review, REVIEW_VALIDATOR_ERRORS_OBJECT_NAME, new ReviewValidator());
        return reviewDAL.addNewReview(review);
    }
}

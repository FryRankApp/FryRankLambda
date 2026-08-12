package com.fryrank.domain;

import static com.fryrank.Constants.REVIEW_VALIDATOR_ERRORS_OBJECT_NAME;
import static com.fryrank.Constants.PUT_REACTION_REQUEST_VALIDATOR_ERRORS_OBJECT_NAME;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.fryrank.dal.ReviewDAL;
import com.fryrank.model.exceptions.NotFoundException;
import com.fryrank.model.AggregateReviewFilter;
import com.fryrank.model.DeleteReviewRequest;
import com.fryrank.model.GetAggregateReviewInformationOutput;
import com.fryrank.model.GetAllReviewsOutput;
import com.fryrank.model.Review;
import com.fryrank.model.PutReactionRequest;
import com.fryrank.model.PutReactionResult;
import com.fryrank.validator.PutReactionRequestValidator;
import com.fryrank.model.ReviewFilter;
import com.fryrank.validator.ReviewValidator;
import com.fryrank.validator.ValidatorException;
import com.fryrank.validator.ValidatorUtils;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class ReviewDomain {

    private final ReviewDAL reviewDAL;
    private final ReviewValidator reviewValidator;

    public ReviewDomain(final ReviewDAL reviewDAL, final ReviewValidator reviewValidator) {
        this.reviewDAL = reviewDAL;
        this.reviewValidator = reviewValidator;
    }

    public GetAllReviewsOutput getAllReviews(
            final String restaurantId,
            final String accountId,
            final Integer limit,
            final String cursor,
            @NonNull final ReviewFilter filter
    ) {
        return getAllReviews(restaurantId, accountId, limit, cursor, filter, null);
    }

    public GetAllReviewsOutput getAllReviews(
            final String restaurantId,
            final String accountId,
            final Integer limit,
            final String cursor,
            @NonNull final ReviewFilter filter,
            final String viewerAccountId
    ) {

        log.info("Getting paginated reviews{}{} with limit: {} and cursor: {}",
                restaurantId != null ? " for restaurantId: " + restaurantId : "",
                accountId != null ? " for accountId: " + accountId : "",
                limit,
                cursor,
                filter);

        final GetAllReviewsOutput output;
        if (restaurantId != null) {
            output = reviewDAL.getAllReviewsByRestaurantId(restaurantId, limit, cursor, filter);
        } else if (accountId != null) {
            output = reviewDAL.getAllReviewsByAccountId(accountId, limit, cursor, filter);
        } else {
            throw new NullPointerException("At least one of restaurantId and accountId must not be null.");
        }

        List<Review> reviews = output.getReviews();
        if (viewerAccountId != null && !viewerAccountId.isBlank() && !reviews.isEmpty()) {
            reviews = reviewDAL.getAndFillViewerReactions(viewerAccountId, reviews);
            return new GetAllReviewsOutput(reviews, output.getNextCursor());
        }
        return output;
    }

    public GetAllReviewsOutput getRecentReviews(final Integer count, @NonNull final ReviewFilter filter) {
        return reviewDAL.getRecentReviews(count, filter);
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
        ValidatorUtils.validateAndThrow(review, REVIEW_VALIDATOR_ERRORS_OBJECT_NAME, reviewValidator);
        return reviewDAL.addNewReview(review);
    }

    public void deleteReview(@NonNull final DeleteReviewRequest reviewIDString) throws NotFoundException {
        if (!reviewDAL.deleteUserReview(reviewIDString)) {
            throw new NotFoundException("Review not found in database.");
        }
    }

    /** Sets a reaction for the authenticated viewer on the given review. */
    public PutReactionResult putReaction(
            @NonNull final String viewerAccountId,
            @NonNull final PutReactionRequest request
    ) throws ValidatorException {
        ValidatorUtils.validateAndThrow(
                request,
                PUT_REACTION_REQUEST_VALIDATOR_ERRORS_OBJECT_NAME,
                new PutReactionRequestValidator());
        return reviewDAL.putReaction(
                viewerAccountId,
                request.reviewId(),
                request.reactionType(),
                request.action());
    }
}

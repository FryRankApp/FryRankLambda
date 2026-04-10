package com.fryrank.domain;

import static com.fryrank.Constants.REVIEW_VALIDATOR_ERRORS_OBJECT_NAME;
import static com.fryrank.Constants.TOGGLE_REACTION_REQUEST_VALIDATOR_ERRORS_OBJECT_NAME;

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
import com.fryrank.model.ToggleReactionRequest;
import com.fryrank.model.ToggleReactionResult;
import com.fryrank.validator.ReviewValidator;
import com.fryrank.validator.ToggleReactionRequestValidator;
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

	public GetAllReviewsOutput getAllReviews(final String restaurantId, final String accountId, final Integer limit, final String cursor) {
        return getAllReviews(restaurantId, accountId, limit, cursor, null);
    }

	public GetAllReviewsOutput getAllReviews(
            final String restaurantId,
            final String accountId,
            final Integer limit,
            final String cursor,
            final String viewerAccountId
    ) {
		log.info("Getting paginated reviews{}{} with limit: {} and cursor: {}",
				restaurantId != null ? " for restaurantId: " + restaurantId : "",
				accountId != null ? " for accountId: " + accountId : "",
				limit,
				cursor);
        return reviewDAL.mergeViewerReactions(restaurantId, accountId, viewerAccountId);
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
        ValidatorUtils.validateAndThrow(review, REVIEW_VALIDATOR_ERRORS_OBJECT_NAME, reviewValidator);
        return reviewDAL.addNewReview(review);
    }

    public void deleteReview(@NonNull final DeleteReviewRequest reviewIDString) throws NotFoundException {
        if (!reviewDAL.deleteUserReview(reviewIDString)) {
            throw new NotFoundException("Review not found in database.");
        }
    }

    /** Toggles a reaction for the authenticated viewer on the given review. */
    public ToggleReactionResult toggleReaction(
            @NonNull final String viewerAccountId,
            @NonNull final ToggleReactionRequest request
    ) throws ValidatorException {
        ValidatorUtils.validateAndThrow(
                request,
                TOGGLE_REACTION_REQUEST_VALIDATOR_ERRORS_OBJECT_NAME,
                new ToggleReactionRequestValidator());
        return reviewDAL.toggleReaction(
                viewerAccountId,
                request.reviewId(),
                request.reactionType(),
                request.action());
    }
}

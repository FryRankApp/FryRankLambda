package com.fryrank.dal;

import com.fryrank.model.AggregateReviewFilter;
import com.fryrank.model.DeleteReviewRequest;
import com.fryrank.model.GetAggregateReviewInformationOutput;
import com.fryrank.model.GetAllReviewsOutput;
import com.fryrank.model.Review;
import com.fryrank.model.ToggleReactionResult;
import com.fryrank.model.enums.ReactionAction;
import com.fryrank.model.enums.ReactionType;

import java.util.List;

public interface ReviewDAL {

    GetAllReviewsOutput getAllReviewsByRestaurantId(final String restaurantId, final Integer limit, final String cursor);

    GetAllReviewsOutput getAllReviewsByAccountId(final String accountId, final Integer limit, final String cursor);

    GetAllReviewsOutput getRecentReviews(final Integer count);

    /**
     * Batch-loads the viewer's reaction rows and fills {@link Review#getMyReactions()} on each review.
     * No-op if {@code viewerAccountId} is null or blank.
     */
    GetAllReviewsOutput mergeViewerReactions(final String restaurantId, final String accountId, final String viewerAccountId);

    GetAggregateReviewInformationOutput getAggregateReviewInformationForRestaurants(final List<String> restaurantIds, final AggregateReviewFilter aggregateReviewFilter);

    Review addNewReview(final Review review);

    boolean deleteUserReview(final DeleteReviewRequest delReviewRequest);

    ToggleReactionResult toggleReaction(
            final String viewerAccountId,
            final String reviewId,
            final ReactionType reactionType,
            final ReactionAction action
    );
}

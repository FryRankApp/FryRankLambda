package com.fryrank.dal;

import com.fryrank.model.AggregateReviewFilter;
import com.fryrank.model.DeleteReviewRequest;
import com.fryrank.model.GetAggregateReviewInformationOutput;
import com.fryrank.model.Review;
import com.fryrank.model.PutReactionResult;
import com.fryrank.model.enums.ReactionAction;
import com.fryrank.model.enums.ReactionType;

import java.util.List;

public interface ReviewDAL {

    ReviewsPage getAllReviewsByRestaurantId(final String restaurantId, final Integer limit, final String cursor);

    ReviewsPage getAllReviewsByAccountId(final String accountId, final Integer limit, final String cursor);

    ReviewsPage getRecentReviews(final Integer count);

    /**
     * Batch-loads the viewer's reaction rows and fills {@link Review#getMyReactions()} on each review.
     */
    List<Review> mergeViewerReactions(final String viewerAccountId, final List<Review> reviews);

    GetAggregateReviewInformationOutput getAggregateReviewInformationForRestaurants(final List<String> restaurantIds, final AggregateReviewFilter aggregateReviewFilter);

    Review addNewReview(final Review review);

    boolean deleteUserReview(final DeleteReviewRequest delReviewRequest);

    PutReactionResult putReaction(
            final String viewerAccountId,
            final String reviewId,
            final ReactionType reactionType,
            final ReactionAction action
    );
}

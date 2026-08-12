package com.fryrank.dal;

import com.fryrank.model.AggregateReviewFilter;
import com.fryrank.model.DeleteReviewRequest;
import com.fryrank.model.GetAggregateReviewInformationOutput;
import com.fryrank.model.GetAllReviewsOutput;
import com.fryrank.model.PutReactionResult;
import com.fryrank.model.Review;
import com.fryrank.model.ReviewFilter;
import com.fryrank.model.enums.ReactionAction;
import com.fryrank.model.enums.ReactionType;

import java.util.List;

public interface ReviewDAL {

    GetAllReviewsOutput getAllReviewsByRestaurantId(final String restaurantId, final Integer limit, final String cursor, final ReviewFilter filter);

    GetAllReviewsOutput getAllReviewsByAccountId(final String accountId, final Integer limit, final String cursor, final ReviewFilter filter);

    GetAllReviewsOutput getRecentReviews(final Integer count, final ReviewFilter filter);

    /**
     * Batch-loads the viewer's reaction rows and fills {@link Review#getMyReactions()} on each review.
     */
    List<Review> getAndFillViewerReactions(String viewerAccountId, List<Review> reviews);

    GetAggregateReviewInformationOutput getAggregateReviewInformationForRestaurants(final List<String> restaurantIds, final AggregateReviewFilter aggregateReviewFilter);

    Review addNewReview(final Review review);

    boolean deleteUserReview(final DeleteReviewRequest delReviewRequest);

    PutReactionResult putReaction(
            String viewerAccountId,
            String reviewId,
            ReactionType reactionType,
            ReactionAction action
    );
}

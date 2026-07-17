package com.fryrank.dal;

import com.fryrank.model.AggregateReviewFilter;
import com.fryrank.model.DeleteReviewRequest;
import com.fryrank.model.GetAggregateReviewInformationOutput;
import com.fryrank.model.GetAllReviewsOutput;
import com.fryrank.model.Review;
import com.fryrank.model.ReviewFilter;

import java.util.List;

public interface ReviewDAL {

    GetAllReviewsOutput getAllReviewsByRestaurantId(final String restaurantId, final Integer limit, final String cursor, final ReviewFilter filter);

    GetAllReviewsOutput getAllReviewsByAccountId(final String accountId, final Integer limit, final String cursor, final ReviewFilter filter);

    GetAllReviewsOutput getRecentReviews(final Integer count, final ReviewFilter filter);

    GetAggregateReviewInformationOutput getAggregateReviewInformationForRestaurants(final List<String> restaurantIds, final AggregateReviewFilter aggregateReviewFilter);

    Review putReview(final Review review);

    boolean deleteUserReview(final DeleteReviewRequest delReviewRequest);
}

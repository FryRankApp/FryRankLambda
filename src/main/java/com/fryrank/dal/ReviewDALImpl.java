package com.fryrank.dal;

import com.fryrank.model.*;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.fryrank.Constants.ACCOUNT_ID_KEY;
import static com.fryrank.Constants.PRIMARY_KEY;
import static com.fryrank.Constants.REVIEW_COLLECTION_NAME;
import static com.fryrank.Constants.PUBLIC_USER_METADATA_COLLECTION_NAME;
import static com.fryrank.Constants.USER_METADATA_OUTPUT_FIELD_NAME;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Repository
public class ReviewDALImpl implements ReviewDAL {

    public static final String RESTAURANT_ID_KEY = "restaurantId";

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final List<AggregationOperation> AGGREGATION_OPERATIONS_FOR_PUBLIC_USER_METADATA_COLLECTION_JOIN =
            new ArrayList<>(Arrays.asList(
                    LookupOperation.newLookup()
                            .from(PUBLIC_USER_METADATA_COLLECTION_NAME)
                            .localField(ACCOUNT_ID_KEY)
                            .foreignField(PRIMARY_KEY)
                            .as(USER_METADATA_OUTPUT_FIELD_NAME),
                    Aggregation.unwind("userMetadata")
            ));

    @Override
    public GetAllReviewsOutput getAllReviewsByRestaurantId(@NonNull final String restaurantId) {
        List<AggregationOperation> aggregationOperations = new ArrayList<>(AGGREGATION_OPERATIONS_FOR_PUBLIC_USER_METADATA_COLLECTION_JOIN);
        final Criteria equalToRestaurantIdCriteria = Criteria.where(RESTAURANT_ID_KEY).is(restaurantId);
        aggregationOperations.add(match(equalToRestaurantIdCriteria));

        final Aggregation aggregation = newAggregation(aggregationOperations);
        final AggregationResults<Review> result = mongoTemplate.aggregate(aggregation, REVIEW_COLLECTION_NAME, Review.class);

        return new GetAllReviewsOutput(result.getMappedResults());
    }

    @Override
    public GetAllReviewsOutput getAllReviewsByAccountId(@NonNull final String accountId) {
        List<AggregationOperation> aggregationOperations = new ArrayList<>(AGGREGATION_OPERATIONS_FOR_PUBLIC_USER_METADATA_COLLECTION_JOIN);
        final Criteria equalToAccountIdCriteria = Criteria.where(ACCOUNT_ID_KEY).is(accountId);
        aggregationOperations.add(match(equalToAccountIdCriteria));

        final Aggregation aggregation = newAggregation(aggregationOperations);
        final AggregationResults<Review> result = mongoTemplate.aggregate(aggregation, REVIEW_COLLECTION_NAME, Review.class);

        return new GetAllReviewsOutput(result.getMappedResults());
    }
}

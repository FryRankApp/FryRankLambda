package com.fryrank.dal;

import com.fryrank.model.*;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.LookupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fryrank.Constants.ACCOUNT_ID_KEY;
import static com.fryrank.Constants.PRIMARY_KEY;
import static com.fryrank.Constants.REVIEW_COLLECTION_NAME;
import static com.fryrank.Constants.PUBLIC_USER_METADATA_COLLECTION_NAME;
import static com.fryrank.Constants.USER_METADATA_OUTPUT_FIELD_NAME;
import static com.fryrank.Constants.ISO_DATE_TIME;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Repository
@Log4j2
@AllArgsConstructor
public class ReviewDALImpl implements ReviewDAL {

    final static String RESTAURANT_ID_KEY = "restaurantId";

    private static final List<AggregationOperation> AGGREGATION_OPERATIONS_FOR_PUBLIC_USER_METADATA_COLLECTION_JOIN =
            new ArrayList<>(Arrays.asList(
                    LookupOperation.newLookup()
                            .from(PUBLIC_USER_METADATA_COLLECTION_NAME)
                            .localField(ACCOUNT_ID_KEY)
                            .foreignField(PRIMARY_KEY)
                            .as(USER_METADATA_OUTPUT_FIELD_NAME),
                    Aggregation.unwind("userMetadata")
            ));

    private final MongoTemplate mongoTemplate;

    public ReviewDALImpl() {
        this.mongoTemplate = MongoDBUtils.createMongoTemplate();
    }

    @Override
    public GetAllReviewsOutput getAllReviewsByRestaurantId(@NonNull final String restaurantId) {
        log.info("Getting all reviews for restaurantId: {}", restaurantId);

        List<AggregationOperation> aggregationOperations = new ArrayList<>(AGGREGATION_OPERATIONS_FOR_PUBLIC_USER_METADATA_COLLECTION_JOIN);
        final Criteria equalToRestaurantIdCriteria = Criteria.where(RESTAURANT_ID_KEY).is(restaurantId);
        aggregationOperations.add(match(equalToRestaurantIdCriteria));

        final Aggregation aggregation = newAggregation(aggregationOperations);
        final AggregationResults<Review> result = mongoTemplate.aggregate(aggregation, REVIEW_COLLECTION_NAME, Review.class);

        return new GetAllReviewsOutput(result.getMappedResults());
    }

    @Override
    public GetAllReviewsOutput getAllReviewsByAccountId(@NonNull final String accountId) {
        log.info("Getting all reviews for accountId: {}", accountId);
        List<AggregationOperation> aggregationOperations = new ArrayList<>(AGGREGATION_OPERATIONS_FOR_PUBLIC_USER_METADATA_COLLECTION_JOIN);
        final Criteria equalToAccountIdCriteria = Criteria.where(ACCOUNT_ID_KEY).is(accountId);
        aggregationOperations.add(match(equalToAccountIdCriteria));

        final Aggregation aggregation = newAggregation(aggregationOperations);
        final AggregationResults<Review> result = mongoTemplate.aggregate(aggregation, REVIEW_COLLECTION_NAME, Review.class);

        return new GetAllReviewsOutput(result.getMappedResults());
    }

    @Override
    public GetAllReviewsOutput getRecentReviews(@NonNull final Integer count){
        List<AggregationOperation> aggregationOperations = new ArrayList<>(AGGREGATION_OPERATIONS_FOR_PUBLIC_USER_METADATA_COLLECTION_JOIN);
        aggregationOperations.add(sort(Sort.by(Sort.Direction.DESC, ISO_DATE_TIME)));
        aggregationOperations.add(limit(count));

        final Aggregation aggregation = newAggregation(aggregationOperations);
        final AggregationResults<Review> result = mongoTemplate.aggregate(aggregation, REVIEW_COLLECTION_NAME, Review.class);

        return new GetAllReviewsOutput(result.getMappedResults());
    }

    @Override
    public GetAggregateReviewInformationOutput getAggregateReviewInformationForRestaurants(@NonNull final List<String> restaurantIds, @NonNull final AggregateReviewFilter aggregateReviewFilter) {
        Map<String, AggregateReviewInformation> restaurantIdToAggregateReviewInformation = new HashMap<String, AggregateReviewInformation>();

        final Criteria idInRestaurantIdsInput = Criteria.where(RESTAURANT_ID_KEY).in(restaurantIds);
        final MatchOperation filterToRestaurantId = match(idInRestaurantIdsInput);
        final Criteria hasAccountId = Criteria.where(ACCOUNT_ID_KEY).exists(true);
        final MatchOperation filterToAccountId = match(hasAccountId);
        final GroupOperation averageScoreGroupOperation = group(RESTAURANT_ID_KEY).avg("score").as("avgScore");
        final Aggregation aggregation = newAggregation(filterToRestaurantId, filterToAccountId, averageScoreGroupOperation);
        final AggregationResults<AggregateReviewInformation> result = mongoTemplate.aggregate(aggregation, REVIEW_COLLECTION_NAME, AggregateReviewInformation.class);
        final List<AggregateReviewInformation> aggregateReviewInformationList = result.getMappedResults();

        aggregateReviewInformationList.stream().forEach(
                aggregateReviewInformation -> {
                    final Float averageScore = aggregateReviewFilter.getIncludeRating()
                            ? BigDecimal.valueOf(aggregateReviewInformation.getAvgScore()).setScale(1, RoundingMode.DOWN).floatValue()
                            : null;

                    restaurantIdToAggregateReviewInformation.put(aggregateReviewInformation.getRestaurantId(), new AggregateReviewInformation(aggregateReviewInformation.getRestaurantId(), averageScore));
                }
        );

        return new GetAggregateReviewInformationOutput(restaurantIdToAggregateReviewInformation);
    }

    @Override
    public Review addNewReview(@NonNull final Review review) {
        final Query query = new Query().addCriteria(Criteria.where("_id").is(review.getRestaurantId() + ":" + review.getAccountId()));
        final FindAndReplaceOptions options = new FindAndReplaceOptions();
        options.upsert();

        return mongoTemplate.findAndReplace(query, review, options);
    }

    //Add a new method to handle deleting the review.
}

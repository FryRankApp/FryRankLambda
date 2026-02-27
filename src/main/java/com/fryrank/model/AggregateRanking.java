package com.fryrank.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

import static com.fryrank.Constants.*;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AggregateRanking extends Ranking {

    @NonNull
    private Double totalScore;

    @NonNull
    private Integer reviewCount;

    @NonNull
    private Double averageScore;

    public static AggregateRanking fromMap(Map<String, AttributeValue> map) {
        return AggregateRanking.builder()
                .restaurantId(map.get(RESTAURANT_ID_KEY).s())
                .identifier(map.get(IDENTIFIER_KEY).s())
                .isoDateTime(map.get(ISO_DATE_TIME_KEY) != null ? map.get(ISO_DATE_TIME_KEY).s() : null)
                .totalScore(Double.parseDouble(map.get(TOTAL_SCORE_KEY).n()))
                .reviewCount(Integer.parseInt(map.get(REVIEW_COUNT_KEY).n()))
                .averageScore(Double.parseDouble(map.get(AVERAGE_SCORE_KEY).n()))
                .build();
    }

    public Map<String, AttributeValue> toMap() {
        Map<String, AttributeValue> map = new HashMap<>();
        map.put(RESTAURANT_ID_KEY, AttributeValue.builder().s(getRestaurantId()).build());
        map.put(IDENTIFIER_KEY, AttributeValue.builder().s(getIdentifier()).build());
        map.put(ISO_DATE_TIME_KEY, AttributeValue.builder().s(getIsoDateTime()).build());
        map.put(TOTAL_SCORE_KEY, AttributeValue.builder().n(String.valueOf(totalScore)).build());
        map.put(REVIEW_COUNT_KEY, AttributeValue.builder().n(String.valueOf(reviewCount)).build());
        map.put(AVERAGE_SCORE_KEY, AttributeValue.builder().n(String.valueOf(averageScore)).build());
        return map;
    }


    /**
     * Creates an AggregateRanking with computed average score.
     */
    public static AggregateRanking create(String restaurantId, Double totalScore, Integer reviewCount) {
        return AggregateRanking.builder()
                .restaurantId(restaurantId)
                .identifier(AGGREGATE_IDENTIFIER)
                .isoDateTime(AGGREGATE_IDENTIFIER)
                .totalScore(totalScore)
                .reviewCount(reviewCount)
                .averageScore(totalScore / reviewCount)
                .build();
    }

    /**
     * Creates a new AggregateRanking by adding a new review score to this aggregate.
     */
    public AggregateRanking withNewReview(Double newScore) {
        double newTotalScore = this.totalScore + newScore;
        int newReviewCount = this.reviewCount + 1;
        return AggregateRanking.builder()
                .restaurantId(getRestaurantId())
                .identifier(AGGREGATE_IDENTIFIER)
                .isoDateTime(AGGREGATE_IDENTIFIER)
                .totalScore(newTotalScore)
                .reviewCount(newReviewCount)
                .averageScore(newTotalScore / newReviewCount)
                .build();
    }

    /**
     * Creates a new AggregateRanking for the first review of a restaurant.
     */
    public static AggregateRanking forFirstReview(String restaurantId, Double score) {
        return AggregateRanking.builder()
                .restaurantId(restaurantId)
                .identifier(AGGREGATE_IDENTIFIER)
                .isoDateTime(AGGREGATE_IDENTIFIER)
                .totalScore(score)
                .reviewCount(1)
                .averageScore(score)
                .build();
    }
}
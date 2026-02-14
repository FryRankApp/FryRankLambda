
package com.fryrank.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

/**
 * Represents a row in the rankings table. This can be either a review for a restaurant 
 * or aggregate information that represents the average rating for a restaurant and its 
 * information to calculate it. Refer to https://github.com/FryRankApp/FryRankInfra/wiki/DynamoDB-Migration
 * for more information on schema.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class Ranking {

    public static final String REVIEW_IDENTIFIER_PREFIX = "REVIEW:";
    public static final String AGGREGATE_IDENTIFIER = "AGGREGATE";

    @NonNull
    private String restaurantId;

    @NonNull
    private String identifier;

    private String isoDateTime;

    public boolean isReview() {
        return identifier.startsWith(REVIEW_IDENTIFIER_PREFIX);
    }

    public boolean isAggregate() {
        return AGGREGATE_IDENTIFIER.equals(identifier);
    }
}
package com.fryrank.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReviewRanking extends Ranking {

    @NonNull
    private String accountId;

    private String username;

    @NonNull
    private Double score;

    @NonNull
    private String title;

    @NonNull
    private String body;

    // This flag is here because it serves as a PK to GSIs centered around reviews.
    final private String isReviewFlag = "true";
}
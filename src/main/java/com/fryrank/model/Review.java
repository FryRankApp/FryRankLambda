package com.fryrank.model;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("review")
@Data
@Builder
public class Review {
    @Id
    private final String reviewId;

    @NonNull
    private final String restaurantId;

    @NonNull
    private final Double score;

    @NonNull
    private final String title;

    @NonNull
    private final String body;

    private final String isoDateTime;

    private final String accountId;

    private final PublicUserMetadata userMetadata;
}

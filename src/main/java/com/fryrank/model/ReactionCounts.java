package com.fryrank.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public reaction totals stored on each review item in DynamoDB ({@code reactionCounts} map).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReactionCounts {

    public static ReactionCounts zero() {
        return ReactionCounts.builder().build();
    }

    @Builder.Default
    private int thumbsUp = 0;

    @Builder.Default
    private int thumbsDown = 0;

    @Builder.Default
    private int heart = 0;
}

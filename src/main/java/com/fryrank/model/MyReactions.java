package com.fryrank.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Logged-in viewer's reaction toggles for one review (merged from the reactions table).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyReactions {

    public static MyReactions none() {
        return MyReactions.builder().build();
    }

    @Builder.Default
    private boolean thumbsUp = false;

    @Builder.Default
    private boolean thumbsDown = false;

    @Builder.Default
    private boolean heart = false;
}

package com.fryrank.model.enums;

import com.fryrank.Constants;

/**
 * Which reaction control the viewer toggled. Maps to Dynamo attribute names under {@link Constants#REACTION_COUNTS_KEY}.
 */
public enum ReactionType {
    THUMBS_UP(Constants.THUMBS_UP_KEY),
    THUMBS_DOWN(Constants.THUMBS_DOWN_KEY),
    HEART(Constants.HEART_KEY);

    private final String dynamoAttributeName;

    ReactionType(String dynamoAttributeName) {
        this.dynamoAttributeName = dynamoAttributeName;
    }

    /**
     * Key used in the {@code reactionCounts} map and on per-viewer reaction items.
     */
    public String getDynamoAttributeName() {
        return dynamoAttributeName;
    }
}

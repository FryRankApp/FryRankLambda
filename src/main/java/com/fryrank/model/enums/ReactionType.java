package com.fryrank.model.enums;

import com.fryrank.Constants;

/**
 * Which reaction control the viewer toggled. Maps to Dynamo attribute names under {@link Constants#REACTION_COUNTS_KEY}.
 */
public enum ReactionType {
    THUMBS_UP(Constants.THUMBS_UP_KEY),
    THUMBS_DOWN(Constants.THUMBS_DOWN_KEY),
    HEART(Constants.HEART_KEY);

    ReactionType(@SuppressWarnings("unused") String dynamoAttributeName) {
    }

}

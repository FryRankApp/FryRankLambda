package com.fryrank.model;

import com.fryrank.model.enums.ReactionAction;
import com.fryrank.model.enums.ReactionType;

/**
 * Request body for setting the viewer's reaction on a review (heart / thumbs up / thumbs down).
 *
 * @param accountId    Review author's account id (must match the targeted review's author;
 *                     lets the client validate it selected the correct review).
 * @param reviewId     Review id (same format as in list responses).
 * @param reactionType Which reaction to add or remove.
 * @param action       {@link ReactionAction#ADD} to turn this reaction on, {@link ReactionAction#REMOVE} to turn it off.
 */
public record PutReactionRequest(
        String accountId,
        String reviewId,
        ReactionType reactionType,
        ReactionAction action
) {}

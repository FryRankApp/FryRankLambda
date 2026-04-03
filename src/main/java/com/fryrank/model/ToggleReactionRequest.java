package com.fryrank.model;

import com.fryrank.model.enums.ReactionType;

/**
 * Request body for toggling the viewer's reaction on a review (heart / thumbs up / thumbs down).
 *
 * @param accountId    Review author's account id (must match the targeted review's author;
 *                     lets the client validate it selected the correct review).
 * @param reviewId     Review id (same format as in list responses).
 * @param reactionType Which button to toggle.
 */
public record ToggleReactionRequest(String accountId, String reviewId, ReactionType reactionType) {}

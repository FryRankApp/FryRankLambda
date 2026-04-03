package com.fryrank.model;

/**
 * Result of toggling a reaction (public counts + viewer state).
 */
public record ToggleReactionResult(
        String reviewId,
        ReactionCounts reactionCounts,
        MyReactions myReactions
) {}

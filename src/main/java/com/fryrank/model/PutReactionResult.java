package com.fryrank.model;

/**
 * Result of putting a reaction (public counts + viewer state).
 */
public record PutReactionResult(
        String reviewId,
        ReactionCounts reactionCounts,
        MyReactions myReactions
) {}

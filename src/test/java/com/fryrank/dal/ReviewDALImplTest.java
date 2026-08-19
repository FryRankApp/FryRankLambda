package com.fryrank.dal;

import com.fryrank.model.MyReactions;
import com.fryrank.model.ReactionCounts;
import com.fryrank.model.Review;
import com.fryrank.model.PutReactionResult;
import com.fryrank.model.enums.ReactionAction;
import com.fryrank.model.enums.ReactionType;
import com.fryrank.model.exceptions.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.fryrank.Constants.BODY_KEY;
import static com.fryrank.Constants.HEART_KEY;
import static com.fryrank.Constants.IDENTIFIER_KEY;
import static com.fryrank.Constants.RANKINGS_TABLE_NAME;
import static com.fryrank.Constants.REACTIONS_TABLE_NAME;
import static com.fryrank.Constants.REACTION_COUNTS_KEY;
import static com.fryrank.Constants.REVIEW_ID_KEY;
import static com.fryrank.Constants.RESTAURANT_ID_KEY;
import static com.fryrank.Constants.SCORE_KEY;
import static com.fryrank.Constants.THUMBS_DOWN_KEY;
import static com.fryrank.Constants.THUMBS_UP_KEY;
import static com.fryrank.Constants.TITLE_KEY;
import static com.fryrank.Constants.VIEWER_ACCOUNT_ID_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewDALImplTest {

    private static final String VIEWER = "viewer-acc";
    private static final String REVIEW_ID = "rest1:author1";

    @Mock
    private DynamoDbClient dynamoDb;

    private ReviewDALImpl dal;

    @BeforeEach
    void setUp() {
        dal = new ReviewDALImpl(dynamoDb);
    }

    @Test
    void getAndFillViewerReactions_emptyReviews_skipsReactionBatchGet() {
        List<Review> out = dal.getAndFillViewerReactions(VIEWER, List.of());

        assertTrue(out.isEmpty());
        verify(dynamoDb, never()).batchGetItem(any(BatchGetItemRequest.class));
    }

    @Test
    void getAndFillViewerReactions_withViewer_batchGetsReactionsAndSetsMyReactions() {
        Review review = Review.builder()
                .reviewId(REVIEW_ID)
                .restaurantId("rest1")
                .score(5.0)
                .title("title")
                .body("body")
                .reactionCounts(ReactionCounts.builder().thumbsUp(1).build())
                .build();

        Map<String, AttributeValue> reactionItem = new HashMap<>();
        reactionItem.put(VIEWER_ACCOUNT_ID_KEY, AttributeValue.builder().s(VIEWER).build());
        reactionItem.put(REVIEW_ID_KEY, AttributeValue.builder().s(REVIEW_ID).build());
        reactionItem.put(THUMBS_UP_KEY, AttributeValue.builder().bool(true).build());
        reactionItem.put(THUMBS_DOWN_KEY, AttributeValue.builder().bool(false).build());
        reactionItem.put(HEART_KEY, AttributeValue.builder().bool(false).build());

        when(dynamoDb.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(
                BatchGetItemResponse.builder()
                        .responses(Map.of(REACTIONS_TABLE_NAME, List.of(reactionItem)))
                        .build());

        List<Review> out = dal.getAndFillViewerReactions(VIEWER, List.of(review));

        assertEquals(1, out.size());
        ReactionCounts counts = out.get(0).getReactionCounts();
        assertEquals(1, totalPublicReactions(counts), "card should show exactly one total reaction");
        MyReactions mine = out.get(0).getMyReactions();
        assertEquals(1, togglesOn(mine),
                "when total public reaction is 1, the viewer must have exactly one reaction on");
        assertTrue(mine.isThumbsUp());

        verify(dynamoDb, atLeastOnce()).batchGetItem(any(BatchGetItemRequest.class));
    }

    @Test
    void putReaction_firstThumbsUp_updatesRankingsAndPutReactionRow() {
        Map<String, AttributeValue> reviewRow = rankingReviewRowWithZeroCounts();

        when(dynamoDb.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
            GetItemRequest req = invocation.getArgument(0);
            if (RANKINGS_TABLE_NAME.equals(req.tableName())) {
                return GetItemResponse.builder().item(reviewRow).build();
            }
            if (REACTIONS_TABLE_NAME.equals(req.tableName())) {
                return GetItemResponse.builder().build();
            }
            return GetItemResponse.builder().build();
        });
        when(dynamoDb.updateItem(any(UpdateItemRequest.class))).thenReturn(UpdateItemResponse.builder().build());
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

        PutReactionResult result = dal.putReaction(VIEWER, REVIEW_ID, ReactionType.THUMBS_UP, ReactionAction.ADD);

        assertEquals(REVIEW_ID, result.reviewId());
        assertTrue(result.myReactions().isThumbsUp());
        assertEquals(1, result.reactionCounts().getThumbsUp());

        verify(dynamoDb, never()).deleteItem(any(DeleteItemRequest.class));

        ArgumentCaptor<UpdateItemRequest> updateCap = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDb).updateItem(updateCap.capture());
        AttributeValue rc = updateCap.getValue().expressionAttributeValues().get(":rc");
        assertEquals("1", rc.m().get(THUMBS_UP_KEY).n());

        ArgumentCaptor<PutItemRequest> putCap = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDb).putItem(putCap.capture());
        assertEquals(REACTIONS_TABLE_NAME, putCap.getValue().tableName());
        assertEquals(VIEWER, putCap.getValue().item().get(VIEWER_ACCOUNT_ID_KEY).s());
        assertTrue(putCap.getValue().item().get(THUMBS_UP_KEY).bool());
    }

    /**
     * Older review rows may lack {@code reactionCounts}. First like must use {@code attribute_not_exists}
     * and must not pass unused ExpressionAttributeValues (DynamoDB rejects with ValidationException).
     */
    @Test
    void putReaction_whenReactionCountsAttributeMissing_usesExistsConditionAndOnlyRcInValues() {
        Map<String, AttributeValue> reviewRow = rankingReviewLegacyRowWithoutReactionCounts();

        when(dynamoDb.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
            GetItemRequest req = invocation.getArgument(0);
            if (RANKINGS_TABLE_NAME.equals(req.tableName())) {
                return GetItemResponse.builder().item(reviewRow).build();
            }
            if (REACTIONS_TABLE_NAME.equals(req.tableName())) {
                return GetItemResponse.builder().build();
            }
            return GetItemResponse.builder().build();
        });
        when(dynamoDb.updateItem(any(UpdateItemRequest.class))).thenReturn(UpdateItemResponse.builder().build());
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

        dal.putReaction(VIEWER, REVIEW_ID, ReactionType.HEART, ReactionAction.ADD);

        ArgumentCaptor<UpdateItemRequest> updateCap = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDb).updateItem(updateCap.capture());
        UpdateItemRequest upd = updateCap.getValue();
        assertEquals("attribute_not_exists(reactionCounts)", upd.conditionExpression());
        assertEquals(1, upd.expressionAttributeValues().size());
        assertTrue(upd.expressionAttributeValues().containsKey(":rc"));
        assertFalse(upd.expressionAttributeValues().containsKey(":etu"));
    }

    /**
     * End-to-end style sequence: first call persists a like (put reaction row + bump public count);
     * second call reads that state and unlikes (delete reaction row + decrement count).
     * Mocks advance {@code getItem} order to mimic what Dynamo would return after the first write.
     */
    @Test
    void putReaction_addThumbsUp_thenRemove_inSequence() {
        Map<String, AttributeValue> reviewZero = rankingReviewRowWithZeroCounts();
        Map<String, AttributeValue> reviewOne = rankingReviewRowWithReactionTotals(1, 0, 0);
        Map<String, AttributeValue> reactionLiked = reactionRowWithThumbsUpOnly();

        AtomicInteger getItemCall = new AtomicInteger(0);
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
            GetItemRequest req = invocation.getArgument(0);
            int n = getItemCall.getAndIncrement();
            // Per putReactionOnce: rankings GetItem, then reactions GetItem.
            if (RANKINGS_TABLE_NAME.equals(req.tableName())) {
                if (n == 0) {
                    return GetItemResponse.builder().item(reviewZero).build();
                }
                if (n == 2) {
                    return GetItemResponse.builder().item(reviewOne).build();
                }
            }
            if (REACTIONS_TABLE_NAME.equals(req.tableName())) {
                if (n == 1) {
                    return GetItemResponse.builder().build();
                }
                if (n == 3) {
                    return GetItemResponse.builder().item(reactionLiked).build();
                }
            }
            return GetItemResponse.builder().build();
        });
        when(dynamoDb.updateItem(any(UpdateItemRequest.class))).thenReturn(UpdateItemResponse.builder().build());
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());
        when(dynamoDb.deleteItem(any(DeleteItemRequest.class))).thenReturn(DeleteItemResponse.builder().build());

        PutReactionResult afterAdd = dal.putReaction(VIEWER, REVIEW_ID, ReactionType.THUMBS_UP, ReactionAction.ADD);
        assertTrue(afterAdd.myReactions().isThumbsUp());
        assertEquals(1, afterAdd.reactionCounts().getThumbsUp());

        PutReactionResult afterRemove = dal.putReaction(VIEWER, REVIEW_ID, ReactionType.THUMBS_UP, ReactionAction.REMOVE);
        assertFalse(afterRemove.myReactions().isThumbsUp());
        assertEquals(0, totalPublicReactions(afterRemove.reactionCounts()));

        verify(dynamoDb, times(2)).updateItem(any(UpdateItemRequest.class));
        verify(dynamoDb).putItem(any(PutItemRequest.class));
        verify(dynamoDb).deleteItem(any(DeleteItemRequest.class));
    }

    @Test
    void putReaction_reviewNotFound_throws() {
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(
                GetItemResponse.builder().build());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> dal.putReaction(VIEWER, REVIEW_ID, ReactionType.THUMBS_UP, ReactionAction.ADD));
        assertTrue(ex.getMessage().contains("Review not found"));
    }

    @Test
    void putReaction_invalidReviewId_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dal.putReaction(VIEWER, "no-colon-in-id", ReactionType.THUMBS_UP, ReactionAction.ADD));
        assertTrue(ex.getMessage().contains("Invalid reviewId"));
        verify(dynamoDb, never()).getItem(any(GetItemRequest.class));
    }

    @Test
    void putReaction_idempotentAddWhenAlreadyThumbsUp_skipsWrites() {
        Map<String, AttributeValue> reviewRow = rankingReviewRowWithReactionTotals(1, 0, 0);
        Map<String, AttributeValue> reactionRow = reactionRowWithThumbsUpOnly();

        when(dynamoDb.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
            GetItemRequest req = invocation.getArgument(0);
            if (RANKINGS_TABLE_NAME.equals(req.tableName())) {
                return GetItemResponse.builder().item(reviewRow).build();
            }
            if (REACTIONS_TABLE_NAME.equals(req.tableName())) {
                return GetItemResponse.builder().item(reactionRow).build();
            }
            return GetItemResponse.builder().build();
        });

        PutReactionResult result = dal.putReaction(VIEWER, REVIEW_ID, ReactionType.THUMBS_UP, ReactionAction.ADD);

        assertTrue(result.myReactions().isThumbsUp());
        assertEquals(1, result.reactionCounts().getThumbsUp());
        verify(dynamoDb, never()).updateItem(any(UpdateItemRequest.class));
        verify(dynamoDb, never()).putItem(any(PutItemRequest.class));
        verify(dynamoDb, never()).deleteItem(any(DeleteItemRequest.class));
    }

    @Test
    void putReaction_removeThumbsUp_decrementsPublicCountAndDeletesReactionRow() {
        Map<String, AttributeValue> reviewRow = rankingReviewRowWithReactionTotals(1, 0, 0);
        Map<String, AttributeValue> reactionRow = reactionRowWithThumbsUpOnly();

        when(dynamoDb.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
            GetItemRequest req = invocation.getArgument(0);
            if (RANKINGS_TABLE_NAME.equals(req.tableName())) {
                return GetItemResponse.builder().item(reviewRow).build();
            }
            if (REACTIONS_TABLE_NAME.equals(req.tableName())) {
                return GetItemResponse.builder().item(reactionRow).build();
            }
            return GetItemResponse.builder().build();
        });
        when(dynamoDb.updateItem(any(UpdateItemRequest.class))).thenReturn(UpdateItemResponse.builder().build());

        PutReactionResult result = dal.putReaction(VIEWER, REVIEW_ID, ReactionType.THUMBS_UP, ReactionAction.REMOVE);

        assertFalse(result.myReactions().isThumbsUp());
        assertEquals(0, totalPublicReactions(result.reactionCounts()));

        ArgumentCaptor<UpdateItemRequest> updateCap = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDb).updateItem(updateCap.capture());
        assertEquals("0", updateCap.getValue().expressionAttributeValues().get(":rc").m().get(THUMBS_UP_KEY).n());

        verify(dynamoDb).deleteItem(any(DeleteItemRequest.class));
        verify(dynamoDb, never()).putItem(any(PutItemRequest.class));
    }

    @Test
    void putReaction_idempotentRemoveWhenNotLiked_skipsWrites() {
        Map<String, AttributeValue> reviewRow = rankingReviewRowWithZeroCounts();

        when(dynamoDb.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
            GetItemRequest req = invocation.getArgument(0);
            if (RANKINGS_TABLE_NAME.equals(req.tableName())) {
                return GetItemResponse.builder().item(reviewRow).build();
            }
            if (REACTIONS_TABLE_NAME.equals(req.tableName())) {
                return GetItemResponse.builder().build();
            }
            return GetItemResponse.builder().build();
        });

        PutReactionResult result = dal.putReaction(VIEWER, REVIEW_ID, ReactionType.THUMBS_UP, ReactionAction.REMOVE);

        assertFalse(result.myReactions().isThumbsUp());
        verify(dynamoDb, never()).updateItem(any(UpdateItemRequest.class));
        verify(dynamoDb, never()).putItem(any(PutItemRequest.class));
        verify(dynamoDb, never()).deleteItem(any(DeleteItemRequest.class));
    }

    private static Map<String, AttributeValue> reactionRowWithThumbsUpOnly() {
        Map<String, AttributeValue> reactionRow = new HashMap<>();
        reactionRow.put(VIEWER_ACCOUNT_ID_KEY, AttributeValue.builder().s(VIEWER).build());
        reactionRow.put(REVIEW_ID_KEY, AttributeValue.builder().s(REVIEW_ID).build());
        reactionRow.put(THUMBS_UP_KEY, AttributeValue.builder().bool(true).build());
        reactionRow.put(THUMBS_DOWN_KEY, AttributeValue.builder().bool(false).build());
        reactionRow.put(HEART_KEY, AttributeValue.builder().bool(false).build());
        return reactionRow;
    }

    private static int totalPublicReactions(ReactionCounts c) {
        return c.getThumbsUp() + c.getThumbsDown() + c.getHeart();
    }

    private static int togglesOn(MyReactions m) {
        return (m.isThumbsUp() ? 1 : 0) + (m.isThumbsDown() ? 1 : 0) + (m.isHeart() ? 1 : 0);
    }

    private static Map<String, AttributeValue> rankingReviewRowWithZeroCounts() {
        return rankingReviewRowWithReactionTotals(0, 0, 0);
    }

    /** Pre-reactionCounts schema: item has no {@code reactionCounts} map (treated as zeros when read). */
    private static Map<String, AttributeValue> rankingReviewLegacyRowWithoutReactionCounts() {
        Map<String, AttributeValue> row = new HashMap<>();
        row.put(RESTAURANT_ID_KEY, AttributeValue.builder().s("rest1").build());
        row.put(IDENTIFIER_KEY, AttributeValue.builder().s("REVIEW:author1").build());
        row.put(SCORE_KEY, AttributeValue.builder().n("5").build());
        row.put(TITLE_KEY, AttributeValue.builder().s("t").build());
        row.put(BODY_KEY, AttributeValue.builder().s("b").build());
        return row;
    }

    /** Same base row as {@link #rankingReviewRowWithZeroCounts()} but with explicit public reaction totals on the card. */
    private static Map<String, AttributeValue> rankingReviewRowWithReactionTotals(int thumbsUp, int thumbsDown, int heart) {
        Map<String, AttributeValue> rc = Map.of(
                THUMBS_UP_KEY, AttributeValue.builder().n(String.valueOf(thumbsUp)).build(),
                THUMBS_DOWN_KEY, AttributeValue.builder().n(String.valueOf(thumbsDown)).build(),
                HEART_KEY, AttributeValue.builder().n(String.valueOf(heart)).build()
        );
        Map<String, AttributeValue> row = new HashMap<>();
        row.put(RESTAURANT_ID_KEY, AttributeValue.builder().s("rest1").build());
        row.put(IDENTIFIER_KEY, AttributeValue.builder().s("REVIEW:author1").build());
        row.put(SCORE_KEY, AttributeValue.builder().n("5").build());
        row.put(TITLE_KEY, AttributeValue.builder().s("t").build());
        row.put(BODY_KEY, AttributeValue.builder().s("b").build());
        row.put(REACTION_COUNTS_KEY, AttributeValue.builder().m(rc).build());
        return row;
    }
}

package com.fryrank.dal;

import com.fryrank.model.GetAllReviewsOutput;
import com.fryrank.model.MyReactions;
import com.fryrank.model.ReactionCounts;
import com.fryrank.model.ToggleReactionResult;
import com.fryrank.model.enums.ReactionAction;
import com.fryrank.model.enums.ReactionType;
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

import static com.fryrank.Constants.ACCOUNT_ID_KEY;
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
import static com.fryrank.Constants.USER_METADATA_TABLE_NAME;
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
    private static final String REVIEW_ID = "rest1:REVIEW:author1";

    @Mock
    private DynamoDbClient dynamoDb;

    private ReviewDALImpl dal;

    @BeforeEach
    void setUp() {
        dal = new ReviewDALImpl(dynamoDb);
    }

    @Test
    void mergeViewerReactions_nullRestaurantAndAccount_throws() {
        assertThrows(NullPointerException.class, () -> dal.mergeViewerReactions(null, null, VIEWER));
    }

    @Test
    void mergeViewerReactions_blankViewer_returnsWithoutMergingMyReactions() {
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(
                QueryResponse.builder().items(List.of()).build());

        GetAllReviewsOutput out = dal.mergeViewerReactions("rest1", null, "   ");

        assertTrue(out.getReviews().isEmpty());
        verify(dynamoDb).query(any(QueryRequest.class));
        verify(dynamoDb, never()).batchGetItem(any(BatchGetItemRequest.class));
    }

    @Test
    void mergeViewerReactions_nonEmptyViewerButNoReviews_skipsReactionBatchGet() {
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(
                QueryResponse.builder().items(List.of()).build());

        GetAllReviewsOutput out = dal.mergeViewerReactions("rest1", null, VIEWER);

        assertTrue(out.getReviews().isEmpty());
        verify(dynamoDb, never()).batchGetItem(any(BatchGetItemRequest.class));
    }

    @Test
    void mergeViewerReactions_nullViewer_queriesReviewsAndSkipsReactionBatchGetWhenNoViewer() {
        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(
                QueryResponse.builder().items(List.of()).build());

        GetAllReviewsOutput out = dal.mergeViewerReactions("rest1", null, null);

        assertTrue(out.getReviews().isEmpty());
        verify(dynamoDb).query(any(QueryRequest.class));
        verify(dynamoDb, never()).batchGetItem(any(BatchGetItemRequest.class));
    }

    @Test
    void mergeViewerReactions_withViewer_queriesReviewsThenBatchGetsReactionsAndSetsMyReactions() {
        Map<String, AttributeValue> reviewRow = rankingReviewRowWithReactionTotals(1, 0, 0);
        reviewRow.put(ACCOUNT_ID_KEY, AttributeValue.builder().s("author1").build());

        when(dynamoDb.query(any(QueryRequest.class))).thenReturn(
                QueryResponse.builder().items(List.of(reviewRow)).build());

        Map<String, AttributeValue> reactionItem = new HashMap<>();
        reactionItem.put(VIEWER_ACCOUNT_ID_KEY, AttributeValue.builder().s(VIEWER).build());
        reactionItem.put(REVIEW_ID_KEY, AttributeValue.builder().s(REVIEW_ID).build());
        reactionItem.put(THUMBS_UP_KEY, AttributeValue.builder().bool(true).build());
        reactionItem.put(THUMBS_DOWN_KEY, AttributeValue.builder().bool(false).build());
        reactionItem.put(HEART_KEY, AttributeValue.builder().bool(false).build());

        when(dynamoDb.batchGetItem(any(BatchGetItemRequest.class))).thenAnswer(invocation -> {
            BatchGetItemRequest req = invocation.getArgument(0);
            if (req.requestItems().containsKey(REACTIONS_TABLE_NAME)) {
                return BatchGetItemResponse.builder()
                        .responses(Map.of(REACTIONS_TABLE_NAME, List.of(reactionItem)))
                        .build();
            }
            if (req.requestItems().containsKey(USER_METADATA_TABLE_NAME)) {
                return BatchGetItemResponse.builder()
                        .responses(Map.of(USER_METADATA_TABLE_NAME, List.of()))
                        .build();
            }
            return BatchGetItemResponse.builder().build();
        });

        GetAllReviewsOutput out = dal.mergeViewerReactions("rest1", null, VIEWER);

        assertEquals(1, out.getReviews().size());
        ReactionCounts counts = out.getReviews().get(0).getReactionCounts();
        assertEquals(1, totalPublicReactions(counts), "card should show exactly one total reaction");
        MyReactions mine = out.getReviews().get(0).getMyReactions();
        assertEquals(1, togglesOn(mine),
                "when total public reaction is 1, the viewer must have exactly one reaction toggle on");
        assertTrue(mine.isThumbsUp());

        verify(dynamoDb).query(any(QueryRequest.class));
        verify(dynamoDb, atLeastOnce()).batchGetItem(any(BatchGetItemRequest.class));
    }

    @Test
    void toggleReaction_firstThumbsUp_updatesRankingsAndPutReactionRow() {
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

        ToggleReactionResult result = dal.toggleReaction(VIEWER, REVIEW_ID, ReactionType.THUMBS_UP, ReactionAction.ADD);

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
     * End-to-end style sequence: first call persists a like (put reaction row + bump public count);
     * second call reads that state and unlikes (delete reaction row + decrement count).
     * Mocks advance {@code getItem} order to mimic what Dynamo would return after the first write.
     */
    @Test
    void toggleReaction_addThumbsUp_thenRemove_inSequence() {
        Map<String, AttributeValue> reviewZero = rankingReviewRowWithZeroCounts();
        Map<String, AttributeValue> reviewOne = rankingReviewRowWithReactionTotals(1, 0, 0);
        Map<String, AttributeValue> reactionLiked = reactionRowWithThumbsUpOnly();

        AtomicInteger getItemCall = new AtomicInteger(0);
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenAnswer(invocation -> {
            GetItemRequest req = invocation.getArgument(0);
            int n = getItemCall.getAndIncrement();
            // Per toggleReactionOnce: rankings GetItem, then reactions GetItem.
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

        ToggleReactionResult afterAdd = dal.toggleReaction(VIEWER, REVIEW_ID, ReactionType.THUMBS_UP, ReactionAction.ADD);
        assertTrue(afterAdd.myReactions().isThumbsUp());
        assertEquals(1, afterAdd.reactionCounts().getThumbsUp());

        ToggleReactionResult afterRemove = dal.toggleReaction(VIEWER, REVIEW_ID, ReactionType.THUMBS_UP, ReactionAction.REMOVE);
        assertFalse(afterRemove.myReactions().isThumbsUp());
        assertEquals(0, afterRemove.reactionCounts().getThumbsUp());

        verify(dynamoDb, times(2)).updateItem(any(UpdateItemRequest.class));
        verify(dynamoDb).putItem(any(PutItemRequest.class));
        verify(dynamoDb).deleteItem(any(DeleteItemRequest.class));
    }

    @Test
    void toggleReaction_reviewNotFound_throws() {
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(
                GetItemResponse.builder().build());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dal.toggleReaction(VIEWER, REVIEW_ID, ReactionType.THUMBS_UP, ReactionAction.ADD));
        assertTrue(ex.getMessage().contains("Review not found"));
    }

    @Test
    void toggleReaction_invalidReviewId_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> dal.toggleReaction(VIEWER, "no-colon-in-id", ReactionType.THUMBS_UP, ReactionAction.ADD));
        assertTrue(ex.getMessage().contains("Invalid reviewId"));
        verify(dynamoDb, never()).getItem(any(GetItemRequest.class));
    }

    @Test
    void toggleReaction_idempotentAddWhenAlreadyThumbsUp_skipsWrites() {
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

        ToggleReactionResult result = dal.toggleReaction(VIEWER, REVIEW_ID, ReactionType.THUMBS_UP, ReactionAction.ADD);

        assertTrue(result.myReactions().isThumbsUp());
        assertEquals(1, result.reactionCounts().getThumbsUp());
        verify(dynamoDb, never()).updateItem(any(UpdateItemRequest.class));
        verify(dynamoDb, never()).putItem(any(PutItemRequest.class));
        verify(dynamoDb, never()).deleteItem(any(DeleteItemRequest.class));
    }

    @Test
    void toggleReaction_removeThumbsUp_decrementsPublicCountAndDeletesReactionRow() {
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

        ToggleReactionResult result = dal.toggleReaction(VIEWER, REVIEW_ID, ReactionType.THUMBS_UP, ReactionAction.REMOVE);

        assertFalse(result.myReactions().isThumbsUp());
        assertEquals(0, result.reactionCounts().getThumbsUp());

        ArgumentCaptor<UpdateItemRequest> updateCap = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDb).updateItem(updateCap.capture());
        assertEquals("0", updateCap.getValue().expressionAttributeValues().get(":rc").m().get(THUMBS_UP_KEY).n());

        verify(dynamoDb).deleteItem(any(DeleteItemRequest.class));
        verify(dynamoDb, never()).putItem(any(PutItemRequest.class));
    }

    @Test
    void toggleReaction_idempotentRemoveWhenNotLiked_skipsWrites() {
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

        ToggleReactionResult result = dal.toggleReaction(VIEWER, REVIEW_ID, ReactionType.THUMBS_UP, ReactionAction.REMOVE);

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

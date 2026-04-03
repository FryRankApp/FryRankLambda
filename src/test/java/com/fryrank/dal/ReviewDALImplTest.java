package com.fryrank.dal;

import com.fryrank.model.GetAllReviewsOutput;
import com.fryrank.model.MyReactions;
import com.fryrank.model.PublicUserMetadata;
import com.fryrank.model.ReactionCounts;
import com.fryrank.model.Review;
import com.fryrank.model.ToggleReactionResult;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void mergeViewerReactions_nullViewer_returnsSameOutputWithoutCallingDynamo() {
        Review review = baseReviewBuilder().build();
        GetAllReviewsOutput input = new GetAllReviewsOutput(List.of(review));

        GetAllReviewsOutput out = dal.mergeViewerReactions(null, input);

        assertSame(input, out);
        verifyNoInteractions(dynamoDb);
    }

    @Test
    void mergeViewerReactions_withViewer_batchGetsReactionsAndSetsMyReactions() {
        // Merge copies reactionCounts from the list query unchanged; set card totals like the rankings row after a toggle.
        ReactionCounts countsOnCard = ReactionCounts.builder().thumbsUp(1).thumbsDown(0).heart(0).build();
        Review review = baseReviewBuilder().reactionCounts(countsOnCard).build();
        GetAllReviewsOutput input = new GetAllReviewsOutput(List.of(review));

        Map<String, AttributeValue> reactionItem = new HashMap<>();
        reactionItem.put(VIEWER_ACCOUNT_ID_KEY, AttributeValue.builder().s(VIEWER).build());
        reactionItem.put(REVIEW_ID_KEY, AttributeValue.builder().s(REVIEW_ID).build());
        reactionItem.put(THUMBS_UP_KEY, AttributeValue.builder().bool(true).build());
        reactionItem.put(THUMBS_DOWN_KEY, AttributeValue.builder().bool(false).build());
        reactionItem.put(HEART_KEY, AttributeValue.builder().bool(false).build());

        when(dynamoDb.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(
                BatchGetItemResponse.builder()
                        .responses(Map.of(REACTIONS_TABLE_NAME, List.of(reactionItem)))
                        .build()
        );

        GetAllReviewsOutput out = dal.mergeViewerReactions(VIEWER, input);

        assertEquals(1, out.getReviews().size());
        MyReactions mine = out.getReviews().get(0).getMyReactions();
        assertTrue(mine.isThumbsUp());
        assertFalse(mine.isThumbsDown());
        assertFalse(mine.isHeart());
        assertEquals(1, out.getReviews().get(0).getReactionCounts().getThumbsUp());

        ArgumentCaptor<BatchGetItemRequest> batchCap = ArgumentCaptor.forClass(BatchGetItemRequest.class);
        verify(dynamoDb).batchGetItem(batchCap.capture());
        assertEquals(REACTIONS_TABLE_NAME, batchCap.getValue().requestItems().keySet().iterator().next());
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

        ToggleReactionResult result = dal.toggleReaction(VIEWER, REVIEW_ID, ReactionType.THUMBS_UP);

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

    private static Review.ReviewBuilder baseReviewBuilder() {
        return Review.builder()
                .reviewId(REVIEW_ID)
                .restaurantId("rest1")
                .score(5.0)
                .title("t")
                .body("b")
                .accountId("author1")
                .userMetadata((PublicUserMetadata) null)
                .reactionCounts(ReactionCounts.zero());
    }

    private static Map<String, AttributeValue> rankingReviewRowWithZeroCounts() {
        Map<String, AttributeValue> rc = Map.of(
                THUMBS_UP_KEY, AttributeValue.builder().n("0").build(),
                THUMBS_DOWN_KEY, AttributeValue.builder().n("0").build(),
                HEART_KEY, AttributeValue.builder().n("0").build()
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

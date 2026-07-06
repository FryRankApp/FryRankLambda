package com.fryrank.dal;
import com.fryrank.dal.enums.WriteMode;
import com.fryrank.model.PublicUserMetadata;
import com.fryrank.model.PublicUserMetadataOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure;

import java.util.Map;

import static com.fryrank.Constants.ACCOUNT_ID_KEY;
import static com.fryrank.TestConstants.TEST_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_ACCOUNT_ID_NO_USER_METADATA;
import static com.fryrank.TestConstants.TEST_DEFAULT_NAME;
import static com.fryrank.TestConstants.TEST_USERNAME;
import static com.fryrank.TestConstants.TEST_USER_METADATA_1;
import static com.fryrank.TestConstants.TEST_USER_METADATA_OUTPUT_1;
import static com.fryrank.TestConstants.TEST_PUBLIC_USER_METADATA_OUTPUT_EMPTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PublicUserMetadataDALTests {
    @Mock
    DynamoDbClient dynamoDb;

    @InjectMocks
    UserMetadataDALImpl userMetadataDAL;

    @Test
    public void testPutPublicUserMetadata_createIfAbsent_itemAbsent_attachesConditionAndReturnsUsername() throws Exception {
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

        final PublicUserMetadataOutput actualOutput =
                userMetadataDAL.putPublicUserMetadata(TEST_USER_METADATA_1, WriteMode.CREATE_IF_ABSENT);

        final ArgumentCaptor<PutItemRequest> requestCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDb).putItem(requestCaptor.capture());
        final PutItemRequest request = requestCaptor.getValue();

        assertEquals("attribute_not_exists(#pk)", request.conditionExpression());
        assertEquals(Map.of("#pk", ACCOUNT_ID_KEY), request.expressionAttributeNames());
        assertEquals(ReturnValuesOnConditionCheckFailure.ALL_OLD, request.returnValuesOnConditionCheckFailure());
        assertEquals(TEST_USER_METADATA_OUTPUT_1, actualOutput);
    }

    @Test
    public void testPutPublicUserMetadata_createIfAbsent_itemExists_returnsExistingUsername() throws Exception {
        // Simulate the race: the conditional put fails because the item already exists,
        // and DynamoDB returns the already-stored attributes via ALL_OLD.
        final ConditionalCheckFailedException conflict = ConditionalCheckFailedException.builder()
                .item(Map.of(
                        ACCOUNT_ID_KEY, AttributeValue.builder().s(TEST_ACCOUNT_ID).build(),
                        "username", AttributeValue.builder().s(TEST_USERNAME).build()
                ))
                .build();
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenThrow(conflict);

        final PublicUserMetadata attemptedWrite = new PublicUserMetadata(TEST_ACCOUNT_ID, TEST_DEFAULT_NAME);
        final PublicUserMetadataOutput actualOutput =
                userMetadataDAL.putPublicUserMetadata(attemptedWrite, WriteMode.CREATE_IF_ABSENT);

        assertEquals(TEST_USER_METADATA_OUTPUT_1, actualOutput);
    }

    @Test
    public void testPutPublicUserMetadata_overwrite_writesUnconditionally() throws Exception {
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

        final PublicUserMetadataOutput actualOutput =
                userMetadataDAL.putPublicUserMetadata(TEST_USER_METADATA_1, WriteMode.OVERWRITE);

        final ArgumentCaptor<PutItemRequest> requestCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDb).putItem(requestCaptor.capture());

        assertNull(requestCaptor.getValue().conditionExpression());
        assertEquals(TEST_USER_METADATA_OUTPUT_1, actualOutput);
    }

    @Test
    public void testPutPublicUserMetadata_nullUserMetadata() throws Exception {
        assertThrows(NullPointerException.class,
                () -> userMetadataDAL.putPublicUserMetadata(null, WriteMode.OVERWRITE));
    }

    @Test
    public void testGetPublicUserMetadataForAccountId_happyPath() throws Exception {
        GetItemResponse getItemResponse = GetItemResponse.builder()
                .item(Map.of(
                        ACCOUNT_ID_KEY, AttributeValue.builder().s(TEST_ACCOUNT_ID).build(),
                        "username", AttributeValue.builder().s(TEST_USER_METADATA_1.getUsername()).build()
                ))
                .build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(getItemResponse);

        final PublicUserMetadataOutput actualOutput = userMetadataDAL.getPublicUserMetadataForAccountId(TEST_ACCOUNT_ID);
        assertEquals(TEST_USER_METADATA_OUTPUT_1, actualOutput);
    }

    @Test
    public void testGetPublicUserMetadataForAccountId_noUserMetadata() throws Exception {
        GetItemResponse getItemResponse = GetItemResponse.builder()
                .item(Map.of())
                .build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(getItemResponse);

        final PublicUserMetadataOutput actualOutput = userMetadataDAL.getPublicUserMetadataForAccountId(TEST_ACCOUNT_ID_NO_USER_METADATA);
        assertEquals(TEST_PUBLIC_USER_METADATA_OUTPUT_EMPTY, actualOutput);
    }

    @Test
    public void testGetPublicUserMetadataForAccountId_nullAccountId() throws Exception {
        assertThrows(NullPointerException.class, () -> userMetadataDAL.getPublicUserMetadataForAccountId(null));
    }
}

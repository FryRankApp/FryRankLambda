package com.fryrank.dal;
import com.fryrank.model.PublicUserMetadata;
import com.fryrank.model.PublicUserMetadataOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

import java.util.Map;

import static com.fryrank.Constants.ACCOUNT_ID_KEY;
import static com.fryrank.TestConstants.TEST_ACCOUNT_ID;
import static com.fryrank.TestConstants.TEST_ACCOUNT_ID_NO_USER_METADATA;
import static com.fryrank.TestConstants.TEST_DEFAULT_NAME;
import static com.fryrank.TestConstants.TEST_USER_METADATA_1;
import static com.fryrank.TestConstants.TEST_USER_METADATA_OUTPUT_1;
import static com.fryrank.TestConstants.TEST_PUBLIC_USER_METADATA_OUTPUT_EMPTY;
import static com.fryrank.TestConstants.TEST_PUBLIC_USER_METADATA_OUTPUT_WITH_DEFAULT_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PublicUserMetadataDALTests {
    @Mock
    DynamoDbClient dynamoDb;

    @InjectMocks
    UserMetadataDALImpl userMetadataDAL;

    @Test
    public void testPutPublicUserMetadataForAccountId_happyPath() throws Exception {
        // Mock getItem to return existing user metadata
        GetItemResponse getItemResponse = GetItemResponse.builder()
                .item(Map.of(
                        ACCOUNT_ID_KEY, AttributeValue.builder().s(TEST_ACCOUNT_ID).build(),
                        "username", AttributeValue.builder().s(TEST_USER_METADATA_1.getUsername()).build()
                ))
                .build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(getItemResponse);

        final PublicUserMetadataOutput actualOutput = userMetadataDAL.putPublicUserMetadataForAccountId(TEST_ACCOUNT_ID, TEST_DEFAULT_NAME);
        assertEquals(TEST_USER_METADATA_OUTPUT_1, actualOutput);
    }

    @Test
    public void testPutPublicUserMetadataForAccountId_noUserMetadata() throws Exception {
        // Mock getItem to return empty (no existing metadata)
        GetItemResponse getItemResponse = GetItemResponse.builder()
                .item(Map.of())
                .build();
        when(dynamoDb.getItem(any(GetItemRequest.class))).thenReturn(getItemResponse);
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

        final PublicUserMetadataOutput actualOutput = userMetadataDAL.putPublicUserMetadataForAccountId(TEST_ACCOUNT_ID_NO_USER_METADATA, TEST_DEFAULT_NAME);
        assertEquals(TEST_PUBLIC_USER_METADATA_OUTPUT_WITH_DEFAULT_NAME, actualOutput);
    }

    @Test
    public void testPutPublicUserMetadataForAccountId_nullAccountId() throws Exception {
        assertThrows(NullPointerException.class, () -> userMetadataDAL.putPublicUserMetadataForAccountId(null, null));
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

    @Test
    public void testUpsertPublicUserMetadata() throws Exception {
        when(dynamoDb.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

        final PublicUserMetadataOutput actualUserMetadata = userMetadataDAL.upsertPublicUserMetadata(TEST_USER_METADATA_1);
        assertEquals(TEST_USER_METADATA_OUTPUT_1, actualUserMetadata);
    }
}
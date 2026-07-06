package com.fryrank.dal;

import com.fryrank.dal.enums.WriteMode;
import com.fryrank.model.PublicUserMetadata;
import com.fryrank.model.PublicUserMetadataOutput;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure;

import java.util.Map;

import static com.fryrank.Constants.ACCOUNT_ID_KEY;
import static com.fryrank.Constants.USER_METADATA_TABLE_NAME;

@Repository
@Log4j2
public class UserMetadataDALImpl implements UserMetadataDAL {

    private static final String USERNAME_KEY = "username";

    private final DynamoDbClient dynamoDb;

    public UserMetadataDALImpl(final DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public PublicUserMetadataOutput putPublicUserMetadata(
            @NonNull final PublicUserMetadata userMetadata,
            @NonNull final WriteMode mode
    ) {
        log.info("Putting public user metadata for accountId: {} with mode: {}", userMetadata.getAccountId(), mode);

        final Map<String, AttributeValue> item = Map.of(
                ACCOUNT_ID_KEY, AttributeValue.builder().s(userMetadata.getAccountId()).build(),
                USERNAME_KEY, AttributeValue.builder().s(userMetadata.getUsername()).build()
        );

        final PutItemRequest.Builder requestBuilder = PutItemRequest.builder()
                .tableName(USER_METADATA_TABLE_NAME)
                .item(item);

        if (mode == WriteMode.CREATE_IF_ABSENT) {
            // Guards on record existence (the partition key), not username presence:
            // an existing item is returned untouched, even if its stored username is null.
            requestBuilder
                    .conditionExpression("attribute_not_exists(#pk)")
                    .expressionAttributeNames(Map.of("#pk", ACCOUNT_ID_KEY))
                    .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD);

            try {
                dynamoDb.putItem(requestBuilder.build());
            } catch (final ConditionalCheckFailedException e) {
                // Item already exists; return the username that's already stored.
                final AttributeValue usernameAttr = e.item().get(USERNAME_KEY);
                final String existingUsername = (usernameAttr == null) ? null : usernameAttr.s();
                return new PublicUserMetadataOutput(existingUsername);
            }
        } else {
            dynamoDb.putItem(requestBuilder.build());
        }

        // DynamoDB PutItem doesn't return the saved item by default
        return new PublicUserMetadataOutput(userMetadata.getUsername());
    }

    @Override
    public PublicUserMetadataOutput getPublicUserMetadataForAccountId(@NonNull final String accountId) {
        log.info("Getting public user metadata for accountId: {}", accountId);

        final Map<String, AttributeValue> key = Map.of(
                ACCOUNT_ID_KEY, AttributeValue.builder().s(accountId).build()
        );

        final GetItemRequest request = GetItemRequest.builder()
                .tableName(USER_METADATA_TABLE_NAME)
                .key(key)
                .build();

        final GetItemResponse response = dynamoDb.getItem(request);
        final Map<String, AttributeValue> item = response.item();

        if (item == null || item.isEmpty()) {
            return new PublicUserMetadataOutput(null);
        }

        final AttributeValue usernameAttr = item.get(USERNAME_KEY);
        final String username = (usernameAttr == null) ? null : usernameAttr.s();
        return new PublicUserMetadataOutput(username);
    }
}

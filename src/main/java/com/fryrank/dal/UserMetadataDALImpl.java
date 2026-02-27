package com.fryrank.dal;

import com.fryrank.model.PublicUserMetadata;
import com.fryrank.model.PublicUserMetadataOutput;
import com.fryrank.util.DynamoDbUtils;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;

import static com.fryrank.Constants.ACCOUNT_ID_KEY;
import static com.fryrank.util.EnvironmentUtils.getRequiredEnv;

@Repository
@Log4j2
@AllArgsConstructor
public class UserMetadataDALImpl implements UserMetadataDAL {

    /**
     * DynamoDB table name for public user metadata.
     * Must be set via environment variable.
     */
    private static final String TABLE_NAME = getRequiredEnv("PUBLIC_USER_METADATA_TABLE_NAME");

    private static final String USERNAME_KEY = "username";

    private final DynamoDbClient dynamoDb;

    public UserMetadataDALImpl() {
        this.dynamoDb = DynamoDbUtils.client();
    }

    @Override
    public PublicUserMetadataOutput putPublicUserMetadataForAccountId(
            @NonNull final String accountId,
            @NonNull final String defaultUserName
    ) {
        log.info("Putting public user metadata for accountId: {}", accountId);

        // Check if it already exists; if so, return current value.
        final PublicUserMetadataOutput existing = getPublicUserMetadataForAccountId(accountId);
        if (existing.getUsername() != null) {
            return existing;
        }

        final PublicUserMetadata newUserMetadata = new PublicUserMetadata(accountId, defaultUserName);
        return upsertPublicUserMetadata(newUserMetadata);
    }

    @Override
    public PublicUserMetadataOutput getPublicUserMetadataForAccountId(@NonNull final String accountId) {
        log.info("Getting public user metadata for accountId: {}", accountId);

        final Map<String, AttributeValue> key = Map.of(
                ACCOUNT_ID_KEY, AttributeValue.builder().s(accountId).build()
        );

        final GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .consistentRead(true)
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

    @Override
    public PublicUserMetadataOutput upsertPublicUserMetadata(@NonNull final PublicUserMetadata userMetadata) {
        log.info("Upserting public user metadata for accountId: {}", userMetadata.getAccountId());

        final Map<String, AttributeValue> item = Map.of(
                ACCOUNT_ID_KEY, AttributeValue.builder().s(userMetadata.getAccountId()).build(),
                USERNAME_KEY, AttributeValue.builder().s(userMetadata.getUsername()).build()
        );

        final PutItemRequest request = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build();

        dynamoDb.putItem(request);

        // DynamoDB PutItem doesn't return the saved item by default
        return new PublicUserMetadataOutput(userMetadata.getUsername());
    }
}

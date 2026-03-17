package com.fryrank.util;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Utility class for DynamoDB client creation.
 */
public class DynamoDbUtils {

    /**
     * Creates a DynamoDbClient using the default credential chain and region
     * (reads region from AWS_REGION env var, which Lambda sets automatically).
     */
    public static DynamoDbClient client() {
        return DynamoDbClient.create();
    }
}

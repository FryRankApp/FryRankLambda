package com.fryrank.util;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public final class DynamoDbUtils {
    private static final DynamoDbClient CLIENT = DynamoDbClient.builder()
            // If you omit region, SDK can often infer it from environment in AWS.
            .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-west-2")))
            // Credentials are typically auto-resolved (Lambda role, env vars, profiles, etc.)
            .build();

    private DynamoDbUtils() {}

    public static DynamoDbClient client() {
        return CLIENT;
    }
}

package com.fryrank.util;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import com.amazonaws.xray.interceptors.TracingInterceptor;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public final class DynamoDbUtils {
    private static final DynamoDbClient CLIENT = DynamoDbClient.builder()
            .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-west-2")))
            .overrideConfiguration(
                    ClientOverrideConfiguration.builder()
                            .addExecutionInterceptor(new TracingInterceptor())
                            .build()
            )
            .build();

    private DynamoDbUtils() {}

    public static DynamoDbClient client() {
        return CLIENT;
    }
}
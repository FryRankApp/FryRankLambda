package com.fryrank.dagger;

import com.amazonaws.xray.interceptors.TracingInterceptor;
import dagger.Module;
import dagger.Provides;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ssm.SsmClient;

import javax.inject.Singleton;

import static com.fryrank.Constants.AWS_REGION_ENV_VAR;
import static com.fryrank.Constants.DEFAULT_AWS_REGION;

@Module
public class AwsModule {
    @Provides
    @Singleton
    static Region region() {
        return Region.of(System.getenv().getOrDefault(AWS_REGION_ENV_VAR, DEFAULT_AWS_REGION));
    }

    @Provides
    @Singleton
    static DynamoDbClient dynamoDbClient(Region region) {
        return DynamoDbClient.builder()
                .region(region)
                .overrideConfiguration(
                        ClientOverrideConfiguration.builder()
                                .addExecutionInterceptor(new TracingInterceptor())
                                .build()
                )
                .build();
    }

    @Provides
    @Singleton
    static SsmClient ssmClient(Region region) {
        return SsmClient.builder()
                .region(region)
                .build();
    }
}

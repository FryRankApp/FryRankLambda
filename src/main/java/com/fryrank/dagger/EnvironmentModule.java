package com.fryrank.dagger;

import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;

import static com.fryrank.Constants.AWS_REGION_ENV_VAR;
import static com.fryrank.Constants.DEFAULT_AWS_REGION;
import static com.fryrank.Constants.DATABASE_URI_PARAMETER_NAME_ENV_VAR;
import static com.fryrank.Constants.GOOGLE_CLIENT_ID_PARAMETER_NAME_ENV_VAR;
import static com.fryrank.Constants.SSM_DISABLE_AUTH_PARAMETER_NAME_ENV_VAR;

/**
 * Captures a snapshot of {@link System#getenv()} during init, so env vars are read once and then reused.
 */
@Module
public final class EnvironmentModule {
    public static final String NAME_REGION = "region";
    public static final String NAME_LAMBDA_FUNCTION_VERSION = "lambdaFunctionVersion";
    public static final String NAME_LAMBDA_INITIALIZATION_TYPE = "lambdaInitializationType";
    public static final String NAME_SSM_DATABASE_URI_PARAMETER_KEY = "ssmDatabaseUriParameterKey";
    public static final String NAME_SSM_GOOGLE_CLIENT_ID_PARAMETER_KEY = "ssmGoogleClientIdParameterKey";
    public static final String NAME_SSM_DISABLE_AUTH_PARAMETER_KEY = "ssmDisableAuthParameterKey";

    private final String region;
    private final String lambdaFunctionVersion;
    private final String lambdaInitializationType;

    private final String ssmDatabaseUriParameterKey;
    private final String ssmGoogleClientIdParameterKey;
    private final String ssmDisableAuthParameterKey;

    public EnvironmentModule() {
        final Map<String, String> env = System.getenv();

        this.region = env.getOrDefault(AWS_REGION_ENV_VAR, DEFAULT_AWS_REGION);
        this.lambdaFunctionVersion = env.get("AWS_LAMBDA_FUNCTION_VERSION");
        this.lambdaInitializationType = env.get("AWS_LAMBDA_INITIALIZATION_TYPE");

        this.ssmDatabaseUriParameterKey = getRequiredEnv(env, DATABASE_URI_PARAMETER_NAME_ENV_VAR);
        this.ssmGoogleClientIdParameterKey = getRequiredEnv(env, GOOGLE_CLIENT_ID_PARAMETER_NAME_ENV_VAR);
        this.ssmDisableAuthParameterKey = getRequiredEnv(env, SSM_DISABLE_AUTH_PARAMETER_NAME_ENV_VAR);
    }

    @Provides
    @Singleton
    @Named(NAME_REGION)
    String providesRegion() {
        return region;
    }

    @Provides
    @Singleton
    @Named(NAME_LAMBDA_FUNCTION_VERSION)
    String providesLambdaFunctionVersion() {
        return lambdaFunctionVersion;
    }

    @Provides
    @Singleton
    @Named(NAME_LAMBDA_INITIALIZATION_TYPE)
    String providesLambdaInitializationType() {
        return lambdaInitializationType;
    }

    @Provides
    @Singleton
    @Named(NAME_SSM_DATABASE_URI_PARAMETER_KEY)
    String providesSsmDatabaseUriParameterKey() {
        return ssmDatabaseUriParameterKey;
    }

    @Provides
    @Singleton
    @Named(NAME_SSM_GOOGLE_CLIENT_ID_PARAMETER_KEY)
    String providesSsmGoogleClientIdParameterKey() {
        return ssmGoogleClientIdParameterKey;
    }

    @Provides
    @Singleton
    @Named(NAME_SSM_DISABLE_AUTH_PARAMETER_KEY)
    String providesSsmDisableAuthParameterKey() {
        return ssmDisableAuthParameterKey;
    }

    private static String getRequiredEnv(final Map<String, String> env, final String name) {
        final String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable '" + name + "' is not set");
        }
        return value;
    }
}

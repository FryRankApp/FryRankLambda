package com.fryrank.dagger;

import dagger.Module;
import dagger.Provides;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Fetches SSM-backed configuration once during init and exposes plain values to the graph.
 *
 * This avoids snapshotting a "used" SSM client (with warmed HTTP connections) into a SnapStart image.
 */
@Module
public final class SsmConfigModule {
    public static final String NAME_AUTH_DISABLED = "authDisabled";

    @Provides
    @Singleton
    @Named(AppModule.NAME_GOOGLE_CLIENT_ID)
    static String providesGoogleClientId(
            @Named(EnvironmentModule.NAME_REGION) final String region,
            @Named(EnvironmentModule.NAME_SSM_GOOGLE_CLIENT_ID_PARAMETER_KEY) final String googleClientIdParameterName
    ) {
        return getParameter(region, googleClientIdParameterName);
    }

    @Provides
    @Singleton
    @Named(NAME_AUTH_DISABLED)
    static boolean providesAuthDisabled(
            @Named(EnvironmentModule.NAME_REGION) final String region,
            @Named(EnvironmentModule.NAME_SSM_DISABLE_AUTH_PARAMETER_KEY) final String disableAuthParameterName
    ) {
        return "true".equals(getParameter(region, disableAuthParameterName));
    }

    private static String getParameter(final String region, final String parameterName) {
        try (SsmClient ssmClient = SsmClient.builder()
                .region(Region.of(region))
                .build()) {
            final GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();
            final GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            return parameterResponse.parameter().value();
        }
    }
}

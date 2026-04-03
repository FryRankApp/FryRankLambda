package com.fryrank.util;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.SsmException;

import javax.inject.Inject;
import javax.inject.Named;

import static com.fryrank.dagger.EnvironmentModule.NAME_SSM_DATABASE_URI_PARAMETER_KEY;
import static com.fryrank.dagger.EnvironmentModule.NAME_SSM_DISABLE_AUTH_PARAMETER_KEY;
import static com.fryrank.dagger.EnvironmentModule.NAME_SSM_GOOGLE_CLIENT_ID_PARAMETER_KEY;

@Log4j2
public class SSMParameterStore {

    private final SsmClient ssmClient;
    private final String ssmDatabaseUriParameterKey;
    private final String ssmGoogleClientIdParameterKey;
    private final String ssmDisableAuthParameterKey;

    @Inject
    public SSMParameterStore(
            final SsmClient ssmClient,
            @Named(NAME_SSM_DATABASE_URI_PARAMETER_KEY) final String ssmDatabaseUriParameterKey,
            @Named(NAME_SSM_GOOGLE_CLIENT_ID_PARAMETER_KEY) final String ssmGoogleClientIdParameterKey,
            @Named(NAME_SSM_DISABLE_AUTH_PARAMETER_KEY) final String ssmDisableAuthParameterKey
    ) {
        this.ssmClient = ssmClient;
        this.ssmDatabaseUriParameterKey = ssmDatabaseUriParameterKey;
        this.ssmGoogleClientIdParameterKey = ssmGoogleClientIdParameterKey;
        this.ssmDisableAuthParameterKey = ssmDisableAuthParameterKey;
    }

    public String getDatabaseUriFromSSM() {
        return getParameterFromSSM(ssmDatabaseUriParameterKey);
    }

    public String getGoogleClientIdFromSSM() {
        return getParameterFromSSM(ssmGoogleClientIdParameterKey);
    }

    public String getDisableAuthFromSSM() {
        return getParameterFromSSM(ssmDisableAuthParameterKey);
    }

    private String getParameterFromSSM(String ssmParameterName) {
        try {
            final GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name(ssmParameterName)
                    .withDecryption(true)
                    .build();

            final GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            log.info("Parameter {} retrieved from SSM Parameter Store successfully", ssmParameterName);
            return parameterResponse.parameter().value();
        } catch (SsmException e) {
            log.error("Error retrieving parameter {} from SSM Parameter Store", ssmParameterName, e);
            throw new IllegalStateException("Failed to retrieve parameter " + ssmParameterName + " from SSM Parameter Store", e);
        }
    }
} 

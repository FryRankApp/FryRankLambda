package com.fryrank.util;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.SsmException;

import javax.inject.Inject;

import static com.fryrank.Constants.DATABASE_URI_PARAMETER_NAME_ENV_VAR;
import static com.fryrank.Constants.GOOGLE_CLIENT_ID_PARAMETER_NAME_ENV_VAR;
import static com.fryrank.Constants.SSM_DISABLE_AUTH_PARAMETER_NAME_ENV_VAR;

@Log4j2
public class SSMParameterStore {

    private final SsmClient ssmClient;

    @Inject
    public SSMParameterStore(final SsmClient ssmClient) {
        this.ssmClient = ssmClient;
    }

    public String getDatabaseUriFromSSM() {
        return getParameterFromSSM(DATABASE_URI_PARAMETER_NAME_ENV_VAR);
    }

    public String getGoogleClientIdFromSSM() {
        return getParameterFromSSM(GOOGLE_CLIENT_ID_PARAMETER_NAME_ENV_VAR);
    }

    public String getDisableAuthFromSSM() {
        return getParameterFromSSM(SSM_DISABLE_AUTH_PARAMETER_NAME_ENV_VAR);
    }

    private String getParameterFromSSM(String parameterName) {
        try {
            final GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name(System.getenv(parameterName))
                    .withDecryption(true)
                    .build();

            final GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            log.info("Parameter {} retrieved from SSM Parameter Store successfully", parameterName);
            return parameterResponse.parameter().value();
        } catch (SsmException e) {
            log.error("Error retrieving parameter {} from SSM Parameter Store", parameterName, e);
            throw new IllegalStateException("Failed to retrieve parameter " + parameterName + " from SSM Parameter Store", e);
        }
    }
} 

package com.fryrank.util;

import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.SsmException;

import static com.fryrank.Constants.DATABASE_URI_PARAMETER_NAME_ENV_VAR;

@Log4j2
public class SSMParameterStore {
    
    public static String getDatabaseUriFromSSM() {
        try (SsmClient ssmClient = SsmClient.create()) {
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                .name(System.getenv(DATABASE_URI_PARAMETER_NAME_ENV_VAR))
                .withDecryption(true)
                .build();

            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            log.info("Database URI retrieved from SSM Parameter successfully");
            return parameterResponse.parameter().value();
        } catch (SsmException e) {
            log.error("Error retrieving database URI from SSM Parameter Store", e);
            throw new IllegalStateException("Failed to retrieve database URI from SSM Parameter Store", e);
        }
    }
} 
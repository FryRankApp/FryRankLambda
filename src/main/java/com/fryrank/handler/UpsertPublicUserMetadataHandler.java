package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dagger.Dependencies;
import com.fryrank.domain.UserMetadataDomain;
import com.fryrank.model.PublicUserMetadata;
import com.fryrank.model.PublicUserMetadataOutput;
import com.fryrank.util.APIGatewayResponseBuilder;
import com.fryrank.validator.APIGatewayRequestValidator;
import com.google.gson.Gson;
import lombok.extern.log4j.Log4j2;

import static com.fryrank.util.HeaderUtils.createCorsHeaders;

@Log4j2
public class UpsertPublicUserMetadataHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final UserMetadataDomain userMetadataDomain;
    private final APIGatewayRequestValidator requestValidator;
    private final Gson gson;

    public UpsertPublicUserMetadataHandler() {
        final var component = Dependencies.appComponent();
        userMetadataDomain = component.userMetadataDomain();
        requestValidator = component.apiGatewayRequestValidator();
        gson = component.gson();
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        log.info("Handling request: {}", input);

        final String handlerName = getClass().getSimpleName();
        return APIGatewayResponseBuilder.handleRequest(handlerName, input, () -> {
            requestValidator.validateRequest(handlerName, input);

            final PublicUserMetadata userMetadata = gson.fromJson(input.getBody(), PublicUserMetadata.class);
            final PublicUserMetadataOutput output = userMetadataDomain.upsertPublicUserMetadata(userMetadata);

            log.info("Request processed successfully");
            return APIGatewayResponseBuilder.buildSuccessResponse(output, createCorsHeaders(input));
        });
    }
}

package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dagger.Dependencies;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.ToggleReactionRequest;
import com.fryrank.model.ToggleReactionResult;
import com.fryrank.model.exceptions.AuthorizationDisabledException;
import com.fryrank.model.exceptions.NotAuthorizedException;
import com.fryrank.util.APIGatewayResponseBuilder;
import com.fryrank.util.Authorizer;
import com.fryrank.util.HeaderUtils;
import com.fryrank.validator.APIGatewayRequestValidator;
import com.google.gson.Gson;
import lombok.extern.log4j.Log4j2;

import static com.fryrank.util.HeaderUtils.createCorsHeaders;

@Log4j2
public class ToggleReactionHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final ReviewDomain reviewDomain;
    private final APIGatewayRequestValidator requestValidator;
    private final Authorizer authorizer;

    public ToggleReactionHandler() {
        final var component = Dependencies.appComponent();
        reviewDomain = component.reviewDomain();
        requestValidator = component.apiGatewayRequestValidator();
        authorizer = component.authorizer();
    }

    public ToggleReactionHandler(
            ReviewDomain reviewDomain,
            APIGatewayRequestValidator requestValidator,
            Authorizer authorizer
    ) {
        this.reviewDomain = reviewDomain;
        this.requestValidator = requestValidator;
        this.authorizer = authorizer;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        log.info("Handling request: {}", input);

        final String handlerName = getClass().getSimpleName();
        return APIGatewayResponseBuilder.handleRequest(handlerName, input, () -> {
            requestValidator.validateRequest(handlerName, input);

            final ToggleReactionRequest request = new Gson().fromJson(input.getBody(), ToggleReactionRequest.class);
            String viewerAccountId = null;
            try {
                final String token = HeaderUtils.extractBearerToken(input);
                viewerAccountId = authorizer.authorizeAndGetAccountId(token);
            } catch (NotAuthorizedException e) {
                return APIGatewayResponseBuilder.buildErrorResponse(401, e.getMessage(), createCorsHeaders(input));
            } catch (AuthorizationDisabledException e) {
                // Local sandbox/dev fallback when auth is disabled.
                viewerAccountId = request.accountId();
                log.info("Authorization disabled; using request accountId as viewer accountId");
            }

            final ToggleReactionResult output = reviewDomain.toggleReaction(viewerAccountId, request);

            log.info("Request processed successfully");
            return APIGatewayResponseBuilder.buildSuccessResponse(output, createCorsHeaders(input));
        });
    }
}

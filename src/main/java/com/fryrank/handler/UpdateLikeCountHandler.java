package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.UpdateLikeCountRequest;
import com.fryrank.model.exceptions.AuthorizationDisabledException;
import com.fryrank.model.exceptions.NotAuthorizedException;
import com.fryrank.util.APIGatewayResponseBuilder;
import com.fryrank.util.Authorizer;
import com.fryrank.util.HeaderUtils;
import com.fryrank.validator.APIGatewayRequestValidator;
import com.fryrank.validator.UpdateLikeCountRequestValidator;
import com.fryrank.validator.ValidatorUtils;
import com.google.gson.Gson;
import lombok.extern.log4j.Log4j2;

import static com.fryrank.Constants.UPDATE_LIKE_COUNT_REQUEST_VALIDATOR_ERRORS_OBJECT_NAME;
import static com.fryrank.util.HeaderUtils.createCorsHeaders;

@Log4j2
public class UpdateLikeCountHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final ReviewDALImpl reviewDAL;
    private final ReviewDomain reviewDomain;
    private final APIGatewayRequestValidator requestValidator;
    private final UpdateLikeCountRequestValidator updateLikeCountRequestValidator;
    private final Authorizer authorizer;

    public UpdateLikeCountHandler() {
        reviewDAL = new ReviewDALImpl();
        reviewDomain = new ReviewDomain(reviewDAL);
        requestValidator = new APIGatewayRequestValidator();
        updateLikeCountRequestValidator = new UpdateLikeCountRequestValidator();
        authorizer = new Authorizer();
    }

    public UpdateLikeCountHandler(
            ReviewDALImpl reviewDAL,
            ReviewDomain reviewDomain,
            APIGatewayRequestValidator requestValidator,
            UpdateLikeCountRequestValidator updateLikeCountRequestValidator,
            Authorizer authorizer
    ) {
        this.reviewDAL = reviewDAL;
        this.reviewDomain = reviewDomain;
        this.requestValidator = requestValidator;
        this.updateLikeCountRequestValidator = updateLikeCountRequestValidator;
        this.authorizer = authorizer;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        log.info("Handling request: {}", input);

        final String handlerName = getClass().getSimpleName();
        return APIGatewayResponseBuilder.handleRequest(handlerName, input, () -> {
            requestValidator.validateRequest(handlerName, input);

            // Authorize
            try {
                final String token = HeaderUtils.extractBearerToken(input);
                authorizer.authorizeAndGetAccountId(token);
            } catch (NotAuthorizedException e) {
                return APIGatewayResponseBuilder.buildErrorResponse(401, e.getMessage(), createCorsHeaders(input));
            } catch (AuthorizationDisabledException e) {
                log.info("Authorization disabled, skipping auth for like update.");
            }

            final UpdateLikeCountRequest req = new Gson().fromJson(input.getBody(), UpdateLikeCountRequest.class);
            ValidatorUtils.validateAndThrow(req, UPDATE_LIKE_COUNT_REQUEST_VALIDATOR_ERRORS_OBJECT_NAME, updateLikeCountRequestValidator);

            reviewDomain.updateLikeCount(req.reviewId(), req.likeCount());

            log.info("Request processed successfully");
            return APIGatewayResponseBuilder.buildSuccessNoContentResponse(createCorsHeaders(input));
        });
    }
}

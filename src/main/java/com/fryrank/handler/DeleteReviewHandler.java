package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dagger.Dependencies;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.DeleteReviewRequest;
import com.fryrank.util.APIGatewayResponseBuilder;
import com.fryrank.validator.APIGatewayRequestValidator;
import com.fryrank.validator.DeleteReviewRequestValidator;
import com.fryrank.validator.ValidatorUtils;
import com.google.gson.Gson;
import lombok.extern.log4j.Log4j2;

import static com.fryrank.Constants.DELETE_REVIEW_REQUEST_VALIDATOR_ERRORS_OBJECT_NAME;
import static com.fryrank.util.HeaderUtils.createCorsHeaders;
@Log4j2
public class DeleteReviewHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private final ReviewDomain reviewDomain;
    private final APIGatewayRequestValidator requestValidator;
    private final DeleteReviewRequestValidator deleteReviewRequestValidator;
    private final Gson gson;
    
    public DeleteReviewHandler() {
        final var component = Dependencies.appComponent();
        reviewDomain = component.reviewDomain();
        requestValidator = component.apiGatewayRequestValidator();
        deleteReviewRequestValidator = component.deleteReviewRequestValidator();
        gson = component.gson();
    }

    public DeleteReviewHandler(ReviewDomain reviewDomain, APIGatewayRequestValidator requestValidator, DeleteReviewRequestValidator deleteReviewRequestValidator) {
        this.reviewDomain = reviewDomain;
        this.requestValidator = requestValidator;
        this.deleteReviewRequestValidator = deleteReviewRequestValidator;
        this.gson = new Gson();
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        log.info("Handling request: {}", input);

        final String handlerName = getClass().getSimpleName();
        return APIGatewayResponseBuilder.handleRequest(handlerName, input, () -> {
            requestValidator.validateRequest(handlerName, input);
            
            final DeleteReviewRequest reviewId = gson.fromJson(input.getBody(), DeleteReviewRequest.class);
            ValidatorUtils.validateAndThrow(reviewId, DELETE_REVIEW_REQUEST_VALIDATOR_ERRORS_OBJECT_NAME, deleteReviewRequestValidator);
            
            reviewDomain.deleteReview(reviewId);
            log.info("Request processed successfully");
            return APIGatewayResponseBuilder.buildSuccessNoContentResponse(createCorsHeaders(input));
        });
    }
}

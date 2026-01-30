package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.Review;
import com.fryrank.model.exceptions.NotAuthorizedException;
import com.fryrank.util.APIGatewayResponseBuilder;
import com.fryrank.util.Authorizer;
import com.fryrank.util.HeaderUtils;
import com.fryrank.validator.APIGatewayRequestValidator;
import com.fryrank.validator.ReviewValidator;
import com.fryrank.validator.ValidatorUtils;
import com.google.gson.Gson;
import lombok.extern.log4j.Log4j2;

import static com.fryrank.Constants.REVIEW_VALIDATOR_ERRORS_OBJECT_NAME;
import static com.fryrank.util.HeaderUtils.createCorsHeaders;

@Log4j2
public class AddNewReviewForRestaurantHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final ReviewDALImpl reviewDAL;
    private final ReviewDomain reviewDomain;
    private final APIGatewayRequestValidator requestValidator;
    private final ReviewValidator reviewValidator;
    private final Authorizer authorizer;

    public AddNewReviewForRestaurantHandler() {
        reviewDAL = new ReviewDALImpl();
        reviewDomain = new ReviewDomain(reviewDAL);
        requestValidator = new APIGatewayRequestValidator();
        reviewValidator = new ReviewValidator();
        authorizer = new Authorizer();
    }

    public AddNewReviewForRestaurantHandler(Authorizer authorizer) {
        this.reviewDAL = new ReviewDALImpl();
        this.reviewDomain = new ReviewDomain(reviewDAL);
        this.requestValidator = new APIGatewayRequestValidator();
        this.reviewValidator = new ReviewValidator();
        this.authorizer = authorizer;
    }

    // Constructor for testing with all dependencies injected
    public AddNewReviewForRestaurantHandler(ReviewDALImpl reviewDAL, ReviewDomain reviewDomain, APIGatewayRequestValidator requestValidator, ReviewValidator reviewValidator, Authorizer authorizer) {
        this.reviewDAL = reviewDAL;
        this.reviewDomain = reviewDomain;
        this.requestValidator = requestValidator;
        this.reviewValidator = reviewValidator;
        this.authorizer = authorizer;
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        log.info("Handling request: {}", input);
        
        final String handlerName = getClass().getSimpleName();
        return APIGatewayResponseBuilder.handleRequest(handlerName, input, () -> {
            requestValidator.validateRequest(handlerName, input);

            // Extract bearer token from authorization header and authorize
            try {
                final String token = HeaderUtils.extractBearerToken(input);
                authorizer.authorizeToken(token);
            } catch (NotAuthorizedException e) {
                return APIGatewayResponseBuilder.buildErrorResponse(401, e.getMessage());
            }

            final Review review = new Gson().fromJson(input.getBody(), Review.class);
            ValidatorUtils.validateAndThrow(review, REVIEW_VALIDATOR_ERRORS_OBJECT_NAME, reviewValidator);
            
            final Review output = reviewDomain.addNewReviewForRestaurant(review);

            log.info("Request processed successfully");
            return APIGatewayResponseBuilder.buildSuccessResponse(output, createCorsHeaders(input));
        });
    }
}
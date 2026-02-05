package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.DeleteReviewInfo;
import com.fryrank.util.APIGatewayResponseBuilder;
import com.fryrank.validator.APIGatewayRequestValidator;
import com.fryrank.validator.DeleteReviewInfoValidator;
import com.fryrank.validator.ValidatorUtils;
import com.google.gson.Gson;
import lombok.extern.log4j.Log4j2;

import static com.fryrank.Constants.DELETE_REVIEW_INFO_VALIDATOR_ERRORS_OBJECT_NAME;
import static com.fryrank.util.HeaderUtils.createCorsHeaders;
@Log4j2
public class DeleteExistingReviewForRestaurantHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private final ReviewDALImpl reviewDAL;
    private final ReviewDomain reviewDomain;
    private final APIGatewayRequestValidator requestValidator;
    private final DeleteReviewInfoValidator deleteReviewInfoValidator;
    
    public DeleteExistingReviewForRestaurantHandler() {
        reviewDAL = new ReviewDALImpl();
        reviewDomain = new ReviewDomain(reviewDAL);
        requestValidator = new APIGatewayRequestValidator();
        deleteReviewInfoValidator = new DeleteReviewInfoValidator();
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        log.info("Handling request: {}", input);

        final String handlerName = getClass().getSimpleName();
            return APIGatewayResponseBuilder.handleRequest(handlerName, input, () -> {
                requestValidator.validateRequest(handlerName, input);
                
                final DeleteReviewInfo reviewId = new Gson().fromJson(input.getBody(), DeleteReviewInfo.class);
                ValidatorUtils.validateAndThrow(reviewId, DELETE_REVIEW_INFO_VALIDATOR_ERRORS_OBJECT_NAME, deleteReviewInfoValidator);
                
                final boolean deleted = reviewDomain.deleteExistingReviewForRestaurant(reviewId);
                
                if (deleted){
                    log.info("Request processed successfully");
                    return APIGatewayResponseBuilder.buildSuccessNoContentResponse(createCorsHeaders(input));
                } else {
                    return APIGatewayResponseBuilder.buildErrorResponse(404, "Review not found in database.");
                }
            
        });
    }
}

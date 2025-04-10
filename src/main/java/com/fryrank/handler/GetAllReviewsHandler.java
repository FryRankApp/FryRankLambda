package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.GetAllReviewsOutput;
import lombok.extern.log4j.Log4j2;
import static com.fryrank.Constants.RESTAURANT_ID_QUERY_PARAM;
import static com.fryrank.Constants.ACCOUNT_ID_QUERY_PARAM;

@Log4j2
public class GetAllReviewsHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final ReviewDALImpl reviewDAL;
    private final ReviewDomain reviewDomain;

    public GetAllReviewsHandler() {
        this.reviewDAL = new ReviewDALImpl();
        this.reviewDomain = new ReviewDomain(reviewDAL);
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        try {
            log.info("Handling request: {}", input);
            
            // Using pattern matching for instanceof (Java 21 feature)
            if (input.getQueryStringParameters() == null) {
                throw new IllegalArgumentException("Query parameters are required");
            }

            GetAllReviewsOutput output = reviewDomain.getAllReviews(
                    input.getQueryStringParameters().getOrDefault(RESTAURANT_ID_QUERY_PARAM, null),
                    input.getQueryStringParameters().getOrDefault(ACCOUNT_ID_QUERY_PARAM, null));
            
            APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
            response.setStatusCode(200);
            response.setBody(output.toString());
            
            log.info("Request processed successfully");
            return response;
        } catch (Exception e) {
            log.error("Error processing request", e);
            APIGatewayV2HTTPResponse errorResponse = new APIGatewayV2HTTPResponse();
            errorResponse.setStatusCode(500);
            errorResponse.setBody("Internal Server Error: " + e.getMessage());
            return errorResponse;
        }
    }
}
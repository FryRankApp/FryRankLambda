package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.GetAllReviewsOutput;
import lombok.extern.log4j.Log4j2;
import static com.fryrank.Constants.COUNT_QUERY_PARAM;

@Log4j2
public class GetTopReviewsHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final ReviewDALImpl reviewDAL;
    private final ReviewDomain reviewDomain;

    public GetTopReviewsHandler() {
        this.reviewDAL = new ReviewDALImpl();
        this.reviewDomain = new ReviewDomain(reviewDAL);
    }
    
    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        try {
            log.info("Handling request: {}", input);

            if (input.getQueryStringParameters() == null || !input.getQueryStringParameters().containsKey(COUNT_QUERY_PARAM)) {
                throw new IllegalArgumentException("Count query parameter is required");
            }

            GetAllReviewsOutput output = reviewDomain.getTopReviews(
                    Integer.parseInt(input.getQueryStringParameters().get(COUNT_QUERY_PARAM)));

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

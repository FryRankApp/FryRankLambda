package com.fryrank.handler;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dal.ReviewDAL;
import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.GetAggregateReviewInformationOutput;

import lombok.extern.log4j.Log4j2;
import static com.fryrank.Constants.IDS_QUERY_PARAM;
import static com.fryrank.Constants.INCLUDE_RATING_QUERY_PARAM;

@Log4j2
public class GetAggregateReviewInformationHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    // looking at the other two handlers, write this class to handle the request for the aggregate review information.

    private final ReviewDomain reviewDomain;
    private final ReviewDAL reviewDAL;

    public GetAggregateReviewInformationHandler() {
        this.reviewDAL = new ReviewDALImpl();
        this.reviewDomain = new ReviewDomain(reviewDAL);
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        try {
            log.info("Handling request: {}", input);

            if (input.getQueryStringParameters() == null || input.getQueryStringParameters().getOrDefault(IDS_QUERY_PARAM, null) == null) {
                throw new IllegalArgumentException("Ids are required");
            }

            GetAggregateReviewInformationOutput output = reviewDomain.getAggregateReviewInformationForRestaurants(
                input.getQueryStringParameters().getOrDefault(IDS_QUERY_PARAM, null),
                Boolean.parseBoolean(input.getQueryStringParameters().getOrDefault(INCLUDE_RATING_QUERY_PARAM, "false"))
            );

            APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
            response.setStatusCode(200);
            response.setBody(output.toString());
            
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

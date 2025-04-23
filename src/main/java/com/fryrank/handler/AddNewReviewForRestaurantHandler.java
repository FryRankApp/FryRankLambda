package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.Review;
import com.google.gson.Gson;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class AddNewReviewForRestaurantHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final ReviewDALImpl reviewDAL;
    private final ReviewDomain reviewDomain;

    public AddNewReviewForRestaurantHandler() {
        this.reviewDAL = new ReviewDALImpl();
        this.reviewDomain = new ReviewDomain(reviewDAL);
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        try {
            log.info("Handling request: {}", input);
            
            if (input.getBody() == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            Review review = new Gson().fromJson(input.getBody(), Review.class);

            Review output = reviewDomain.addNewReviewForRestaurant(review);


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
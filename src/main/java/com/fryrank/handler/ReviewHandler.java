package com.fryrank.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.model.GetAllReviewsOutput;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

@Log4j2
public class ReviewHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    MongoTemplate mongoTemplate;
    ReviewDALImpl reviewDAL;
    ReviewDomain reviewDomain;

    public ReviewHandler() {
        log.info("Initializing ReviewHandler");
        MongoDatabaseFactory mongoDatabaseFactory = new SimpleMongoClientDatabaseFactory("some random string");
        this.mongoTemplate = new MongoTemplate(mongoDatabaseFactory);
        this.reviewDAL = new ReviewDALImpl(mongoTemplate);
        this.reviewDomain = new ReviewDomain(reviewDAL);
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {

        log.info("Handling request: {}", input);
        GetAllReviewsOutput output = reviewDomain.getAllReviews(
                input.getQueryStringParameters().getOrDefault("restaurantId", null),
                input.getQueryStringParameters().getOrDefault("accountId", null));
        APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
        response.setBody(output.toString());

        log.info("Returning response: {}", response);

        return response;
    }
}
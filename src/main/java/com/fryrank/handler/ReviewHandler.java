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
import com.mongodb.MongoClientSettings;
import com.mongodb.ConnectionString;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import java.util.concurrent.TimeUnit;

@Log4j2
public class ReviewHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    MongoTemplate mongoTemplate;
    ReviewDALImpl reviewDAL;
    ReviewDomain reviewDomain;

    public ReviewHandler() {
        try {
            log.info("Initializing ReviewHandler");
            String mongoUri = System.getenv("DATABASE_URI");
            log.info("DATABASE_URI environment variable: {}", mongoUri != null ? "is set" : "is null");
            
            if (mongoUri == null || mongoUri.isEmpty()) {
                throw new IllegalStateException("DATABASE_URI environment variable is not set");
            }

            // Parse the connection string to validate format
            ConnectionString connectionString;
            try {
                connectionString = new ConnectionString(mongoUri);
                log.info("MongoDB connection string validated. Database: {}", connectionString.getDatabase());
            } catch (IllegalArgumentException e) {
                log.error("Invalid MongoDB connection string format", e);
                throw new IllegalStateException("Invalid MongoDB connection string format", e);
            }

            // Configure MongoDB client with explicit settings
            MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .applyToSocketSettings(builder -> 
                    builder.connectTimeout(10, TimeUnit.SECONDS)
                           .readTimeout(10, TimeUnit.SECONDS))
                .build();

            log.info("Creating MongoDB client with custom settings");
            MongoClient mongoClient = MongoClients.create(settings);
            
            log.info("Creating MongoTemplate");
            this.mongoTemplate = new MongoTemplate(mongoClient, connectionString.getDatabase());
            
            // Test the connection
            try {
                log.info("Testing MongoDB connection with ping command");
                Document pingResult = this.mongoTemplate.getDb().runCommand(new Document("ping", 1));
                log.info("Successfully connected to MongoDB. Ping result: {}", pingResult);
            } catch (MongoException e) {
                log.error("Failed to connect to MongoDB: {}", e.getMessage(), e);
                if (e.getMessage().contains("Authentication failed")) {
                    throw new IllegalStateException("MongoDB authentication failed. Please check username and password.", e);
                } else if (e.getMessage().contains("Server selection timed out")) {
                    throw new IllegalStateException("MongoDB server selection timed out. Please check network connectivity and MongoDB Atlas status.", e);
                } else {
                    throw new IllegalStateException("Failed to connect to MongoDB: " + e.getMessage(), e);
                }
            }
            
            log.info("Creating ReviewDALImpl");
            this.reviewDAL = new ReviewDALImpl(mongoTemplate);
            
            log.info("Creating ReviewDomain");
            this.reviewDomain = new ReviewDomain(reviewDAL);
            
            log.info("ReviewHandler initialization completed successfully");
        } catch (Exception e) {
            log.error("Error during ReviewHandler initialization", e);
            throw e;
        }
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
        try {
            log.info("Handling request: {}", input);
            
            if (input.getQueryStringParameters() == null) {
                throw new IllegalArgumentException("Query parameters are required");
            }

            GetAllReviewsOutput output = reviewDomain.getAllReviews(
                    input.getQueryStringParameters().getOrDefault("restaurantId", null),
                    input.getQueryStringParameters().getOrDefault("accountId", null));
            
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
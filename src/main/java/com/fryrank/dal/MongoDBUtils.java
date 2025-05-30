package com.fryrank.dal;

import com.fryrank.util.SSMParameterStore;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Utility class for MongoDB operations and configuration.
 */
public class MongoDBUtils {

    /**
     * Creates a MongoTemplate with standardized settings.
     * 
     * @return Configured MongoTemplate instance
     * @throws IllegalStateException if database URI cannot be retrieved
     */
    public static MongoTemplate createMongoTemplate() {
        String databaseUri = SSMParameterStore.getDatabaseUriFromSSM();
        
        if (databaseUri == null || databaseUri.isEmpty()) {
            throw new IllegalStateException("Database URI could not be retrieved from SSM Parameter Store");
        }

        ConnectionString connectionString = new ConnectionString(databaseUri);

        // Configure MongoDB client with explicit settings
        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .applyToSocketSettings(builder -> 
                builder.connectTimeout(10, TimeUnit.SECONDS)
                       .readTimeout(10, TimeUnit.SECONDS))
            .build();

        MongoClient mongoClient = MongoClients.create(settings);
        
        return new MongoTemplate(mongoClient, connectionString.getDatabase());
    }
}

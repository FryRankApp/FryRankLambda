package com.fryrank.dagger;

import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.dal.UserMetadataDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.domain.UserMetadataDomain;
import com.fryrank.util.Authorizer;
import com.fryrank.util.SSMParameterStore;
import com.fryrank.validator.APIGatewayRequestValidator;
import com.fryrank.validator.DeleteReviewRequestValidator;
import com.fryrank.validator.ReviewValidator;
import com.fryrank.validator.UserMetadataValidator;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;
import dagger.Module;
import dagger.Provides;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collections;

@Module
public class AppModule {
    public static final String NAME_GOOGLE_CLIENT_ID = "googleClientId";
    public static final String NAME_AUTH_DISABLED = "authDisabled";

    @Provides
    @Singleton
    static Gson gson() {
        return new Gson();
    }

    @Provides
    @Singleton
    static APIGatewayRequestValidator apiGatewayRequestValidator() {
        return new APIGatewayRequestValidator();
    }

    @Provides
    @Singleton
    static ReviewValidator reviewValidator() {
        return new ReviewValidator();
    }

    @Provides
    @Singleton
    static UserMetadataValidator userMetadataValidator() {
        return new UserMetadataValidator();
    }

    @Provides
    @Singleton
    static DeleteReviewRequestValidator deleteReviewRequestValidator() {
        return new DeleteReviewRequestValidator();
    }

    @Provides
    @Singleton
    static ReviewDALImpl reviewDAL(DynamoDbClient dynamoDbClient) {
        return new ReviewDALImpl(dynamoDbClient);
    }

    @Provides
    @Singleton
    static UserMetadataDALImpl userMetadataDAL(DynamoDbClient dynamoDbClient) {
        return new UserMetadataDALImpl(dynamoDbClient);
    }

    @Provides
    @Singleton
    static ReviewDomain reviewDomain(ReviewDALImpl reviewDAL, ReviewValidator reviewValidator) {
        return new ReviewDomain(reviewDAL, reviewValidator);
    }

    @Provides
    @Singleton
    static UserMetadataDomain userMetadataDomain(UserMetadataDALImpl userMetadataDAL, UserMetadataValidator userMetadataValidator) {
        return new UserMetadataDomain(userMetadataDAL, userMetadataValidator);
    }

    @Provides
    @Singleton
    static HttpTransport httpTransport() {
        return new NetHttpTransport();
    }

    @Provides
    @Singleton
    static JsonFactory jsonFactory() {
        return GsonFactory.getDefaultInstance();
    }

    @Provides
    @Singleton
    @Named(NAME_GOOGLE_CLIENT_ID)
    static String googleClientId(SSMParameterStore parameterStore) {
        return parameterStore.getGoogleClientIdFromSSM();
    }

    @Provides
    @Singleton
    @Named(NAME_AUTH_DISABLED)
    static boolean authDisabled(SSMParameterStore parameterStore) {
        return "true".equals(parameterStore.getDisableAuthFromSSM());
    }

    @Provides
    @Singleton
    static GoogleIdTokenVerifier googleIdTokenVerifier(HttpTransport transport, JsonFactory jsonFactory, @Named(NAME_GOOGLE_CLIENT_ID) String googleClientId) {
        return new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    @Provides
    @Singleton
    static Authorizer authorizer(GoogleIdTokenVerifier verifier, @Named(NAME_AUTH_DISABLED) boolean authDisabled) {
        return new Authorizer(verifier, authDisabled);
    }
}

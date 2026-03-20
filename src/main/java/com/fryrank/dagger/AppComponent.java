package com.fryrank.dagger;

import com.fryrank.dal.ReviewDALImpl;
import com.fryrank.dal.UserMetadataDALImpl;
import com.fryrank.domain.ReviewDomain;
import com.fryrank.domain.UserMetadataDomain;
import com.fryrank.util.Authorizer;
import com.fryrank.validator.APIGatewayRequestValidator;
import com.fryrank.validator.DeleteReviewRequestValidator;
import com.fryrank.validator.ReviewValidator;
import com.fryrank.validator.UserMetadataValidator;
import com.google.gson.Gson;
import dagger.Component;

import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
@Component(modules = {AwsModule.class, AppModule.class})
public interface AppComponent {
    ReviewDALImpl reviewDAL();

    UserMetadataDALImpl userMetadataDAL();

    ReviewDomain reviewDomain();

    UserMetadataDomain userMetadataDomain();

    APIGatewayRequestValidator apiGatewayRequestValidator();

    ReviewValidator reviewValidator();

    UserMetadataValidator userMetadataValidator();

    DeleteReviewRequestValidator deleteReviewRequestValidator();

    Authorizer authorizer();

    Gson gson();

    @Named(AppModule.NAME_AUTH_DISABLED)
    boolean authDisabled();
}

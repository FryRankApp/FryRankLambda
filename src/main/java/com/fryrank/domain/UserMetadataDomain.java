package com.fryrank.domain;

import static com.fryrank.Constants.USER_METADATA_VALIDATOR_ERRORS_OBJECT_NAME;

import com.fryrank.dal.UserMetadataDAL;
import com.fryrank.model.PublicUserMetadata;
import com.fryrank.model.PublicUserMetadataOutput;
import com.fryrank.validator.UserMetadataValidator;
import com.fryrank.validator.ValidatorException;
import com.fryrank.validator.ValidatorUtils;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

@Log4j2
@AllArgsConstructor
public class UserMetadataDomain {

    private final UserMetadataDAL userMetadataDAL;

    public PublicUserMetadataOutput getPublicUserMetadata(@NonNull final String accountId) {
        log.info("Getting public user metadata for accountId: {}", accountId);
        return userMetadataDAL.getPublicUserMetadataForAccountId(accountId);
    }

    public PublicUserMetadataOutput putPublicUserMetadata(@NonNull final String accountId, @NonNull final String defaultUserName) {
        log.info("Putting public user metadata for accountId: {} with default username: {}", accountId, defaultUserName);
        return userMetadataDAL.putPublicUserMetadataForAccountId(accountId, defaultUserName);
    }

    public PublicUserMetadataOutput upsertPublicUserMetadata(@NonNull final PublicUserMetadata userMetadata) throws ValidatorException {
        log.info("Upserting public user metadata for accountId: {}", userMetadata.getAccountId());
        
        ValidatorUtils.validateAndThrow(userMetadata, USER_METADATA_VALIDATOR_ERRORS_OBJECT_NAME, new UserMetadataValidator());
        
        return userMetadataDAL.upsertPublicUserMetadata(userMetadata);
    }
}

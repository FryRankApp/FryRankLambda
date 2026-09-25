package com.fryrank.domain;

import static com.fryrank.Constants.USER_METADATA_VALIDATOR_ERRORS_OBJECT_NAME;

import com.fryrank.dal.UserMetadataDAL;
import com.fryrank.model.PublicUserMetadata;
import com.fryrank.model.PublicUserMetadataOutput;
import com.fryrank.validator.UserMetadataValidator;
import com.fryrank.validator.ValidatorException;
import com.fryrank.validator.ValidatorUtils;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class UserMetadataDomain {

    private final UserMetadataDAL userMetadataDAL;
    private final UserMetadataValidator userMetadataValidator;

    public UserMetadataDomain(final UserMetadataDAL userMetadataDAL, final UserMetadataValidator userMetadataValidator) {
        this.userMetadataDAL = userMetadataDAL;
        this.userMetadataValidator = userMetadataValidator;
    }

    public PublicUserMetadataOutput getPublicUserMetadata(@NonNull final String accountId) {
        log.info("Getting public user metadata for accountId: {}", accountId);
        return userMetadataDAL.getPublicUserMetadataForAccountId(accountId);
    }

    public PublicUserMetadataOutput putPublicUserMetadata(final String accountId, final String username) throws ValidatorException {
        log.info("Putting public user metadata for accountId: {} with username: {}", accountId, username);

        final PublicUserMetadata userMetadata = new PublicUserMetadata(accountId, username);
        ValidatorUtils.validateAndThrow(userMetadata, USER_METADATA_VALIDATOR_ERRORS_OBJECT_NAME, userMetadataValidator);

        return userMetadataDAL.putPublicUserMetadata(userMetadata);
    }
}

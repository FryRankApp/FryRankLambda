package com.fryrank.dal;

import com.fryrank.model.PublicUserMetadata;
import com.fryrank.model.PublicUserMetadataOutput;

public interface UserMetadataDAL {
    PublicUserMetadataOutput putPublicUserMetadataForAccountId(final String accountId, final String defaultUserName);

    PublicUserMetadataOutput getPublicUserMetadataForAccountId(final String accountId);

    PublicUserMetadataOutput upsertPublicUserMetadata(final PublicUserMetadata userMetadata);
}
package com.fryrank.dal;

import com.fryrank.model.PublicUserMetadata;
import com.fryrank.model.PublicUserMetadataOutput;

public interface UserMetadataDAL {
    PublicUserMetadataOutput putPublicUserMetadata(final PublicUserMetadata userMetadata);

    PublicUserMetadataOutput getPublicUserMetadataForAccountId(final String accountId);
}
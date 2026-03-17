package com.fryrank.dal;

import com.fryrank.model.PublicUserMetadata;
import com.fryrank.model.PublicUserMetadataOutput;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.fryrank.Constants.ACCOUNT_ID_KEY;

@Repository
@Log4j2
@AllArgsConstructor
public class UserMetadataDALImpl implements UserMetadataDAL {

    private final MongoTemplate mongoTemplate;

    public UserMetadataDALImpl() {
        this.mongoTemplate = MongoDBUtils.createMongoTemplate();
    }

    @Override
    public PublicUserMetadataOutput putPublicUserMetadataForAccountId(@NonNull final String accountId, @NonNull final String defaultUserName) {
        log.info("Putting public user metadata for accountId: {}", accountId);
        
        final Query query = new Query(Criteria.where(ACCOUNT_ID_KEY).is(accountId));
        final List<PublicUserMetadata> publicUserMetadata = mongoTemplate.find(query, PublicUserMetadata.class);
        
        if (!publicUserMetadata.isEmpty()) {
            if (publicUserMetadata.size() > 1) {
                log.warn("Multiple user metadata records found for accountId: {}. Using first record.", accountId);
            }
            return new PublicUserMetadataOutput(publicUserMetadata.get(0).getUsername());
        }
        
        PublicUserMetadata newUserMetadata = new PublicUserMetadata(accountId, defaultUserName);
        return upsertPublicUserMetadata(newUserMetadata);
    }

    @Override
    public PublicUserMetadataOutput getPublicUserMetadataForAccountId(@NonNull final String accountId) {
        log.info("Getting public user metadata for accountId: {}", accountId);
        
        final Query query = new Query(Criteria.where(ACCOUNT_ID_KEY).is(accountId));
        final List<PublicUserMetadata> publicUserMetadata = mongoTemplate.find(query, PublicUserMetadata.class);
        
        if (!publicUserMetadata.isEmpty()) {
            if (publicUserMetadata.size() > 1) {
                log.warn("Multiple user metadata records found for accountId: {}. Using first record.", accountId);
            }
            return new PublicUserMetadataOutput(publicUserMetadata.get(0).getUsername());
        }
        
        return new PublicUserMetadataOutput(null);
    }

    @Override
    public PublicUserMetadataOutput upsertPublicUserMetadata(@NonNull final PublicUserMetadata userMetadata) {
        log.info("Upserting public user metadata for accountId: {}", userMetadata.getAccountId());
        
        final Query query = new Query(Criteria.where("_id").is(userMetadata.getAccountId()));
        final FindAndReplaceOptions options = new FindAndReplaceOptions();
        options.upsert();
        options.returnNew();

        PublicUserMetadata mongodbRecord = mongoTemplate.findAndReplace(query, userMetadata, options);
        return new PublicUserMetadataOutput(mongodbRecord != null ? mongodbRecord.getUsername() : userMetadata.getUsername());
    }
}
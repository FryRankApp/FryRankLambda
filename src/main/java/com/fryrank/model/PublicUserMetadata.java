package com.fryrank.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("user-metadata")
@Data
public class PublicUserMetadata {
    @Id
    private final String accountId;

    private final String username;
}
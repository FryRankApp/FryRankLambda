## FryRankLambda ##

A Lambda package hosting all the backend logic for the Fry Rank project.

### Unified Handler (Single Lambda Entrypoint)
This repo now includes a unified handler: `com.fryrank.handler.ServiceRequestHandler`.

It routes requests based on API Gateway HTTP API `routeKey` (preferred) or method+path:
- `GET /api/reviews` -> list reviews (requires `restaurantId` or `accountId` query param; optional `limit`, `cursor`)
- `POST /api/reviews` -> create review (JSON body `Review`, bearer token authorization if enabled)
- `DELETE /api/reviews` -> delete review (JSON body `DeleteReviewRequest`)
- `GET /api/reviews/aggregateInformation` -> aggregate info (requires `ids` query param; optional `includeRating`)
- `GET /api/reviews/recent` (or `/api/reviews/recentReviews`) -> recent reviews (requires `count` query param)
- `GET /api/publicUserMetadata` (or `/api/userMetadata`) -> get metadata (requires `accountId` query param)
- `PUT /api/publicUserMetadata` (or `/api/userMetadata`) -> put default metadata (requires `accountId`, `username` query params)
- `POST /api/publicUserMetadata` (or `/api/userMetadata`) -> upsert metadata (JSON body `PublicUserMetadata`)

### Prerequisites
1) Install: `python3 -m pip install boto3`  
2) Set AWS region (FryRank sandbox): `aws configure set region us-west-2`

### Testing ###
To build the package, run the following command, either in the terminal or with the IntelliJ Gradle plugin:
```bash
gradle build
```

To build and deploy it to your sandbox account in one step, run the following command:
```bash
./deploy.sh
```

Note: If you are on Windows, use the deploy.bat file instead. The command is `.\deploy.bat`.

Troubleshooting: If you come across a "NoRegionError", you may want to run "aws configure" via aws cli 
to set the region manually from there

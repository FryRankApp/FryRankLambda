# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew build

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.fryrank.dal.ReviewDALImplTest"

# Deploy (requires AWS CLI configured for us-west-2)
.\deploy.bat   # Windows
./deploy.sh    # Linux/Mac
```

Prerequisites for deploy: Python 3 with boto3, AWS CLI configured for region `us-west-2`.

If you encounter `NoRegionError`, set your AWS region: `aws configure set region us-west-2`.

## Architecture

FryRankLambda is an AWS Lambda backend for a restaurant french-fry rating platform. Each feature is a separate Lambda function exposed through API Gateway V2 (HTTP API).

**Request flow:** API Gateway → Handler → Domain → DAL → MongoDB

### Layers

- **`handler/`** — Lambda entry points implementing `RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse>`. One class per Lambda function (GetAllReviews, AddNewReview, DeleteReview, GetAggregateReviewInformation, GetRecentReviews, GetPublicUserMetadata, PutPublicUserMetadata, UpsertPublicUserMetadata).

- **`domain/`** — Business logic orchestration (`ReviewDomain`, `UserMetadataDomain`). Handlers delegate here; domain classes call DAL.

- **`dal/`** — Data access layer with interfaces (`ReviewDAL`, `UserMetadataDAL`) and MongoDB implementations (`ReviewDALImpl`, `UserMetadataDALImpl`). Uses Spring Data MongoDB with aggregation pipelines for complex queries.

- **`model/`** — Request/response DTOs and MongoDB entity classes. `Review` maps to the `"review"` collection. Pagination uses cursor-based approach via `GetAllReviewsOutput` (includes `next_cursor`).

- **`util/`** — Cross-cutting utilities: `Authorizer` (Google OAuth 2.0 verification), `APIGatewayResponseBuilder` (consistent Lambda responses with CORS), `SSMParameterStore` (reads config from AWS SSM), `HeaderUtils` (CORS + bearer token extraction).

- **`validator/`** — Input validation classes that return structured error responses.

### Configuration

All runtime config is pulled from **AWS SSM Parameter Store** at Lambda cold start:
- MongoDB connection URI
- Google OAuth client ID
- `DISABLE_AUTH` flag (set to `true` to bypass Google auth for local/API Gateway testing)

CORS allowed origins are hardcoded in `Constants.java`.

### Authentication

Write operations (add/delete review, upsert user metadata) require a Google OAuth bearer token in the `Authorization` header. The `Authorizer` class verifies the token and extracts the Google account ID — this ID is used as the canonical user identifier stored in reviews, not any value passed from the frontend.

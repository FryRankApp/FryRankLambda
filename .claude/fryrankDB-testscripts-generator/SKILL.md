---
name: fryrankDB-testscripts-generator
description: Use this skill when the user wants to seed or wipe test data in FryRank's DynamoDB table, run seed_reviews.py or nuke_reviews.py, or manage dummy review data for a restaurant.
version: 1.1.0
---

# FryRank DB Test Scripts Generator

Scripts for managing test data in the FryRank DynamoDB backend. Located in `python/scripts/`.

---

## seed_reviews.py — Inject dummy reviews

`python/scripts/seed_reviews.py`

Inserts N dummy reviews for a restaurant into `fryrank-app-rankings` and creates/updates the `AGGREGATE` row.

```bash
python python/scripts/seed_reviews.py --restaurant-id <id> --count <n> [--region <region>]
```

**Arguments:**
- `--restaurant-id` (required) — restaurant ID to seed reviews for
- `--count` (required) — number of reviews to insert (must be > 0)
- `--region` (optional) — AWS region, defaults to `us-west-2`

**Key details:**
- Scores are random whole integers (1–10), stored as `Decimal`
- Timestamps are ISO 8601 UTC, spaced 1 hour apart going backwards from now (deterministic pagination order)
- `accountId` format: `seed-account-<12-char hex uuid>`
- `identifier` format: `REVIEW:<accountId>`
- `AGGREGATE` row tracks `reviewCount`, `totalScore`, `averageScore`
- Batch writer handles DynamoDB's 25-item chunk limit automatically

---

## nuke_reviews.py — Delete all reviews for a restaurant

`python/scripts/nuke_reviews.py`

Permanently deletes all `REVIEW:` items for a restaurant ID. Also deletes the `AGGREGATE` row unless `--keep-aggregate` is passed. Requires typing the restaurant ID to confirm.

```bash
python python/scripts/nuke_reviews.py --restaurant-id <id> [--keep-aggregate] [--region <region>]
```

**Arguments:**
- `--restaurant-id` (required) — restaurant ID to wipe
- `--keep-aggregate` (optional flag) — preserve the AGGREGATE row
- `--region` (optional) — AWS region, defaults to `us-west-2`

**Key details:**
- Paginates the full query so restaurants with >1MB of items are fully deleted
- Interactive confirmation: user must type the restaurant ID before deletion proceeds
- Uses batch writer for efficient bulk deletes

---

## Prerequisites (both scripts)

- Python 3 with `boto3` (`pip install boto3`)
- AWS CLI configured for `us-west-2` (or pass `--region`)
- DynamoDB table `fryrank-app-rankings` must exist in the target region

---

## When to Use This Skill

- User asks to seed/populate/generate test reviews in DynamoDB
- User wants to wipe/nuke/clear reviews for a restaurant
- User wants to reset a restaurant back to a clean state
- User wants to test pagination with a controlled number of reviews
- User asks how to run either script or what arguments they take

"""
seed_reviews.py - Inject dummy reviews into the FryRank DynamoDB backend for testing.

Usage:
    python seed_reviews.py --restaurant-id <id> --count <n> [--region <region>]

Each review gets a unique fake accountId, a random score (1.0-10.0), and a
timestamp spread evenly over the past <count> hours so pagination ordering is
deterministic and easy to reason about.

The script also creates/updates the AGGREGATE row for the restaurant so that
aggregate stats remain consistent.
"""

import argparse
import random
import uuid
from datetime import datetime, timedelta, timezone
from decimal import Decimal

import boto3
from boto3.dynamodb.conditions import Key

TABLE_NAME = "fryrank-app-rankings"
REVIEW_IDENTIFIER_PREFIX = "REVIEW:"
AGGREGATE_IDENTIFIER = "AGGREGATE"
IS_REVIEW_VALUE = "true"

TITLES = [
    "Crispy perfection",
    "Could be better",
    "Absolutely delicious",
    "Soggy disappointment",
    "Perfectly seasoned",
    "Thick and hearty",
    "Thin and crispy",
    "Too salty for me",
    "Golden and wonderful",
    "Just okay",
    "Best fries ever",
    "Needs more salt",
    "Fantastic crunch",
    "Greasy but tasty",
    "Fresh from the fryer",
]

BODIES = [
    "These fries had the perfect balance of crispy outside and fluffy inside.",
    "A bit too greasy for my taste, but the flavor was there.",
    "Seasoning was spot on. Would definitely order again.",
    "They came out lukewarm and a little limp. Disappointing.",
    "Thick-cut with a great crunch. Paired perfectly with the burger.",
    "Nothing special, just your standard fast-food fry.",
    "Incredibly crispy and light. One of the best I have had.",
    "Way too salty. Hard to eat more than a few.",
    "Fresh out of the fryer and absolutely perfect.",
    "Decent fries but nothing memorable about them.",
]


def generate_reviews(restaurant_id: str, count: int) -> list[dict]:
    """Generate `count` dummy review items for the given restaurant."""
    reviews = []
    now = datetime.now(timezone.utc)

    for i in range(count):
        account_id = f"seed-account-{uuid.uuid4().hex[:12]}"
        score = random.randint(1, 10)
        # Spread timestamps evenly over the past `count` hours (oldest first)
        iso_dt = (now - timedelta(hours=count - i)).strftime("%Y-%m-%dT%H:%M:%SZ")

        reviews.append({
            "restaurantId": restaurant_id,
            "identifier": REVIEW_IDENTIFIER_PREFIX + account_id,
            "accountId": account_id,
            "score": Decimal(str(score)),
            "title": random.choice(TITLES),
            "body": random.choice(BODIES),
            "isoDateTime": iso_dt,
            "isReview": IS_REVIEW_VALUE,
        })

    return reviews


def upsert_aggregate(table, restaurant_id: str, reviews: list[dict]):
    """Create or update the AGGREGATE row for the restaurant."""
    aggregate_key = {
        "restaurantId": restaurant_id,
        "identifier": AGGREGATE_IDENTIFIER,
    }

    # Fetch existing aggregate (if any)
    response = table.get_item(Key=aggregate_key)
    existing = response.get("Item")

    new_scores = [float(r["score"]) for r in reviews]
    new_count = len(new_scores)
    new_total = sum(new_scores)

    if existing:
        existing_count = int(existing.get("reviewCount", 0))
        existing_total = float(existing.get("totalScore", 0))
        final_count = existing_count + new_count
        final_total = existing_total + new_total
    else:
        final_count = new_count
        final_total = new_total

    final_avg = round(final_total / final_count, 10) if final_count > 0 else 0

    table.put_item(Item={
        **aggregate_key,
        "reviewCount": final_count,
        "totalScore": Decimal(str(round(final_total, 10))),
        "averageScore": Decimal(str(final_avg)),
    })

    print(f"Aggregate updated: restaurantId={restaurant_id}, "
          f"reviewCount={final_count}, averageScore={round(final_avg, 2)}")


def seed(restaurant_id: str, count: int, region: str):
    dynamodb = boto3.resource("dynamodb", region_name=region)
    table = dynamodb.Table(TABLE_NAME)

    print(f"Generating {count} reviews for restaurantId='{restaurant_id}' in region '{region}'...")
    reviews = generate_reviews(restaurant_id, count)

    # Batch-write reviews (DynamoDB batch_writer handles chunking at 25)
    with table.batch_writer() as batch:
        for review in reviews:
            batch.put_item(Item=review)

    print(f"Inserted {count} reviews into '{TABLE_NAME}'.")

    upsert_aggregate(table, restaurant_id, reviews)


def main():
    parser = argparse.ArgumentParser(description="Seed dummy reviews into FryRank DynamoDB.")
    parser.add_argument("--restaurant-id", required=True, help="The restaurant ID to seed reviews for.")
    parser.add_argument("--count", type=int, required=True, help="Number of reviews to insert.")
    parser.add_argument("--region", default="us-west-2", help="AWS region (default: us-west-2).")
    args = parser.parse_args()

    if args.count <= 0:
        parser.error("--count must be a positive integer.")

    seed(args.restaurant_id, args.count, args.region)


if __name__ == "__main__":
    main()

"""
nuke_reviews.py - Delete all reviews (and optionally the AGGREGATE row) for a restaurant ID.

Usage:
    python nuke_reviews.py --restaurant-id <id> [--keep-aggregate] [--region <region>]

Queries all items under the given restaurantId and batch-deletes every REVIEW: item.
Also deletes the AGGREGATE row unless --keep-aggregate is passed.

WARNING: This is destructive and irreversible.
"""

import argparse

import boto3
from boto3.dynamodb.conditions import Key

TABLE_NAME = "fryrank-app-rankings"
REVIEW_IDENTIFIER_PREFIX = "REVIEW:"
AGGREGATE_IDENTIFIER = "AGGREGATE"


def nuke(restaurant_id: str, keep_aggregate: bool, region: str):
    dynamodb = boto3.resource("dynamodb", region_name=region)
    table = dynamodb.Table(TABLE_NAME)

    print(f"Querying all items for restaurantId='{restaurant_id}'...")

    items = []
    kwargs = {"KeyConditionExpression": Key("restaurantId").eq(restaurant_id)}

    while True:
        response = table.query(**kwargs)
        items.extend(response["Items"])
        last_key = response.get("LastEvaluatedKey")
        if not last_key:
            break
        kwargs["ExclusiveStartKey"] = last_key

    to_delete = [
        item for item in items
        if item["identifier"].startswith(REVIEW_IDENTIFIER_PREFIX)
        or (not keep_aggregate and item["identifier"] == AGGREGATE_IDENTIFIER)
    ]

    if not to_delete:
        print("No items found to delete.")
        return

    print(f"Deleting {len(to_delete)} item(s)...")

    with table.batch_writer() as batch:
        for item in to_delete:
            batch.delete_item(Key={
                "restaurantId": item["restaurantId"],
                "identifier": item["identifier"],
            })

    review_count = sum(1 for i in to_delete if i["identifier"].startswith(REVIEW_IDENTIFIER_PREFIX))
    agg_deleted = any(i["identifier"] == AGGREGATE_IDENTIFIER for i in to_delete)

    print(f"Deleted {review_count} review(s)" + (" and the AGGREGATE row." if agg_deleted else "."))
    if keep_aggregate:
        print("AGGREGATE row retained (--keep-aggregate).")


def main():
    parser = argparse.ArgumentParser(description="Nuke all reviews for a FryRank restaurant ID.")
    parser.add_argument("--restaurant-id", required=True, help="The restaurant ID to nuke reviews for.")
    parser.add_argument("--keep-aggregate", action="store_true", help="Preserve the AGGREGATE row.")
    parser.add_argument("--region", default="us-west-2", help="AWS region (default: us-west-2).")
    args = parser.parse_args()

    print(f"WARNING: This will permanently delete all reviews for restaurantId='{args.restaurant_id}'.")
    confirm = input("Type the restaurant ID to confirm: ").strip()
    if confirm != args.restaurant_id:
        print("Confirmation did not match. Aborting.")
        return

    nuke(args.restaurant_id, args.keep_aggregate, args.region)


if __name__ == "__main__":
    main()

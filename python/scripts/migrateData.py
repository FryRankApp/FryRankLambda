import json
from dotenv import load_dotenv
from decimal import Decimal
from mongoDBClient import MongoDBClient
from dynamoDBClient import DynamoDBClient
import os
import logging
from datetime import datetime
import sys

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler()
    ]
)

def transform_for_dynamodb(data, transformation_function, table_name=None):
    """
    Transform all input data using the given transformation function.

    Args:
        data (list): A list of input items/documents to be transformed.
        transformation_function (function): A transformation function that will process the entire dataset.
        table_name (str, optional): Name of the table being processed.

    Returns:
        tuple: (transformed_items, stats_object)
    """
    print("Processing", len(data), "items")

    transformed_items, stats = transformation_function(data)

    print(f"\nProcessed items: {stats.total_processed}, skipped: {len(stats.failed_items)}")
    if stats.failures_by_reason:
        print("\nSkipped items by reason:")
        for reason, count in stats.failures_by_reason.items():
            print(f"- {reason}: {count}")

    if table_name and len(transformed_items) <= 5:
        print(f"\nSample transformed items for table: {table_name}:")
        for item in transformed_items[:5]:
            print(json.dumps(item, indent=2, default=str))
            print("-" * 80)  # Separator between items
    elif table_name:
        print(f"\nTransformed {len(transformed_items)} items for table: {table_name}")

    return transformed_items, stats


class TransformationStats:
    def __init__(self, item_type="Entry"):
        self.total_processed = 0
        self.successfully_transformed = 0
        self.failed_items = []
        self.processed_items = []
        self.failures_by_reason = {}
        self.item_type = item_type


    def format_stats(self, include_failed_items=True):
        """Format statistics and optionally failed items into a list of strings"""
        lines = []

        lines.append("=== Transformation Statistics ===")
        lines.append(f"Total Processed: {self.total_processed}")
        lines.append(f"Successfully Transformed: {self.successfully_transformed}")
        lines.append(f"Failed: {len(self.failed_items)}")
        lines.append("")

        if self.failures_by_reason:
            lines.append("=== Failure Reasons ===")
            for reason, count in self.failures_by_reason.items():
                lines.append(f"{reason}: {count}")
            lines.append("")

        if include_failed_items and self.failed_items:
            lines.append("=== Failed Entries ===")
            lines.append("")
            for entry in self.failed_items:
                lines.append(f"--- Failed {self.item_type} ---")
                lines.append(f"Timestamp: {entry['timestamp']}")
                lines.append(f"Error: {entry['error']}")
                lines.append("Item:")
                lines.append(json.dumps(entry['item'], indent=2))
                lines.append("")

        return lines

    def write_log(self, filename):
        """Write all processed items and statistics to log file"""
        try:
            with open(filename, "w", encoding="utf-8") as f:
                f.write(f"=== {self.item_type} Processing Log ===\n")
                f.write(f"Generated: {datetime.utcnow().isoformat()}\n\n")

                f.write("=== Processing Statistics ===\n")
                f.write(f"Total Processed: {self.total_processed}\n")
                f.write(f"Successfully Transformed: {self.successfully_transformed}\n")
                f.write(f"Failed: {len(self.failed_items)}\n\n")

                if self.failures_by_reason:
                    f.write("=== Failure Reasons ===\n")
                    for reason, count in self.failures_by_reason.items():
                        f.write(f"{reason}: {count}\n")
                    f.write("\n")

                f.write("=== Processed Items ===\n\n")
                for idx, entry in enumerate(self.processed_items, 1):
                    f.write(f"--- Item {idx} ({entry['status']}) ---\n")
                    f.write(f"Timestamp: {entry['timestamp']}\n")
                    f.write("Original Item:\n")
                    f.write(json.dumps(entry['original_item'], indent=2, default=str))
                    f.write("\n")

                    if entry['status'] == 'success':
                        f.write("Transformed Item:\n")
                        f.write(json.dumps(entry['transformed_item'], indent=2, default=str))
                    else:
                        f.write(f"Error: {entry['error']}\n")

                    f.write("\n" + "-"*50 + "\n\n")

                # This is so that we write the files immediately instead of after program exits.
                f.flush()
                os.fsync(f.fileno())


            print(f"Wrote {self.total_processed} processed items to {filename}")


        except Exception as e:
            print(f"Error writing to {filename}: {str(e)}")



    def print_summary(self):
        """Print transformation statistics to console"""
        for line in self.format_stats(include_failed_items=False):
            print(line)

    def add_processed_item(self, original_item, transformed_item=None, error=None):
        """Log a processed item with its result"""
        entry = {
            'timestamp': datetime.utcnow().isoformat(),
            'original_item': original_item,
            'status': 'success' if transformed_item else 'failed'
        }

        if transformed_item:
            entry['transformed_item'] = transformed_item
        if error:
            entry['error'] = error

        self.processed_items.append(entry)


    def add_success(self, original_item, transformed_item):
        self.total_processed += 1
        self.successfully_transformed += 1
        self.add_processed_item(original_item, transformed_item=transformed_item)

    def add_failure(self, item, error_message):
        self.total_processed += 1
        self.failures_by_reason[error_message] = self.failures_by_reason.get(error_message, 0) + 1
        self.add_processed_item(item, error=error_message)
        self.failed_items.append({
            "item": json.loads(json.dumps(item, default=str)),
            "error": error_message,
            "timestamp": datetime.utcnow().isoformat()
        })


    def get_stats(self):
        return {
            "total_processed": self.total_processed,
            "successfully_transformed": self.successfully_transformed,
            "failed": len(self.failed_items),
            "failures_by_reason": self.failures_by_reason
        }

def transform_reviews_for_dynamodb(mongo_items):
    """
    Transform MongoDB review documents to DynamoDB format. Additionally generates review aggregate
    rows since the new DynamoDB table contains those.
    """
    dynamodb_items = []
    restaurant_map = {}
    stats = TransformationStats("Review Entry")

    for item in mongo_items:
        try:
            required_fields = {
                "restaurantId": str,
                "score": (int, float),
                "title": str,
                "body": str
            }

            for field, field_type in required_fields.items():
                if field not in item or not isinstance(item[field], field_type):
                    raise ValueError(f"Missing or invalid {field}")

            dynamodb_item = {
                "restaurantId": item["restaurantId"],
                "isReview": "true",
                "score": Decimal(str(item["score"])),
                "title": item["title"],
                "body": item["body"]
            }

            # This is the old mongoDB review format when we used to have it autogenerate an ID for the _id field.
            if isinstance(item.get("_id"), dict) and "$oid" in item["_id"]:
                identifier = str("REVIEW:" + item.get("_id"))
            else:
                identifier = f"REVIEW:{item['accountId']}"
            dynamodb_item["identifier"] = identifier

            dynamodb_item["accountId"] = str(item.get("accountId") or item.get("authorId"))

            if "isoDateTime" in item:
                dynamodb_item["isoDateTime"] = item["isoDateTime"]
            else:
                dynamodb_item["isoDateTime"] = datetime.utcnow().isoformat() + "Z"

            dynamodb_items.append(dynamodb_item)
            stats.add_success(item, dynamodb_item)

            restaurant_id = item.get('restaurantId')
            if not restaurant_id:
                continue

            if restaurant_id not in restaurant_map:
                restaurant_map[restaurant_id] = {
                    'reviews': [],
                    'totalScore': 0,
                    'reviewCount': 0
                }

            restaurant_map[restaurant_id]['reviews'].append(item)

            score = item.get('score')
            if score is not None and isinstance(score, (int, float)):
                restaurant_map[restaurant_id]['totalScore'] += score
                restaurant_map[restaurant_id]['reviewCount'] += 1


        except Exception as e:
            stats.add_failure(item, str(e))
            print(f"Failed to transform item: {item.get('_id', 'unknown id')}, Error: {str(e)}")

    aggregate_stats = TransformationStats("Restaurant Aggregates")
    print(f"================= ${restaurant_map.items()}")

    for restaurant_id, data in restaurant_map.items():
        review_count = data['reviewCount']
        total_score = data['totalScore']

        average_score = total_score / review_count if review_count > 0 else 0

        # Create the aggregate item
        aggregate_item = {
            'restaurantId': restaurant_id,
            'identifier': 'AGGREGATE',
            'timestamp': 'AGGREGATE',
            'totalScore': Decimal(str(total_score)),
            'reviewCount': review_count,
            'averageScore': Decimal(str(round(average_score, 1)))
        }

        dynamodb_items.append(aggregate_item)

        aggregate_stats.add_success(
            {"restaurantId": restaurant_id, "calculated": True},
            aggregate_item
        )

    stats.write_log("review_transformation_log.txt")
    aggregate_stats.write_log("restaurant_aggregates_log.txt")
    return dynamodb_items, stats


def transform_metadata_for_dynamodb(mongo_items):
    """Transform MongoDB documents to DynamoDB format."""
    dynamodb_items = []
    stats = TransformationStats("Metadata Entry")

    for item in mongo_items:
        try:
            if '_id' not in item:
                raise ValueError("Missing _id field")

            if 'username' not in item:
                raise ValueError("Missing username field")

            if not isinstance(item['username'], str):
                raise ValueError("Username must be a string")

            dynamodb_item = {
                "accountId": str(item['_id']),
                "username": item["username"]
            }

            dynamodb_compatible_item = json.loads(
                json.dumps(dynamodb_item),
                parse_float=Decimal
            )

            dynamodb_items.append(dynamodb_compatible_item)
            stats.add_success(item, dynamodb_compatible_item)
        except Exception as e:
            stats.add_failure(item, str(e))
            print(f"Failed to transform metadata item: {item.get('_id', 'unknown id')}, Error: {str(e)}")

    stats.write_log("metadata_transformation_log.txt")
    return dynamodb_items, stats



def main():
    load_dotenv()

    # Script variable config
    CONNECTION_STRING = os.getenv('CONNECTION_STRING')
    DATABASE_NAME = os.getenv('DATABASE_NAME')
    AWS_REGION_NAME = os.getenv('AWS_REGION_NAME')
    AWS_ACCESS_KEY = os.getenv('AWS_ACCESS_KEY_ID')
    AWS_SECRET_ACCESS_KEY = os.getenv('AWS_SECRET_ACCESS_KEY')
    AWS_SESSION_TOKEN = os.getenv('AWS_SESSION_TOKEN')
    MONGODB_USER_METADATA_TABLE_NAME = os.getenv('MONGODB_USER_METADATA_TABLE_NAME')
    MONGODB_REVIEW_TABLE_NAME = os.getenv('MONGODB_REVIEW_TABLE_NAME')
    DYNAMODB_USER_METADATA_TABLE_NAME = os.getenv('DYNAMODB_USER_METADATA_TABLE_NAME')
    DYNAMODB_REVIEW_TABLE_NAME = os.getenv('DYNAMODB_REVIEW_TABLE_NAME')

    COLLECTIONS = [
        (MONGODB_USER_METADATA_TABLE_NAME, DYNAMODB_USER_METADATA_TABLE_NAME, transform_metadata_for_dynamodb),
        (MONGODB_REVIEW_TABLE_NAME, DYNAMODB_REVIEW_TABLE_NAME, transform_reviews_for_dynamodb)
    ]

    mongodb_client = MongoDBClient(CONNECTION_STRING, DATABASE_NAME)
    transformed_dynamodb_data = {}
    transformation_stats = {}

    for mongo_collection, dynamo_db_table_name, transform_function in COLLECTIONS:
        print(f"\nProcessing collection: {mongo_collection} -> {dynamo_db_table_name}")
        mongodb_data = mongodb_client.fetch_data(mongo_collection)

        transformed_data, stats = transform_for_dynamodb(
            data=mongodb_data,
            transformation_function=transform_function,
            table_name=dynamo_db_table_name
        )

        transformed_dynamodb_data[dynamo_db_table_name] = transformed_data

        transformation_stats[dynamo_db_table_name] = stats

    print("\n=== Migration Summary ===")
    for table_name, stats in transformation_stats.items():
        print(f"\nTable: {table_name}")
        stats.print_summary()

    sys.stdout.flush()

    confirmation = input("\nDo you want to proceed with the upload? Type 'yes' to continue: ")

    if confirmation.lower() == 'yes':
        print("\nProceeding with upload...")
        dynamo_db_client = DynamoDBClient(AWS_REGION_NAME, AWS_ACCESS_KEY, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN)

        for table_name, items in transformed_dynamodb_data.items():
            print(f"\nUploading to {table_name}...")
            dynamo_db_client.upload_items(table_name, items)

        dynamo_db_client.close()
        print("\nUpload complete!")
    else:
        print("\nUpload cancelled.")

    mongodb_client.close()

if __name__ == '__main__':
    main()
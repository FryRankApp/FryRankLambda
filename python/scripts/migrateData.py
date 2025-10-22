import json
from dotenv import load_dotenv
from decimal import Decimal
from mongoDBClient import MongoDBClient
from dynamoDBClient import DynamoDBClient
import os
import logging

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler()  # Output to console
    ]
)

def transform_for_dynamodb(mongo_items):
    """Transform MongoDB documents to DynamoDB format"""
    dynamodb_items = []

    for item in mongo_items:
        # Convert MongoDB ObjectId to string
        if '_id' in item:
            item['_id'] = str(item['_id'])

        # Convert any non-supported types to DynamoDB compatible formats
        dynamodb_item = json.loads(json.dumps(item), parse_float=Decimal)
        dynamodb_items.append(dynamodb_item)

    return dynamodb_items

def adapt_to_schema(items, schema_mapping):
    """Adapt items to match the target DynamoDB schema"""
    adapted_items = []

    for item in items:
        adapted_item = {}
        for target_key, source_info in schema_mapping.items():
            source_key = source_info['source_key']
            transform_func = source_info.get('transform_func')

            # Extract value from source
            value = item.get(source_key)

            # Apply transformation if specified
            if transform_func and value is not None:
                value = transform_func(value)

            if value is not None:
                adapted_item[target_key] = value

        adapted_items.append(adapted_item)

    return adapted_items


def main():
    load_dotenv()

    CONNECTION_STRING = os.getenv('CONNECTION_STRING')
    DATABASE_NAME = os.getenv('DATABASE_NAME')
    '''region_name = os.getenv('REGION_NAME')
    aws_access_key = os.getenv('AWS_ACCESS_KEY_ID')
    aws_secret_key = os.getenv('AWS_SECRET_ACCESS_KEY')'''

    mongodb_client = MongoDBClient(CONNECTION_STRING, DATABASE_NAME)

if __name__ == '__main__':
    main()
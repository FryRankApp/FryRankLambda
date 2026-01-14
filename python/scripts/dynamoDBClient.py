import boto3
import logging
from botocore.exceptions import ClientError

class DynamoDBClient:
    def __init__(self, region_name, aws_access_key_id=None, aws_secret_access_key=None,
                 aws_session_token=None, endpoint_url=None):
        """
        Args:
            region_name (str): AWS region name (e.g., 'us-east-1')
            aws_access_key_id (str, optional): AWS access key. Defaults to None (uses environment/IAM).
            aws_secret_access_key (str, optional): AWS secret key. Defaults to None (uses environment/IAM).
            aws_session_token (str, optional): AWS session token. Defaults to None.
            endpoint_url (str, optional): Custom endpoint URL for DynamoDB. Defaults to None.
        """
        self.region_name = region_name
        self.aws_access_key_id = aws_access_key_id
        self.aws_secret_access_key = aws_secret_access_key
        self.aws_session_token = aws_session_token
        self.endpoint_url = endpoint_url
        self.max_batch_size = 25  # DynamoDB batch write limit is 25
        self.dynamodb = None
        self.logger = logging.getLogger(__name__)

        # Connect on initialization
        self._connect()

    def _connect(self):
        try:
            kwargs = {
                'region_name': self.region_name
            }

            if self.aws_access_key_id and self.aws_secret_access_key:
                kwargs['aws_access_key_id'] = self.aws_access_key_id
                kwargs['aws_secret_access_key'] = self.aws_secret_access_key

                if self.aws_session_token:
                    kwargs['aws_session_token'] = self.aws_session_token

            if self.endpoint_url:
                kwargs['endpoint_url'] = self.endpoint_url

            # Create the DynamoDB resource
            self.dynamodb = boto3.resource('dynamodb', **kwargs)
            self.logger.info(f"Successfully connected to DynamoDB in region: {self.region_name}")

        except Exception as e:
            self.logger.error(f"Failed to connect to DynamoDB: {str(e)}")
            raise

    def upload_items(self, table_name, items, batch_size=None):
        """
        Upload items to a DynamoDB table using batch writes.

        Args:
            table_name (str): Name of the target DynamoDB table
            items (list): List of items (dictionaries) to upload
            batch_size (int, optional): Size of each batch upload. Defaults to class default.

        Returns:
            dict: Statistics about the upload operation
        """
        if not items:
            self.logger.warning("No items to upload")
            return {"uploaded": 0, "failed": 0, "total": 0}

        if batch_size is None:
            batch_size = self.max_batch_size
        else:
            batch_size = min(batch_size, self.max_batch_size)

        table = self.dynamodb.Table(table_name)
        total_items = len(items)
        uploaded = 0
        failed = 0

        try:
            for i in range(0, total_items, batch_size):
                batch = items[i:i+batch_size]
                batch_num = i//batch_size + 1
                total_batches = (total_items - 1)//batch_size + 1

                try:
                    with table.batch_writer() as batch_writer:
                        for item in batch:
                            batch_writer.put_item(Item=item)

                    uploaded += len(batch)
                    self.logger.info(f"Uploaded batch {batch_num}/{total_batches} ({len(batch)} items)")

                except ClientError as e:
                    failed += len(batch)
                    self.logger.error(f"Failed to upload batch {batch_num}: {str(e)}")

            self.logger.info(f"Upload complete: {uploaded} items uploaded, {failed} items failed")
            return {
                "uploaded": uploaded,
                "failed": failed,
                "total": total_items
            }

        except Exception as e:
            self.logger.error(f"Error during upload to table {table_name}: {str(e)}")
            raise

    def close(self):
        """
        Clean up resources.
        """
        self.dynamodb = None
        self.logger.info("DynamoDB client resources released")

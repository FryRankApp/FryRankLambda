#!/usr/bin/env python3
"""
Script: update_lambdas.py

Purpose:
- Upload the built Lambda zip to an S3 bucket.
- Read terraform.tfstate from the Terraform state S3 bucket to discover
  aws_lambda_function resources and update their code to use the newly uploaded zip.
- This script will dynamically build the S3 bucket names using the AWS account ID:
    - Lambda function bucket: fryrank-app-lambda-function-bucket-{accountId}
    - Terraform state bucket:  fryrank-app-terraform-state-{accountId}
"""

import json
import os
import sys

import boto3
from botocore.exceptions import ClientError

DEFAULT_ZIP_KEY = "FryRankLambda.zip"
DEFAULT_LOCAL_ZIP = "build/distributions/FryRankLambda.zip"


def get_aws_account_id(sts_client=None):
    """
    Returns the AWS account id for the current credentials.
    Exits on error.
    """
    if sts_client is None:
        sts_client = boto3.client("sts")

    try:
        resp = sts_client.get_caller_identity()
        account_id = resp.get("Account")
        if not account_id:
            print("Error: Could not determine AWS account id from STS response.")
            sys.exit(1)
        return account_id
    except Exception as e:
        print(f"Error retrieving AWS account id: {e}")
        sys.exit(1)


def resolve_bucket_names():
    """
    Determine the S3 bucket names to use. Build them using the AWS account id.
    """

    # Need account id to construct names
    account_id = get_aws_account_id()

    lambda_bucket = f"fryrank-app-lambda-function-bucket-{account_id}"
    state_bucket = f"fryrank-app-terraform-state-{account_id}"

    return lambda_bucket, state_bucket


def upload_zip_to_s3(local_zip_path, s3_client, bucket, key):
    """
    Uploads a local zip file to the specified bucket/key.
    Exits on error.
    """
    if not os.path.exists(local_zip_path):
        print(f"Error: Local zip file not found at {local_zip_path}")
        sys.exit(1)
    try:
        print(f"Uploading {local_zip_path} to s3://{bucket}/{key} ...")
        s3_client.upload_file(local_zip_path, bucket, key)
        print("Upload complete.")
    except ClientError as e:
        print(f"Error uploading zip to S3: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"Unexpected error uploading zip to S3: {e}")
        sys.exit(1)


def verify_zip_exists(s3_client, bucket, key):
    """
    Verifies that the object exists in S3 by calling head_object.
    Returns True when present, False otherwise.
    """
    try:
        s3_client.head_object(Bucket=bucket, Key=key)
        return True
    except ClientError as e:
        # If a 404 or NoSuchKey, treat as missing; otherwise rethrow
        error_code = e.response.get("Error", {}).get("Code", "")
        if error_code in ("404", "NoSuchKey", "NotFound"):
            print(f"Error: Lambda zip file not found at s3://{bucket}/{key}")
            return False
        print(f"Error checking for zip in S3: {e}")
        return False
    except Exception as e:
        print(f"Unexpected error checking S3 object: {e}")
        return False


def load_lambda_functions(s3_client, state_bucket, state_key="terraform.tfstate"):
    """
    Reads terraform.tfstate from the given state_bucket and returns a dict
    mapping function_name -> {name, arn}.
    Exits on error when the state file can't be read / parsed.
    """
    try:
        response = s3_client.get_object(Bucket=state_bucket, Key=state_key)
        body = response["Body"].read().decode("utf-8")
        tf_state = json.loads(body)
    except ClientError as e:
        error_code = e.response.get("Error", {}).get("Code", "")
        if error_code in ("NoSuchKey", "404", "NotFound"):
            print(f"Error: {state_key} not found in bucket {state_bucket}")
            sys.exit(1)
        print(f"Error reading terraform state from S3: {e}")
        sys.exit(1)
    except json.JSONDecodeError:
        print("Error: Invalid JSON in terraform.tfstate")
        sys.exit(1)
    except Exception as e:
        print(f"Unexpected error reading terraform state: {e}")
        sys.exit(1)

    lambda_functions = {}

    for resource in tf_state.get("resources", []):
        # Look for aws_lambda_function resources
        if resource.get("type") == "aws_lambda_function":
            for instance in resource.get("instances", []):
                attributes = instance.get("attributes", {})
                function_name = attributes.get("function_name")
                arn = attributes.get("arn")
                if function_name:
                    lambda_functions[function_name] = {
                        "name": function_name,
                        "arn": arn,
                    }

    if not lambda_functions:
        print("Warning: No Lambda functions found in terraform state")

    return lambda_functions


def update_lambda_functions():
    # Initialize AWS clients
    s3_client = boto3.client("s3")
    lambda_client = boto3.client("lambda")

    # Resolve buckets (may build them dynamically from account id)
    lambda_bucket, state_bucket = resolve_bucket_names()

    s3_key = DEFAULT_ZIP_KEY
    local_zip_path = DEFAULT_LOCAL_ZIP

    if not s3_key:
        print("Error: LAMBDA_ZIP_KEY must be set")
        sys.exit(1)

    # Upload local zip to S3
    upload_zip_to_s3(local_zip_path, s3_client, lambda_bucket, s3_key)

    # Verify upload succeeded
    if not verify_zip_exists(s3_client, lambda_bucket, s3_key):
        sys.exit(1)

    # Load functions from terraform state
    lambda_functions = load_lambda_functions(s3_client, state_bucket)

    if not lambda_functions:
        print("No Lambda functions to update")
        sys.exit(0)

    # Update each Lambda function
    success_count = 0
    error_count = 0

    for function_name, function_info in lambda_functions.items():
        print(f"Updating Lambda function: {function_name}")
        try:
            lambda_client.update_function_code(
                FunctionName=function_name,
                S3Bucket=lambda_bucket,
                S3Key=s3_key,
                Publish=True,
            )
            print(f"Successfully updated {function_name}")
            success_count += 1
        except ClientError as e:
            print(f"Error updating {function_name}: {e}")
            error_count += 1
        except Exception as e:
            print(f"Unexpected error updating {function_name}: {e}")
            error_count += 1

    print("\nUpdate Summary:")
    print(f"Successfully updated: {success_count}")
    print(f"Failed to update: {error_count}")

    if error_count > 0:
        sys.exit(1)


if __name__ == "__main__":
    update_lambda_functions()

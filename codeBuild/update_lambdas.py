import boto3
import json
import os
import sys

def load_lambda_functions(s3_client=None, state_bucket=None):
    if s3_client is None:
        s3_client = boto3.client('s3')
    
    if state_bucket is None:
        state_bucket = os.environ.get('TERRAFORM_STATE_BUCKET', 'fryrank-terraform-state-bucket')
    
    try:
        # Read terraform.tfstate from S3 bucket
        response = s3_client.get_object(
            Bucket=state_bucket,
            Key='terraform.tfstate'
        )
        
        # Parse the JSON content
        tf_state = json.loads(response['Body'].read().decode('utf-8'))
        
        # Extract Lambda functions from the state file
        lambda_functions = {}
        
        # Look for aws_lambda_function resources
        for resource in tf_state.get('resources', []):
            if resource['type'] == 'aws_lambda_function':
                for instance in resource['instances']:
                    function_name = instance['attributes']['function_name']
                    lambda_functions[function_name] = {
                        'name': function_name,
                        'arn': instance['attributes']['arn']
                    }
        
        if not lambda_functions:
            print("Warning: No Lambda functions found in terraform state")
        
        return lambda_functions
    except s3_client.exceptions.NoSuchKey:
        print(f"Error: terraform.tfstate not found in bucket {state_bucket}")
        sys.exit(1)
    except json.JSONDecodeError:
        print("Error: Invalid JSON in terraform.tfstate")
        sys.exit(1)
    except Exception as e:
        print(f"Error reading terraform state: {str(e)}")
        sys.exit(1)

def verify_zip_exists(s3_client, bucket, key):
    try:
        s3_client.head_object(Bucket=bucket, Key=key)
        return True
    except s3_client.exceptions.ClientError:
        print(f"Error: Lambda zip file not found at s3://{bucket}/{key}")
        return False

def update_lambda_functions():
    # Initialize AWS clients
    s3_client = boto3.client('s3')
    lambda_client = boto3.client('lambda')
    
    # Get the S3 bucket and key from environment variables
    s3_bucket = os.environ.get('S3_BUCKET')
    s3_key = os.environ.get('S3_KEY')
    
    if not s3_bucket or not s3_key:
        print("Error: S3_BUCKET and S3_KEY environment variables must be set")
        sys.exit(1)
    
    # Verify the zip file exists
    if not verify_zip_exists(s3_client, s3_bucket, s3_key):
        sys.exit(1)
    
    # Load Lambda functions from terraform state
    lambda_functions = load_lambda_functions(s3_client)
    
    if not lambda_functions:
        print("No Lambda functions to update")
        sys.exit(0)
    
    # Update each Lambda function
    success_count = 0
    error_count = 0
    
    for function_name in lambda_functions.items():
        print(f"Updating Lambda function: {function_name}")
        try:
            lambda_client.update_function_code(
                FunctionName=function_name,
                S3Bucket=s3_bucket,
                S3Key=s3_key
            )
            print(f"Successfully updated {function_name}")
            success_count += 1
        except Exception as e:
            print(f"Error updating {function_name}: {str(e)}")
            error_count += 1
    
    print(f"\nUpdate Summary:")
    print(f"Successfully updated: {success_count}")
    print(f"Failed to update: {error_count}")
    
    if error_count > 0:
        sys.exit(1)

if __name__ == "__main__":
    update_lambda_functions() 
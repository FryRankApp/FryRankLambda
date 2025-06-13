import json
import os
import boto3
import yaml
import hcl2
import sys

def load_lambda_function(s3_client=None, target_function=None):
    if s3_client is None:
        s3_client = boto3.client('s3')
    
    # Read terraform.tfstate from S3 bucket
    response = s3_client.get_object(
        Bucket='fryrank-terraform-state-bucket',
        Key='terraform.tfstate'
    )
    
    # Parse the JSON content
    tf_state = json.loads(response['Body'].read().decode('utf-8'))
    
    # Look for the specific Lambda function in the state file
    for resource in tf_state.get('resources', []):
        if resource['type'] == 'aws_lambda_function':
            for instance in resource['instances']:
                function_name = instance['attributes']['function_name']
                if function_name == target_function:
                    return {
                        'name': function_name,
                        'arn': instance['attributes']['arn']
                    }
    
    raise ValueError(f"Lambda function {target_function} not found in terraform state")

def generate_appspec(lambda_client=None, s3_client=None, target_function=None):
    if lambda_client is None:
        lambda_client = boto3.client('lambda')
    
    # Get the specific function
    function = load_lambda_function(s3_client, target_function)
    function_name = function['name']
    
    # Get current function version
    function_info = lambda_client.get_function(FunctionName=function_name)
    current_version = function_info['Configuration']['Version']
    
    # Create new version
    new_version = lambda_client.publish_version(
        FunctionName=function_name
    )['Version']
    
    # Create appspec for single function
    appspec = {
        'version': '0.0',
        'Resources': [{
            function_name: {
                'Type': 'AWS::Lambda::Function',
                'Properties': {
                    'Name': function_name,
                    'Alias': 'Production',
                    'CurrentVersion': current_version,
                    'TargetVersion': new_version
                }
            }
        }]
    }
    
    # Write appspec file
    with open('appspec.yml', 'w') as f:
        f.write(yaml.dump(appspec, default_flow_style=False))

def main():
    # Get function name from environment variable
    target_function = os.environ.get('LAMBDA_FUNCTION')
    if not target_function:
        raise ValueError("LAMBDA_FUNCTION environment variable is required")
    
    generate_appspec(target_function=target_function)

if __name__ == '__main__':
    main()
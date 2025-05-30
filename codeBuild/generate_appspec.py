import json
import os
import boto3
import yaml
import hcl2

def load_lambda_functions(s3_client=None):
    if s3_client is None:
        s3_client = boto3.client('s3')
    
    # Read terraform.tfstate from S3 bucket
    response = s3_client.get_object(
        Bucket='fryrank-terraform-state-bucket',
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
    
    return lambda_functions

def generate_appspec(lambda_client=None, s3_client=None):
    if lambda_client is None:
        lambda_client = boto3.client('lambda')
    lambda_functions = load_lambda_functions(s3_client)
    
    # Process each function separately
    for key, function in lambda_functions.items():
        function_name = function['name']
        
        # Get current function version
        function_info = lambda_client.get_function(FunctionName=function_name)
        current_version = function_info['Configuration']['Version']
        
        # Create new version
        new_version = lambda_client.publish_version(
            FunctionName=function_name
        )['Version']
        
        # Create single resource for this function
        resource = {
            function_name: {
                'Type': 'AWS::Lambda::Function',
                'Properties': {
                    'Name': function_name,
                    'Alias': 'Production',
                    'CurrentVersion': current_version,
                    'TargetVersion': new_version
                }
            }
        }
        
        # Create appspec for this function
        appspec = {
            'version': '0.0',
            'Resources': [resource]
        }
        
        # Write appspec file for this function
        appspec_filename = f'appspec-{function_name}.yml'
        with open(appspec_filename, 'w') as f:
            f.write(yaml.dump(appspec, default_flow_style=False))
        print(f"Generated {appspec_filename}")

def main():
    generate_appspec()

if __name__ == '__main__':
    main()

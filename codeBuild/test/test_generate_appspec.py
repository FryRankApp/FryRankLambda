import os
import json
import yaml
import sys
from unittest.mock import patch, MagicMock

# Add parent directory to Python path to import generate_appspec
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from generate_appspec import generate_appspec, load_lambda_functions

def validate_appspec():
    print("\nValidating appspec files...")
    
    # Mock S3 response
    mock_s3 = MagicMock()
    mock_s3.get_object.return_value = {
        'Body': MagicMock(
            read=lambda: json.dumps({
                'resources': [
                    {
                        'type': 'aws_lambda_function',
                        'instances': [
                            {
                                'attributes': {
                                    'function_name': 'testFunction1',
                                    'arn': 'arn:aws:lambda:us-west-2:123456789012:function:testFunction1'
                                }
                            },
                            {
                                'attributes': {
                                    'function_name': 'testFunction2',
                                    'arn': 'arn:aws:lambda:us-west-2:123456789012:function:testFunction2'
                                }
                            }
                        ]
                    }
                ]
            }).encode('utf-8')
        )
    }
    
    # Mock AWS Lambda client
    mock_lambda = MagicMock()
    mock_lambda.get_function.return_value = {
        'Configuration': {'Version': '1'}
    }
    mock_lambda.publish_version.return_value = {'Version': '2'}
    
    # Generate the appspec files with mocked AWS clients
    print("Generating appspec files...")
    generate_appspec(lambda_client=mock_lambda, s3_client=mock_s3)
    
    # Get all function names from mocked state
    lambda_functions = load_lambda_functions(s3_client=mock_s3)
    
    # Validate each function's appspec file
    for key, function in lambda_functions.items():
        function_name = function['name']
        appspec_filename = f'appspec-{function_name}.yml'
        
        # Verify the file was created
        assert os.path.exists(appspec_filename), f"AppSpec file {appspec_filename} was not created"
        
        # Read and parse the generated file
        with open(appspec_filename, 'r') as f:
            generated = yaml.safe_load(f)
        
        # Validate structure
        assert generated['version'] == '0.0', f"Version should be 0.0 in {appspec_filename}"
        assert 'Resources' in generated, f"Should have Resources section in {appspec_filename}"
        assert len(generated['Resources']) == 1, f"Should have exactly 1 resource in {appspec_filename}"
        
        # Get the function resource
        function_resource = generated['Resources'][0][function_name]
        
        assert function_resource['Type'] == 'AWS::Lambda::Function', f"Type should be AWS::Lambda::Function for {function_name}"
        
        properties = function_resource['Properties']
        required_props = ['Name', 'Alias', 'CurrentVersion', 'TargetVersion']
        for prop in required_props:
            assert prop in properties, f"Properties should include {prop} for {function_name}"
        
        assert properties['Name'] == function_name, f"Name should match function name for {function_name}"
        assert properties['Alias'] == 'Production', f"Alias should be Production for {function_name}"
        assert properties['CurrentVersion'] == '1', f"CurrentVersion should be 1 for {function_name}"
        assert properties['TargetVersion'] == '2', f"TargetVersion should be 2 for {function_name}"
        
        print(f"[PASS] Validation passed for {function_name}")
        print(f"Generated AppSpec for {function_name}:")
        print(yaml.dump(generated, default_flow_style=False))
        print("---")
    
    print("[PASS] Validation passed for all functions")

def main():
    validate_appspec()

if __name__ == '__main__':
    main()

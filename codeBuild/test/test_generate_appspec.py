import os
import json
import yaml
import sys
from unittest.mock import patch, MagicMock

# Add parent directory to Python path to import generate_appspec
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from generate_appspec import generate_appspec, load_lambda_functions

def validate_appspec():
    print("\nValidating appspec.yml...")
    
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
    
    # Generate the appspec file with mocked AWS clients
    print("Generating appspec.yml...")
    generate_appspec(lambda_client=mock_lambda, s3_client=mock_s3)
    
    # Verify the file was created
    assert os.path.exists('appspec.yml'), "AppSpec file appspec.yml was not created"
    
    # Read and parse the generated file
    with open('appspec.yml', 'r') as f:
        generated = yaml.safe_load(f)
    
    # Validate structure
    assert generated['version'] == '0.0', "Version should be 0.0"
    assert 'Resources' in generated, "Should have Resources section"
    
    # Get all function names from mocked state
    lambda_functions = load_lambda_functions(s3_client=mock_s3)
    expected_function_count = len(lambda_functions)
    assert len(generated['Resources']) == expected_function_count, f"Should have exactly {expected_function_count} resources"
    
    # Validate each function
    for key, function in lambda_functions.items():
        function_name = function['name']
        # Find the resource for this function
        function_resource = None
        for resource in generated['Resources']:
            if function_name in resource:
                function_resource = resource[function_name]
                break
        
        assert function_resource is not None, f"Resource for {function_name} not found"
        assert function_resource['Type'] == 'AWS::Lambda::Function', f"Type should be AWS::Lambda::Function for {function_name}"
        
        properties = function_resource['Properties']
        required_props = ['Name', 'Alias', 'CurrentVersion', 'TargetVersion']
        for prop in required_props:
            assert prop in properties, f"Properties should include {prop} for {function_name}"
        
        assert properties['Name'] == function_name, f"Name should match function name for {function_name}"
        assert properties['Alias'] == 'Production', f"Alias should be Production for {function_name}"
        assert properties['CurrentVersion'] == '1', f"CurrentVersion should be 1 for {function_name}"
        assert properties['TargetVersion'] == '2', f"TargetVersion should be 2 for {function_name}"
    
    print("[PASS] Validation passed for all functions")
    print("Generated AppSpec:")
    print(yaml.dump(generated, default_flow_style=False))
    print("---")

def main():
    validate_appspec()

if __name__ == '__main__':
    main()

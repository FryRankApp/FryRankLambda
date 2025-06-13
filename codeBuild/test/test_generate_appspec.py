import os
import json
import yaml
import sys
from unittest.mock import patch, MagicMock

# Add parent directory to Python path to import generate_appspec
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from generate_appspec import generate_appspec, load_lambda_function

def validate_appspec():
    print("\nValidating appspec.yml...")
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
    
    # Set environment variable for target function
    os.environ['LAMBDA_FUNCTION'] = 'testFunction1'
    
    # Generate the appspec file with mocked AWS clients
    print("Generating appspec.yml...")
    generate_appspec(lambda_client=mock_lambda, s3_client=mock_s3, target_function='testFunction1')
    
    # Verify the file was created
    assert os.path.exists('appspec.yml'), "AppSpec file appspec.yml was not created"
    
    # Read and parse the generated file
    with open('appspec.yml', 'r') as f:
        generated = yaml.safe_load(f)
    
    # Validate structure
    assert generated['version'] == '0.0', "Version should be 0.0"
    assert 'Resources' in generated, "Should have Resources section"
    assert len(generated['Resources']) == 1, "Should have exactly one resource"
    
    # Validate the function
    function_resource = generated['Resources'][0]['testFunction1']
    assert function_resource['Type'] == 'AWS::Lambda::Function', "Type should be AWS::Lambda::Function"
    
    properties = function_resource['Properties']
    required_props = ['Name', 'Alias', 'CurrentVersion', 'TargetVersion']
    for prop in required_props:
        assert prop in properties, f"Properties should include {prop}"
    
    assert properties['Name'] == 'testFunction1', "Name should match function name"
    assert properties['Alias'] == 'Production', "Alias should be Production"
    assert properties['CurrentVersion'] == '1', "CurrentVersion should be 1"
    assert properties['TargetVersion'] == '2', "TargetVersion should be 2"
    
    print("[PASS] Validation passed for testFunction1")
    print("Generated AppSpec:")
    print(yaml.dump(generated, default_flow_style=False))
    print("---")

def test_load_lambda_function():
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
                            }
                        ]
                    }
                ]
            }).encode('utf-8')
        )
    }
    
    # Test successful function load
    function = load_lambda_function(s3_client=mock_s3, target_function='testFunction1')
    assert function['name'] == 'testFunction1'
    assert function['arn'] == 'arn:aws:lambda:us-west-2:123456789012:function:testFunction1'
    
    # Test function not found
    try:
        load_lambda_function(s3_client=mock_s3, target_function='nonexistentFunction')
        assert False, "Should have raised ValueError for nonexistent function"
    except ValueError as e:
        assert str(e) == "Lambda function nonexistentFunction not found in terraform state"

def main():
    validate_appspec()
    test_load_lambda_function()

if __name__ == '__main__':
    main()
import json
import os
import boto3
import yaml
import hcl2

def load_lambda_functions():
    # Get the project root directory (one level up from build directory)
    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    lambda_tf_path = os.path.join(project_root, 'stack', 'lambda.tf')
    
    with open(lambda_tf_path, 'r') as f:
        tf_config = hcl2.load(f)
        # Extract lambda_functions from locals block
        if 'locals' in tf_config:
            locals_blocks = tf_config['locals']
            if isinstance(locals_blocks, list) and len(locals_blocks) > 0:
                locals_block = locals_blocks[0]  # Get the first locals block
                if 'lambda_functions' in locals_block:
                    return locals_block['lambda_functions']
    return {}

def generate_appspec():
    lambda_client = boto3.client('lambda')
    lambda_functions = load_lambda_functions()
    
    # Create resources list for all functions
    resources = []
    for key, function in lambda_functions.items():
        function_name = function['name']
        
        # Get current function version
        function_info = lambda_client.get_function(FunctionName=function_name)
        current_version = function_info['Configuration']['Version']
        
        # Create new version
        new_version = lambda_client.publish_version(
            FunctionName=function_name
        )['Version']
        
        # Add function to resources
        resources.append({
            function_name: {
                'Type': 'AWS::Lambda::Function',
                'Properties': {
                    'Name': function_name,
                    'Alias': 'Production',
                    'CurrentVersion': current_version,
                    'TargetVersion': new_version
                }
            }
        })
    
    # Create single appspec with all functions
    appspec = {
        'version': '0.0',
        'Resources': resources
    }
    
    # Write appspec file
    with open('appspec.yml', 'w') as f:
        f.write(yaml.dump(appspec, default_flow_style=False))

def main():
    generate_appspec()

if __name__ == '__main__':
    main()

#!/bin/bash

# Exit on error
set -e

echo "Building the project with Gradle..."
./gradlew build

echo "Updating Lambda functions..."
python3 codeBuild/update_lambdas.py

echo "Deployment completed successfully!"

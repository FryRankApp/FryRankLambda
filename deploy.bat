@echo off
echo Building the project with Gradle...
call gradlew build

if %ERRORLEVEL% NEQ 0 (
    echo Build failed with error level %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)

echo Updating Lambda functions...
python codeBuild\update_lambdas.py

if %ERRORLEVEL% NEQ 0 (
    echo Failed to update Lambda functions
    exit /b %ERRORLEVEL%
)

echo Deployment completed successfully!

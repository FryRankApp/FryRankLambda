## FryRankLambda ##

A Lambda package hosting all the backend logic for the Fry Rank project.

### Prerequisites
1) Install: `python3 -m pip install boto3`  
2) Set AWS region: `aws configure set region us-west-2`

### Testing ###
To build the package, run the following command, either in the terminal or with the IntelliJ Gradle plugin:
```bash
gradle build
```

To build and deploy it to your sandbox account in one step, run the following command:
```bash
./deploy.sh
```

Note: If you are on Windows, use the deploy.bat file instead. The command is `.\deploy.bat`.

Troubleshooting: If you come across a "NoRegionError", you may want to run "aws configure" via aws cli 
to set the region manually from there

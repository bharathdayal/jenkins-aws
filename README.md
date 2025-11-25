# Java Spring Boot CI/CD with Jenkins, AWS ECR & EC2

This repository demonstrates a complete CI/CD pipeline for a Java (Spring Boot) app built with Gradle. The pipeline (Jenkins Declarative) uses Jib to build and push container images to AWS ECR and deploys the container on an AWS EC2 instance. A local JAR smoke test is optional. The orchestration lives in the repository's `Jenkinsfile`.

## Features
- Jenkins Declarative Pipeline
- Jib (build images without Docker daemon)
- Push to AWS ECR
- Deploy to EC2 (Docker on remote host)
- Optional local smoke test of the produced JAR

## Pipeline steps
1. Checkout source from SCM.
2. Prep Gradle: normalize line endings, make `gradlew` executable, verify Java/Gradle.
3. Build JAR:
   ```
   ./gradlew --no-daemon clean bootJar -x test --stacktrace --info
   ```
   Archives `build/libs/*.jar`.
4. Publish image to AWS ECR via Jib (tags: `${BUILD_NUMBER}`, `latest`).
5. Optional smoke test: start JAR on port `8086`, curl health, archive `app.log`.
6. Deploy to EC2: SSH, ensure Docker, login to ECR, pull image, stop/remove existing container, run new container with `--restart=always`.

## Pipeline parameters (examples)
- APP_NAME: `java-jenkins-aws`
- AWS_ACCOUNT_ID: `11xxxx`
- AWS_REGION: `us-east-2`
- ECR_REPO: `java-jenkins-aws`
- EC2_HOST: `ec2-xxxxxxxxx.compute.amazonaws.com`
- EC2_USER: `ec2-user`
- SMOKE_JAR: `true`
- EXPOSE_PORT: `8086`

## Environment variables used
- GRADLE_USER_HOME
- TAG (defaults to `${BUILD_NUMBER}`)
- EXPOSE_PORT (default `8086`)
- ECR_REGISTRY = `${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com`
- ECR_IMAGE = `${ECR_REGISTRY}/${ECR_REPO}`

## Jenkins credentials required
- AWS credentials (ID: `aws-creds`) — access to ECR (and EC2 if needed).
- EC2 SSH private key (ID: `ec2-ssh`) for SSH access to the EC2 host.

## Prerequisites
On Jenkins agent:
- Pipeline support, shell (`sh`) execution
- JDK 21 configured (agent label `jdk-21`)
- Outbound internet access (for AWS CLI install and ECR)

On AWS:
- ECR repository (pipeline will create it if missing)
- EC2 instance with SSH access and security group allowing app port and SSH
- IAM permissions for ECR operations (Describe/Create repo, GetAuthorizationToken, Push)

## Key commands (examples)
Smoke test
```
nohup java -jar build/libs/*.jar --server.port=${EXPOSE_PORT} > app.log 2>&1 &
echo $! > app.pid
sleep 8
curl -sf http://localhost:${EXPOSE_PORT}/ || true
kill $(cat app.pid) || true
```

Remote ECR login & deploy (on EC2)
```
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"
docker pull "$ECR:$TAG"
docker rm -f "$APP_NAME" || true
docker run -d --name "$APP_NAME" -p "$PORT:$PORT" --restart=always "$ECR:$TAG"
```

Local development
```
./gradlew clean bootJar
java -jar build/libs/*.jar --server.port=8086
```

## Notes
- Jib removes the need for Docker on the Jenkins agent; Docker is required only on the EC2 host.
- Adjust base image, ports, and deployment targets as needed (multiple EC2 instances, staging/prod environments).
- Check Jenkins console logs and archived artifacts (app.log, JAR) for troubleshooting.

For full behavioral details, refer to the `Jenkinsfile` in this repository.

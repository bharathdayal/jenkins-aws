# Java Spring Boot CI/CD with Jenkins, AWS ECR & EC2

This repository demonstrates a complete CI/CD pipeline for a **Java (Spring Boot) application** built with **Gradle**, using:

- **Jenkins Declarative Pipeline**
- **Jib** (container image build without Docker daemon)
- **AWS ECR** (for storing Docker images)
- **AWS EC2** (for running the containerized app)
- Optional **local smoke test** of the built JAR

The entire flow is orchestrated via the `Jenkinsfile` in this repo.

---

## 🚀 Pipeline Overview

The Jenkins pipeline performs the following steps:

1. **Checkout**
   - Checks out the source code from the configured SCM.

2. **Prep Gradle**
   - Normalizes line endings and makes `gradlew` executable.
   - Verifies Gradle and Java versions.

3. **Build JAR**
   - Runs:
     ```bash
     ./gradlew --no-daemon clean bootJar -x test --stacktrace --info
     ```
   - Archives the generated JAR from `build/libs/*.jar`.

4. **Publish Image to AWS ECR (via Jib)**
   - Installs AWS CLI locally in the Jenkins workspace if needed.
   - Ensures the ECR repository exists (creates if missing).
   - Builds and pushes the Docker image to ECR using **Jib**:
     - Tags: `${BUILD_NUMBER}` and `latest`
     - Base image: `eclipse-temurin:21-jre`
     - Exposed port: `8086`

5. **Smoke Test (Run JAR Locally)** – *optional*
   - Starts the generated JAR locally on port `8086`.
   - Performs a simple HTTP `curl` against `http://localhost:8086/`.
   - Stops the process and archives `app.log`.

6. **Deploy to EC2**
   - SSHs into the configured EC2 instance using SSH key credentials.
   - Installs Docker if not already installed (supports common Ubuntu/Debian/RHEL/CentOS/Amazon Linux).
   - Logs in to ECR using AWS CLI and pulls the newly built image.
   - Stops & removes any existing container with the same app name.
   - Runs the container with:
     - Name: `APP_NAME`
     - Port mapping: `PORT:PORT`
     - Restart policy: `--restart=always`

---

## ⚙️ Jenkins Pipeline Parameters

The pipeline is parameterized to make it reusable across environments.

| Parameter            | Type    | Default                               | Description                                           |
|----------------------|---------|---------------------------------------|-------------------------------------------------------|
| `APP_NAME`           | String  | `java-jenkins-aws`                    | Application/container name.                          |
| `DOCKERHUB_NAMESPACE`| String  | `bharathdayal`                        | Docker Hub user/org (currently not used in ECR flow).|
| `SMOKE_JAR`          | Boolean | `true`                                | Run local JAR smoke test after build.                |
| `AWS_ACCOUNT_ID`     | String  | `11xxxx`                              | Your AWS account ID.                                 |
| `AWS_REGION`         | String  | `us-east-2`                           | AWS region (e.g., `us-east-1`, `us-east-2`).         |
| `ECR_REPO`           | String  | `java-jenkins-aws`                    | ECR repository name.                                 |
| `EC2_HOST`           | String  | `ec2-xxxxxxxxx.compute.amazonaws.com` | EC2 public DNS or IP.                                |
| `EC2_USER`           | String  | `ec2-user`                            | SSH login user (`ec2-user`, `ubuntu`, etc.).         |

---

## 🌍 Environment & Tools

The pipeline configures the following environment variables:

- `GRADLE_USER_HOME` – gradle cache folder within workspace.
- `TAG` – Image tag (defaults to Jenkins `${BUILD_NUMBER}`).
- `EXPOSE_PORT` – Application port (`8086`).
- `ECR_REGISTRY` – `${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com`
- `ECR_IMAGE` – `${ECR_REGISTRY}/${ECR_REPO}`

In the **Publish** stage, additional environment variables are set for AWS:

- `AWS_DEFAULT_REGION`
- `ECR_REPO`
- `ECR_IMAGE`

---

## 🔐 Jenkins Credentials Required

You must configure these credentials in Jenkins:

1. **AWS Credentials**
   - ID: `aws-creds`
   - Type: *AWS Credentials* (Access Key ID + Secret Access Key)
   - Permissions: access to ECR and EC2 (for ECR login, describe/create repo, etc.).

2. **EC2 SSH Key**
   - ID: `ec2-ssh`
   - Type: *SSH Username with private key* or *SSH private key*.
   - Private key should match the one configured on the EC2 instance.

---

## 🧱 Prerequisites

### On Jenkins

- Jenkins with **Pipeline** support.
- JDK 21 installed and configured as `jdk-21`.
- Ability to run shell commands (`sh`) on the agent.
- Outbound internet access (for AWS CLI install and ECR access).

### On AWS

1. **ECR**
   - An ECR repository for the app (the pipeline will create it if missing).
   - Example: `java-jenkins-aws`.

2. **EC2 Instance**
   - Running a supported Linux distribution (Ubuntu, Debian, CentOS, RHEL, Amazon Linux).
   - Security group must allow inbound traffic on the app port (`8086`) from your users.
   - SSH access allowed from Jenkins agent to EC2 (`22`).

3. **IAM Permissions for AWS Credentials**
   - ECR:
     - `ecr:DescribeRepositories`
     - `ecr:CreateRepository`
     - `ecr:GetAuthorizationToken`
     - `ecr:BatchCheckLayerAvailability`
     - `ecr:PutImage`
     - `ecr:InitiateLayerUpload`, `ecr:UploadLayerPart`, `ecr:CompleteLayerUpload`
   - (Optional) EC2-related permissions if needed beyond ECR login.

---

## 🛠️ How to Use

### 1. Clone & Configure Jenkins Pipeline

- Create a new **Multibranch Pipeline** or **Pipeline** job in Jenkins.
- Point it to this repository (so it finds the `Jenkinsfile`).
- Ensure the correct **JDK** and **agent** are configured.

### 2. Set Job Parameters

When triggering the build, set (or confirm) values for:

- `APP_NAME`
- `AWS_ACCOUNT_ID`
- `AWS_REGION`
- `ECR_REPO`
- `EC2_HOST`
- `EC2_USER`
- `SMOKE_JAR` (true/false)

### 3. Run the Pipeline

The pipeline will:

1. Checkout the code.
2. Prepare Gradle and verify tools.
3. Build the Spring Boot JAR.
4. Build & push the Docker image to ECR (with Jib).
5. Optionally run the local smoke test.
6. Deploy the latest image to the EC2 instance.

---

## 🔎 Smoke Test Details

If `SMOKE_JAR` is `true`:

- The pipeline runs:
  ```bash
  nohup java -jar build/libs/*.jar --server.port=${EXPOSE_PORT} > app.log 2>&1 &
  echo $! > app.pid
  sleep 8
  curl -sf http://localhost:${EXPOSE_PORT}/ || true
  kill $(cat app.pid) || true

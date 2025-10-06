pipeline {
  agent any
  options {
    timestamps()
   
  }
  tools { jdk 'jdk-21' }

  parameters {
    string(name: 'APP_NAME', defaultValue: 'java-jenkins-aws', description: 'Image/repo name')
    string(name: 'DOCKERHUB_NAMESPACE', defaultValue: 'bharathdayal', description: 'Docker Hub user/org')
    booleanParam(name: 'SMOKE_JAR', defaultValue: true, description: 'Run jar locally as a smoke test (no Docker)')

    // AWS / EC2 deployment params
    string(name: 'AWS_ACCOUNT_ID', defaultValue: '115047389529', description: 'AWS Account ID')
    string(name: 'AWS_REGION', defaultValue: 'us-east-2', description: 'AWS region')
    string(name: 'ECR_REPO', defaultValue: 'java-jenkins-aws', description: 'ECR repository name (usually same as APP_NAME)')
    string(name: 'EC2_HOST', defaultValue: 'ec2-18-223-152-111.us-east-2.compute.amazonaws.com', description: 'EC2 public DNS or IP')
    string(name: 'EC2_USER', defaultValue: 'ec2-user', description: 'SSH user (ec2-user, ubuntu, etc.)')
  }

  environment {
    GRADLE_USER_HOME = "${WORKSPACE}/.gradle"
    TAG             = "${env.BUILD_NUMBER}"
    EXPOSE_PORT     = "8086"
    // ECR registry URL
    ECR_REGISTRY    = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${params.AWS_REGION}.amazonaws.com"
    ECR_IMAGE       = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${params.AWS_REGION}.amazonaws.com/${params.ECR_REPO}"
  }

  stages {
    stage('Checkout') { steps { checkout scm } }



stage('Prep Gradle') {
  steps {
       sh '''
      #!/bin/bash
      set -eu
      sed -i -e 's/\r$//' gradlew || true
      chmod +x gradlew
      ./gradlew --version
      java -version
      '''
  }
}

    stage('Build JAR') {
      steps {
       sh '''
        bash -lc "
         
          ./gradlew --no-daemon clean bootJar -x test --stacktrace --info
        "
        '''
      }
      post { success { archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true } }
    }

    // ---- NEW: Publish image to Amazon ECR using Jib (no local Docker daemon required) ----
        stage('Publish (Jib → Amazon ECR)') {
        environment {
          AWS_DEFAULT_REGION = "${params.AWS_REGION}"  // e.g., us-east-2
          ECR_REGISTRY = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${params.AWS_REGION}.amazonaws.com"
          ECR_REPO     = "${params.ECR_REPO}"          // e.g., myapp
          ECR_IMAGE    = "${params.AWS_ACCOUNT_ID}.dkr.ecr.${params.AWS_REGION}.amazonaws.com/${params.ECR_REPO}"
        }
        steps {
          withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: 'aws-creds']]) {
            sh '''#!/usr/bin/env bash
      set -euo pipefail
      
      echo "Ensuring AWS CLI is available..."
      if ! command -v aws >/dev/null 2>&1; then
        echo "Installing AWS CLI v2 to workspace (user space)..."
        curl -fsSL https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip -o awscliv2.zip
        if command -v unzip >/dev/null 2>&1; then unzip -q awscliv2.zip; else jar xf awscliv2.zip; fi
        ./aws/install -i "${WORKSPACE}/.aws-cli" -b "${WORKSPACE}/.local/bin"
        export PATH="${WORKSPACE}/.local/bin:$PATH"
      fi
      
      # Ensure PATH contains local bin even if aws was already present from a prior run
      export PATH="${WORKSPACE}/.local/bin:$PATH"
      
      echo "AWS CLI: $(aws --version 2>&1)"
      echo "ECR Image : ${ECR_IMAGE}:${TAG}"
      echo "Region    : ${AWS_DEFAULT_REGION}"
      
      # Ensure repo exists (idempotent)
      aws ecr describe-repositories --repository-names "${ECR_REPO}" >/dev/null 2>&1 \
        || aws ecr create-repository --repository-name "${ECR_REPO}" >/dev/null
      
      # Explicit ECR auth for Jib (username MUST be AWS)
      ECR_PASSWORD="$(aws ecr get-login-password --region "${AWS_DEFAULT_REGION}")"
      
      echo "Building & pushing with Jib..."
      ./gradlew --no-daemon jib \
        -Djib.to.image="${ECR_IMAGE}" \
        -Djib.to.tags="${TAG},latest" \
        -Djib.to.auth.username=AWS \
        -Djib.to.auth.password="${ECR_PASSWORD}" \
        -Djib.from.image=eclipse-temurin:21-jre \
        -Djib.container.ports="${EXPOSE_PORT}" \
        --stacktrace --info
      '''
          }
        }
      }


    // Optional local smoke test
    stage('Smoke test (run jar)') {
      when { expression { return params.SMOKE_JAR } }
      steps {
        sh """
          nohup java -jar build/libs/*.jar --server.port=${EXPOSE_PORT} > app.log 2>&1 &
          echo \$! > app.pid
          sleep 8
          curl -sf http://localhost:${EXPOSE_PORT}/ || true
          kill \$(cat app.pid) || true
        """
      }
      post {
        always { archiveArtifacts artifacts: 'app.log', onlyIfSuccessful: false }
      }
    }

    // ---- NEW: Deploy on EC2 (pull from ECR and (re)start) ----
stage('Deploy to EC2') {
  steps {
    script {
      // Resolve values in Groovy (params -> env -> defaults)
      def resolvedAppName = (params?.APP_NAME ?: env.APP_NAME) ?: ''
      def resolvedPort    = (params?.EXPOSE_PORT ?: env.EXPOSE_PORT) ?: ''
      def resolvedEcrImage= (params?.ECR_IMAGE ?: env.ECR_IMAGE) ?: "${ECR_IMAGE ?: ''}"
      def resolvedTag     = (params?.TAG ?: env.TAG) ?: "${TAG ?: 'latest'}"
      def resolvedRegion  = (params?.AWS_REGION ?: env.AWS_REGION) ?: "${AWS_REGION ?: ''}"
      def resolvedRegistry= (params?.ECR_REGISTRY ?: env.ECR_REGISTRY) ?: "${ECR_REGISTRY ?: ''}"
      def resolvedHost    = (params?.EC2_HOST ?: env.EC2_HOST) ?: "${params?.EC2_HOST ?: ''}"
      def resolvedUser    = (params?.EC2_USER ?: env.EC2_USER) ?: "${params?.EC2_USER ?: ''}"

      // Fail early if required values are missing (helps avoid unbound variable on remote)
      if (!resolvedAppName) { error "Missing APP_NAME (set param APP_NAME or env.APP_NAME)" }
      if (!resolvedPort)    { error "Missing EXPOSE_PORT (set param EXPOSE_PORT or env.EXPOSE_PORT)" }
      if (!resolvedHost)    { error "Missing EC2_HOST (set param EC2_HOST or env.EC2_HOST)" }
      if (!resolvedUser)    { error "Missing EC2_USER (set param EC2_USER or env.EC2_USER)" }

      // Print resolved values in Jenkins log for verification
      echo "Resolved values before deploy:"
      echo "  APP_NAME=${resolvedAppName}"
      echo "  PORT=${resolvedPort}"
      echo "  ECR=${resolvedEcrImage}"
      echo "  TAG=${resolvedTag}"
      echo "  REGION=${resolvedRegion}"
      echo "  REGISTRY=${resolvedRegistry}"
      echo "  HOST=${resolvedHost}"
      echo "  USER=${resolvedUser}"

      withCredentials([sshUserPrivateKey(
          credentialsId: 'ec2-ssh',
          keyFileVariable: 'EC2_KEYFILE',
          usernameVariable: 'SSH_USER')]) {

        sh """#!/bin/bash
set -eu

# Ensure keyfile exists
echo "Using SSH keyfile: \$EC2_KEYFILE (exists: \$( [ -f \"\$EC2_KEYFILE\" ] && echo yes || echo no ))"
chmod 600 "\$EC2_KEYFILE" || true

echo "About to SSH to ${resolvedUser}@${resolvedHost} and deploy ${resolvedEcrImage}:${resolvedTag}"
echo "Jenkins-layer verified APP_NAME=${resolvedAppName}, PORT=${resolvedPort}"

# Run remote script; Jenkins variables are interpolated into the heredoc below
ssh -o StrictHostKeyChecking=no -i "\$EC2_KEYFILE" "${resolvedUser}@${resolvedHost}" bash -s <<'REMOTE'
#!/bin/bash
set -euo pipefail

# Values interpolated by Jenkins (literal strings)
APP_NAME="${resolvedAppName}"
PORT="${resolvedPort}"
ECR="${resolvedEcrImage}"
TAG="${resolvedTag}"
REGION="${resolvedRegion}"
REGISTRY="${resolvedRegistry}"

echo "REMOTE: Connected. APP_NAME=\$APP_NAME PORT=\$PORT ECR=\$ECR TAG=\$TAG REGION=\$REGION"

# Verify required commands exist on remote
command -v docker >/dev/null 2>&1 || { echo "docker not found on remote host"; exit 2; }
command -v aws >/dev/null 2>&1 || { echo "aws cli not found on remote host"; exit 2; }

echo "REMOTE: Logging in to ECR..."
# If REGION or REGISTRY are empty, aws/dock login may fail — that's expected; check output
if [ -n "\$REGION" ] && [ -n "\$REGISTRY" ]; then
  aws ecr get-login-password --region "\$REGION" | docker login --username AWS --password-stdin "\$REGISTRY"
else
  echo "REMOTE: WARNING - REGION or REGISTRY empty; skipping docker login (you may be using instance role)."
fi

echo "REMOTE: Pulling Docker image: \$ECR:\$TAG"
docker pull "\$ECR:\$TAG"

# Stop & remove existing container if present
if docker ps -a --format '{{.Names}}' | grep -q "^\$APP_NAME\$"; then
  echo "REMOTE: Stopping existing container \$APP_NAME..."
  docker stop "\$APP_NAME" || true
  docker rm "\$APP_NAME" || true
fi

echo "REMOTE: Starting container \$APP_NAME on port \$PORT..."
docker run -d --name "\$APP_NAME" -p "\$PORT:\$PORT" --restart=always "\$ECR:\$TAG"

echo "REMOTE: Pruning old images..."
docker image prune -f >/dev/null 2>&1 || true

echo "REMOTE: Deployment completed successfully."
REMOTE
"""
      } // withCredentials
    } // script
  } // steps
} // stage
  }

  post {
    success {
      echo "✔ Built JAR and pushed ${ECR_IMAGE}:${TAG} (and :latest). Deployed to EC2 ${params.EC2_HOST}."
    }
    failure { echo "✖ Build/Deploy failed" }
  }
}

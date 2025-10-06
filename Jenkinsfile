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
      // Resolve Jenkins params/env into Groovy variables
      def resolvedAppName  = (params?.APP_NAME ?: env.APP_NAME) ?: error("Missing APP_NAME")
      def resolvedPort     = (params?.EXPOSE_PORT ?: env.EXPOSE_PORT) ?: error("Missing EXPOSE_PORT")
      def resolvedEcrImage = (params?.ECR_IMAGE ?: env.ECR_IMAGE) ?: error("Missing ECR_IMAGE")
      def resolvedTag      = (params?.TAG ?: env.TAG) ?: "latest"
      def resolvedRegion   = (params?.AWS_REGION ?: env.AWS_REGION) ?: error("Missing AWS_REGION")
      def resolvedRegistry = (params?.ECR_REGISTRY ?: env.ECR_REGISTRY) ?: error("Missing ECR_REGISTRY")
      def resolvedHost     = (params?.EC2_HOST ?: env.EC2_HOST) ?: error("Missing EC2_HOST")
      def resolvedUser     = (params?.EC2_USER ?: env.EC2_USER) ?: error("Missing EC2_USER")

      echo "Resolved before deploy: APP_NAME=${resolvedAppName}, PORT=${resolvedPort}, HOST=${resolvedHost}"

      withCredentials([sshUserPrivateKey(credentialsId: 'ec2-ssh', keyFileVariable: 'EC2_KEYFILE')]) {

        // Remote shell variables (interpolated by Groovy)
        def remoteVars = """
APP_NAME="${resolvedAppName}"
PORT="${resolvedPort}"
ECR="${resolvedEcrImage}"
TAG="${resolvedTag}"
REGION="${resolvedRegion}"
REGISTRY="${resolvedRegistry}"
"""

        // Remote shell body: use single-quoted Groovy string so $ and \ are literal
        def remoteBody = '''
echo "REMOTE: Connected. APP_NAME=$APP_NAME PORT=$PORT ECR=$ECR TAG=$TAG REGION=$REGION"

_RUN_SUDO=""
if command -v sudo >/dev/null 2>&1 && sudo -n true 2>/dev/null; then
  _RUN_SUDO="sudo"
fi

# Docker installation if missing
if ! command -v docker >/dev/null 2>&1; then
  echo "REMOTE: docker not found. Installing..."
  if [ -n "$_RUN_SUDO" ]; then
    if [ -f /etc/debian_version ] || ( [ -f /etc/os-release ] && grep -Eqi "ubuntu|debian" /etc/os-release ); then
      echo "Installing Docker on Debian/Ubuntu..."
      $_RUN_SUDO apt-get update -y
      $_RUN_SUDO apt-get install -y ca-certificates curl gnupg lsb-release
      mkdir -p /etc/apt/keyrings
      curl -fsSL https://download.docker.com/linux/ubuntu/gpg | $_RUN_SUDO gpg --dearmour -o /etc/apt/keyrings/docker.gpg
      arch=$(dpkg --print-architecture)
      codename=$(. /etc/os-release && echo "$VERSION_CODENAME")
      echo "deb [arch=${arch} signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${codename} stable" | $_RUN_SUDO tee /etc/apt/sources.list.d/docker.list >/dev/null
      $_RUN_SUDO apt-get update -y
      $_RUN_SUDO apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
      $_RUN_SUDO systemctl enable --now docker || true
    elif [ -f /etc/redhat-release ] || ( [ -f /etc/os-release ] && grep -Eqi "amzn|centos|rhel" /etc/os-release ); then
      echo "Installing Docker on RHEL/CentOS/Amazon Linux..."
      $_RUN_SUDO yum install -y yum-utils
      $_RUN_SUDO yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
      $_RUN_SUDO yum install -y docker-ce docker-ce-cli containerd.io || true
      $_RUN_SUDO systemctl enable --now docker || true
    else
      echo "Unsupported distro for Docker install."
      exit 3
    fi
  else
    echo "No sudo: please install docker manually."
    exit 5
  fi
fi

# AWS CLI check (optional install)
if ! command -v aws >/dev/null 2>&1; then
  echo "REMOTE: AWS CLI not found"
fi

# Login to ECR and deploy
if [ -n "$REGION" ] && [ -n "$REGISTRY" ]; then
  aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"
fi

docker pull "$ECR:$TAG"

if docker ps -a --format '{{.Names}}' | grep -q "^$APP_NAME$"; then
  docker stop "$APP_NAME" || true
  docker rm "$APP_NAME" || true
fi

docker run -d --name "$APP_NAME" -p "$PORT:$PORT" --restart=always "$ECR:$TAG"
docker image prune -f >/dev/null 2>&1 || true
echo "REMOTE: Deployment complete."
'''

        // Compose full SSH script (heredoc marker REMOTE must be at column 0)
        def fullSh = """#!/bin/bash
set -eu
chmod 600 "\$EC2_KEYFILE"

ssh -o StrictHostKeyChecking=no -i "\$EC2_KEYFILE" "${resolvedUser}@${resolvedHost}" bash -s <<'REMOTE'
""" + remoteVars + remoteBody + "\nREMOTE\n"

        sh fullSh
      }
    }
  }
}
  }

  post {
    success {
      echo "✔ Built JAR and pushed ${ECR_IMAGE}:${TAG} (and :latest). Deployed to EC2 ${params.EC2_HOST}."
    }
    failure { echo "✖ Build/Deploy failed" }
  }
}

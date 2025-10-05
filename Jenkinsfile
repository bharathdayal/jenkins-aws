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
      // Resolve values in Groovy (params -> env -> default)
      def resolvedAppName = (params.containsKey('APP_NAME') && params.APP_NAME) ? params.APP_NAME : (env.APP_NAME ?: 'myapp')
      def resolvedPort    = (params.containsKey('EXPOSE_PORT') && params.EXPOSE_PORT) ? params.EXPOSE_PORT : (env.EXPOSE_PORT ?: '8080')
      def resolvedEcrImage= (params.containsKey('ECR_IMAGE') && params.ECR_IMAGE) ? params.ECR_IMAGE : (env.ECR_IMAGE ?: "${ECR_IMAGE}")
      def resolvedTag     = (params.containsKey('TAG') && params.TAG) ? params.TAG : (env.TAG ?: "${TAG}")
      def resolvedRegion  = (params.containsKey('AWS_REGION') && params.AWS_REGION) ? params.AWS_REGION : (env.AWS_REGION ?: "${AWS_REGION}")
      def resolvedRegistry= (params.containsKey('ECR_REGISTRY') && params.ECR_REGISTRY) ? params.ECR_REGISTRY : (env.ECR_REGISTRY ?: "${ECR_REGISTRY}")
      def resolvedHost    = (params.containsKey('EC2_HOST') && params.EC2_HOST) ? params.EC2_HOST : (env.EC2_HOST ?: "${params.EC2_HOST}")
      def resolvedUser    = (params.containsKey('EC2_USER') && params.EC2_USER) ? params.EC2_USER : (env.EC2_USER ?: "${params.EC2_USER}")

      // Echo resolved values to pipeline log (for debugging)
      echo "Resolved values: APP_NAME=${resolvedAppName}, PORT=${resolvedPort}, ECR=${resolvedEcrImage}, TAG=${resolvedTag}, REGION=${resolvedRegion}, REGISTRY=${resolvedRegistry}, HOST=${resolvedHost}, USER=${resolvedUser}"

      withCredentials([sshUserPrivateKey(credentialsId: 'ec2-ssh',
                                         keyFileVariable: 'EC2_KEYFILE',
                                         usernameVariable: 'SSH_USER')]) {
sh """#!/bin/bash
set -eu

# show which keyfile Jenkins provided
echo "Using SSH keyfile: \$EC2_KEYFILE (exists: \$( [ -f \"\$EC2_KEYFILE\" ] && echo yes || echo no ))"
chmod 600 "\$EC2_KEYFILE"

# show resolved values again in build log (sanity)
echo "Deploying ${resolvedEcrImage}:${resolvedTag} to ${resolvedHost} as ${resolvedUser}"
echo "APP_NAME=${resolvedAppName} PORT=${resolvedPort} REGION=${resolvedRegion} REGISTRY=${resolvedRegistry}"

# SSH and run remote script; Jenkins variables interpolated above are placed directly into the heredoc
ssh -o StrictHostKeyChecking=no -i "\$EC2_KEYFILE" "${resolvedUser}@${resolvedHost}" bash -s <<-REMOTE
#!/bin/bash
set -eu

# Jenkins-provided values (hard-coded into the remote script)
APP_NAME="${resolvedAppName}"
PORT="${resolvedPort}"
ECR="${resolvedEcrImage}"
TAG="${resolvedTag}"
REGION="${resolvedRegion}"
REGISTRY="${resolvedRegistry}"

echo "Remote: connected, will deploy \$APP_NAME on port \$PORT in region \$REGION"

# verify docker + aws presence
command -v docker >/dev/null 2>&1 || { echo "docker not found on remote host"; exit 2; }
command -v aws >/dev/null 2>&1 || { echo "aws cli not found on remote host"; exit 2; }

echo "Logging in to ECR..."
aws ecr get-login-password --region "\$REGION" | docker login --username AWS --password-stdin "\$REGISTRY"

echo "Pulling image: \$ECR:\$TAG"
docker pull "\$ECR:\$TAG"

# stop & remove existing container if present
if docker ps -a --format '{{.Names}}' | grep -q "^\$APP_NAME\$"; then
  echo "Stopping existing container \$APP_NAME..."
  docker stop "\$APP_NAME" || true
  docker rm "\$APP_NAME" || true
fi

echo "Starting container \$APP_NAME on port \$PORT..."
docker run -d --name "\$APP_NAME" -p "\$PORT:\$PORT" --restart=always "\$ECR:\$TAG"

echo "Pruning old images..."
docker image prune -f >/dev/null 2>&1 || true

echo "Deployment finished successfully."
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

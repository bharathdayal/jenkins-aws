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
        sshagent(credentials: ['ec2-ssh']) {
          sh '''
            set -e

            echo "Logging in to ECR on remote host and deploying ${ECR_IMAGE}:${TAG}"

            ssh -o StrictHostKeyChecking=no ${params.EC2_USER}@${params.EC2_HOST} bash -s <<'REMOTE'
              
              APP_NAME="${APP_NAME}"
              PORT="${EXPOSE_PORT}"
              ECR="${ECR_IMAGE}"
              TAG="${TAG}"
              REGION="${AWS_REGION}"

              # Login to ECR on the EC2 machine (requires AWS CLI + credentials/role on EC2)
              aws ecr get-login-password --region "$REGION" | \
                 docker login --username AWS --password-stdin ${ECR_REGISTRY}

              # Pull the new image
              docker pull "${ECR}:${TAG}"

              # Stop & remove existing container if present
              if docker ps -a --format '{{.Names}}' | grep -q "^${APP_NAME}$"; then
                echo "Stopping existing container ${APP_NAME}..."
                docker stop "${APP_NAME}" || true
                docker rm   "${APP_NAME}" || true
              fi

              # Run the new container
              echo "Starting container ${APP_NAME} on port ${PORT}..."
              docker run -d --name "${APP_NAME}" \
                -p ${PORT}:${PORT} \
                --restart=always \
                "${ECR}:${TAG}"

              # Optionally prune old images (keep current and latest)
              docker image prune -f >/dev/null 2>&1 || true
            REMOTE
          '''
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

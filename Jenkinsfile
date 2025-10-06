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
      // Resolve Jenkins params/env into Groovy variables (fail fast if missing)
      def resolvedAppName  = (params?.APP_NAME ?: env.APP_NAME) ?: error("Missing APP_NAME")
      def resolvedPort     = (params?.EXPOSE_PORT ?: env.EXPOSE_PORT) ?: error("Missing EXPOSE_PORT")
      def resolvedEcrImage = (params?.ECR_IMAGE ?: env.ECR_IMAGE) ?: (this.binding.hasVariable('ECR_IMAGE') ? ECR_IMAGE : '')
      def resolvedTag      = (params?.TAG ?: env.TAG) ?: (this.binding.hasVariable('TAG') ? TAG : 'latest')
      def resolvedRegion   = (params?.AWS_REGION ?: env.AWS_REGION) ?: (this.binding.hasVariable('AWS_REGION') ? AWS_REGION : '')
      def resolvedRegistry = (params?.ECR_REGISTRY ?: env.ECR_REGISTRY) ?: (this.binding.hasVariable('ECR_REGISTRY') ? ECR_REGISTRY : '')
      def resolvedHost     = (params?.EC2_HOST ?: env.EC2_HOST) ?: error("Missing EC2_HOST")
      def resolvedUser     = (params?.EC2_USER ?: env.EC2_USER) ?: error("Missing EC2_USER")

      echo "Resolved before deploy: APP_NAME=${resolvedAppName}, PORT=${resolvedPort}, HOST=${resolvedHost}, USER=${resolvedUser}, ECR=${resolvedEcrImage}, TAG=${resolvedTag}, REGION=${resolvedRegion}, REGISTRY=${resolvedRegistry}"

      withCredentials([sshUserPrivateKey(
          credentialsId: 'ec2-ssh',
          keyFileVariable: 'EC2_KEYFILE',
          usernameVariable: 'SSH_USER')]) {

        // Build the sh script by concatenating the resolved Groovy variables into the heredoc body.
        def remoteScriptHeader =
"""#!/bin/bash
set -euo pipefail

# Jenkins-provided values (injected by Groovy)
"""

        def remoteScriptVars =
"APP_NAME=\"" + resolvedAppName + "\"\n" +
"PORT=\"" + resolvedPort + "\"\n" +
"ECR=\"" + resolvedEcrImage + "\"\n" +
"TAG=\"" + resolvedTag + "\"\n" +
"REGION=\"" + resolvedRegion + "\"\n" +
"REGISTRY=\"" + resolvedRegistry + "\"\n\n"

        def remoteScriptBody =
"""echo "REMOTE: Connected. APP_NAME=\\$APP_NAME PORT=\\$PORT ECR=\\$ECR TAG=\\$TAG REGION=\\$REGION"

# helper: check for passwordless sudo
_RUN_SUDO=""
if command -v sudo >/dev/null 2>&1; then
  if sudo -n true 2>/dev/null; then
    _RUN_SUDO="sudo"
  else
    _RUN_SUDO=""
  fi
fi

# Ensure docker exists, otherwise try to install (requires passwordless sudo)
if command -v docker >/dev/null 2>&1; then
  echo "REMOTE: docker found at \\$(command -v docker)"
else
  echo "REMOTE: docker not found. Attempting install (requires passwordless sudo)..."
  if [ -n "\\$_RUN_SUDO" ]; then
    # Detect distro and install
    if [ -f /etc/debian_version ] || ( [ -f /etc/os-release ] && grep -qi 'ubuntu\\|debian' /etc/os-release ); then
      echo "REMOTE: Installing Docker on Debian/Ubuntu..."
      \\$_RUN_SUDO apt-get update -y
      \\$_RUN_SUDO apt-get install -y ca-certificates curl gnupg lsb-release
      mkdir -p /etc/apt/keyrings
      curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \\$_RUN_SUDO gpg --dearmour -o /etc/apt/keyrings/docker.gpg
      echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo \"\$VERSION_CODENAME\") stable" | \\$_RUN_SUDO tee /etc/apt/sources.list.d/docker.list >/dev/null
      \\$_RUN_SUDO apt-get update -y
      \\$_RUN_SUDO apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
      \\$_RUN_SUDO systemctl enable --now docker || true
    elif [ -f /etc/redhat-release ] || ( [ -f /etc/os-release ] && grep -qi 'amzn\\|centos\\|rhel' /etc/os-release ); then
      echo "REMOTE: Installing Docker on RHEL/CentOS/AmazonLinux..."
      \\$_RUN_SUDO yum install -y yum-utils
      \\$_RUN_SUDO yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
      \\$_RUN_SUDO yum install -y docker-ce docker-ce-cli containerd.io || true
      \\$_RUN_SUDO systemctl enable --now docker || true
    else
      echo "REMOTE: Unsupported distro for automatic Docker install. Please install docker manually."
      exit 3
    fi

    # verify docker after install
    if command -v docker >/dev/null 2>&1; then
      echo "REMOTE: docker installed successfully at \\$(command -v docker)"
    else
      echo "REMOTE: docker install attempted but docker still not found"
      exit 4
    fi
  else
    echo "REMOTE: sudo not available or requires a password. Cannot auto-install docker. Please install docker manually and re-run."
    exit 5
  fi
fi

# Ensure aws cli exists (optional install attempted if sudo available)
if command -v aws >/dev/null 2>&1; then
  echo "REMOTE: aws cli found at \\$(command -v aws)"
else
  echo "REMOTE: aws cli not found. Attempting minimal install (optional; will continue even if install fails)..."
  if [ -n "\\$_RUN_SUDO" ]; then
    TMPDIR=\\$(mktemp -d)
    pushd "\\$TMPDIR" >/dev/null 2>&1 || true
    if curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o awscliv2.zip; then
      unzip -q awscliv2.zip || true
      \\$_RUN_SUDO ./aws/install || true
    fi
    popd >/dev/null 2>&1 || true
    rm -rf "\\$TMPDIR"
    if command -v aws >/dev/null 2>&1; then
      echo "REMOTE: aws cli installed successfully"
    else
      echo "REMOTE: aws cli not installed; continuing — ensure instance role has ECR access"
    fi
  else
    echo "REMOTE: no sudo for aws install; continuing — ensure instance role has ECR access"
  fi
fi

# Login to ECR if region & registry provided, otherwise assume instance role for access
if [ -n "\\$REGION" ] && [ -n "\\$REGISTRY" ]; then
  echo "REMOTE: Logging in to ECR..."
  aws ecr get-login-password --region "\\$REGION" | docker login --username AWS --password-stdin "\\$REGISTRY"
else
  echo "REMOTE: REGION or REGISTRY empty — skipping 'aws ecr login' (assuming instance IAM role or manual login)."
fi

echo "REMOTE: Pulling image \\$ECR:\\$TAG"
docker pull "\\$ECR:\\$TAG"

# Stop & remove existing container if present
if docker ps -a --format '{{.Names}}' | grep -q "^\\\\$APP_NAME\\\\$"; then
  echo "REMOTE: Stopping existing container \\$APP_NAME..."
  docker stop "\\$APP_NAME" || true
  docker rm "\\$APP_NAME" || true
fi

echo "REMOTE: Starting container \\$APP_NAME on port \\$PORT..."
docker run -d --name "\\$APP_NAME" -p "\\$PORT:\\$PORT" --restart=always "\\$ECR:\\$TAG"

echo "REMOTE: Pruning old images..."
docker image prune -f >/dev/null 2>&1 || true

echo "REMOTE: Deployment finished successfully."
"""

        // Compose full script that will be fed to sh on Jenkins agent.
        // Note: we must carefully place the heredoc end marker (REMOTE) at column 0.
        def fullSh = """#!/bin/bash
set -eu

echo "Using SSH keyfile: \$EC2_KEYFILE"
chmod 600 "\$EC2_KEYFILE" || true

echo "About to SSH to ${resolvedUser}@${resolvedHost} and deploy ${resolvedEcrImage}:${resolvedTag}"
echo "Jenkins-verified APP_NAME=${resolvedAppName}, PORT=${resolvedPort}"

ssh -o StrictHostKeyChecking=no -i "\$EC2_KEYFILE" "${resolvedUser}@${resolvedHost}" bash -s <<'REMOTE'
""" + remoteScriptHeader + remoteScriptVars + remoteScriptBody + "\nREMOTE\n"

        // Run the assembled script; Groovy will not see any ${...} inside the remote script body
        sh fullSh
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

# Backend GitHub Actions CI/CD Setup

This document describes the automated CI/CD pipeline setup for the Expensia backend using GitHub Actions.

## Workflows Overview

### 1. Backend CI (`ci.yml`)
- **Trigger**: All pushes to any branch
- **Purpose**: Build, test, and validate backend code
- **Actions**:
  - Runs unit tests with Maven
  - Builds application package
  - Generates test reports
  - Checks code coverage with Jacoco
  - Comments test results on PRs

### 2. Backend Deployment (`deploy.yml`)
- **Trigger**: Pushes to `main` or `development` branches
- **Purpose**: Deploy backend to Google Cloud Run
- **Actions**:
  - Builds and tests the application
  - Creates Docker image
  - Pushes to Google Container Registry
  - Deploys to Cloud Run (production or preview)
  - Runs health checks
  - Cleans up old preview deployments

## Required Secrets

Add these secrets in your backend repository's GitHub settings (`Settings > Secrets and variables > Actions`):

### Required GitHub Secrets (Only sensitive data)
```
GCP_SERVICE_ACCOUNT_KEY     # Service account JSON key for GCP authentication
GEMINI_API_KEY             # Google Gemini AI API key (sensitive)
```

### Public Configuration (No secrets needed)
These values are configured directly in the workflow files:
- `GOOGLE_CLIENT_ID` - Public OAuth2 client ID
- `PROJECT_ID` - Google Cloud project ID (expensia-471415)
- `SERVICE_NAME` - Cloud Run service name (expensia-backend)
- `REGION` - Deployment region (us-central1)
- All timeout, memory, and scaling settings

### Already in Google Cloud Secret Manager
These are automatically accessed by Cloud Run:
- `MONGODB_URI` - Database connection string
- `GOOGLE_CLIENT_SECRET` - OAuth2 client secret  
- `JWT_SECRET` - JWT signing key

## GCP Service Account Setup

1. **Create Service Account**:
   ```bash
   gcloud iam service-accounts create github-actions-backend \
     --description="Service account for Backend GitHub Actions" \
     --display-name="GitHub Actions Backend"
   ```

2. **Grant Required Permissions**:
   ```bash
   # Cloud Run permissions
   gcloud projects add-iam-policy-binding expensia-471415 \
     --member="serviceAccount:github-actions-backend@expensia-471415.iam.gserviceaccount.com" \
     --role="roles/run.admin"
   
   # Container Registry permissions
   gcloud projects add-iam-policy-binding expensia-471415 \
     --member="serviceAccount:github-actions-backend@expensia-471415.iam.gserviceaccount.com" \
     --role="roles/storage.admin"
   
   # Service Account User (for Cloud Run)
   gcloud projects add-iam-policy-binding expensia-471415 \
     --member="serviceAccount:github-actions-backend@expensia-471415.iam.gserviceaccount.com" \
     --role="roles/iam.serviceAccountUser"
   ```

3. **Create and Download Key**:
   ```bash
   gcloud iam service-accounts keys create github-actions-backend-key.json \
     --iam-account=github-actions-backend@expensia-471415.iam.gserviceaccount.com
   ```

4. **Add Key to GitHub Secrets**:
   - Copy the entire content of `github-actions-backend-key.json`
   - Add as `GCP_SERVICE_ACCOUNT_KEY` secret in the backend repository

## Deployment Environments

### Production
- **URL**: `https://expensia-backend-expensia-471415.us-central1.run.app`
- **Trigger**: Push to `main` branch
- **Configuration**: Full production settings with autoscaling

### Development
- **URL**: `https://expensia-backend-dev-expensia-471415.us-central1.run.app`
- **Trigger**: Push to `development` branch
- **Configuration**: Development settings with limited resources

### Preview (PR)
- **URL**: `https://expensia-backend-pr-{PR_NUMBER}-expensia-471415.us-central1.run.app`
- **Trigger**: Pull request creation/update
- **Configuration**: Minimal resources, automatic cleanup after 7 days

## Workflow Features

### 🚀 Automatic Deployments
- **Main Branch**: Deploys to production Cloud Run service
- **Development Branch**: Deploys to development Cloud Run service
- **Pull Requests**: Creates temporary preview deployments

### 🧪 Testing & Quality
- Runs comprehensive test suite
- Generates code coverage reports with Jacoco
- Automatically comments test results on PRs
- Fails deployment if tests fail

### 🐳 Docker Optimization
- Multi-stage Docker build for smaller images
- Caches Maven dependencies for faster builds
- Tags images with Git SHA for traceability

### 🧹 Cleanup & Management
- Automatically cleans up preview deployments after 7 days
- Provides deployment URLs in PR comments
- Removes old Docker images to save storage costs

### 📊 Monitoring & Health Checks
- Performs health checks after deployment at `/api/health`
- Fails workflow if service doesn't respond
- Provides deployment status and URLs

## File Structure

```
backend/
├── .github/
│   ├── workflows/
│   │   ├── ci.yml              # Continuous Integration
│   │   └── deploy.yml          # Deployment Pipeline
│   └── CICD_SETUP.md          # This documentation
├── src/                       # Java source code
├── Dockerfile                 # Container configuration
├── pom.xml                   # Maven configuration (includes Jacoco)
└── deploy.sh                 # Manual deployment script
```

## Monitoring Deployments

1. **GitHub Actions Tab**: View workflow runs and logs in the backend repository
2. **Google Cloud Console**: Monitor Cloud Run services and logs
3. **Health Endpoints**: 
   - Production: `https://expensia-backend-expensia-471415.us-central1.run.app/api/health`
   - Development: `https://expensia-backend-dev-expensia-471415.us-central1.run.app/api/health`

## Quick Setup Checklist

1. ✅ Add `GCP_SERVICE_ACCOUNT_KEY` to GitHub Secrets
2. ✅ Add `GEMINI_API_KEY` to GitHub Secrets  
3. ✅ Ensure Google Cloud Secret Manager has the required secrets
4. ✅ Grant service account permissions to access secrets
5. ✅ Push to `main` branch to trigger first deployment
6. ✅ Create a PR to test preview deployment feature

## Troubleshooting

### Common Issues

1. **Authentication Errors**:
   - Verify GCP service account has correct permissions
   - Check `GCP_SERVICE_ACCOUNT_KEY` secret is valid JSON

2. **Build Failures**:
   - Check Maven dependencies and Java version compatibility
   - Verify tests pass locally before pushing

3. **Deployment Failures**:
   - Check Cloud Run quotas and limits
   - Verify Docker image builds successfully
   - Check environment variables and secrets access

### Useful Commands

```bash
# View backend repository workflows
gh run list --repo [your-username]/[backend-repo-name]

# View Cloud Run services
gcloud run services list --region=us-central1

# View service logs
gcloud run services logs read expensia-backend --region=us-central1

# Manual deployment (if needed)
./deploy.sh
```
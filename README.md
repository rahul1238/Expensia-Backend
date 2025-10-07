# Expensia Backend

A Spring Boot REST API for the Expensia expense tracking application.

## Features

- JWT-based authentication
- Google OAuth2 integration
- Gmail transaction import
- AI-powered transaction categorization
- MongoDB data persistence
- RESTful API endpoints

## Prerequisites

- Java 21+
- Maven 3.6+
- MongoDB instance
- Google Cloud Platform project with OAuth2 configured

## Environment Variables

Create a `.env` file in the project root with the following variables:

```env
# Database
MONGODB_URI=your_mongodb_connection_string

# Google OAuth2
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret

# JWT
JWT_SECRET=your_jwt_secret
JWT_ACCESS_EXP=3600000
JWT_REFRESH_EXP=604800000

# CORS and URLs
APP_CORS_ALLOWED_ORIGINS=https://expensia.live
FRONTEND_BASE_URL=https://expensia.live
OAUTH2_REDIRECT_URIS=https://expensia.live/auth/callback

# AI Integration
GEMINI_API_KEY=your_gemini_api_key
```

## Running Locally

1. Install dependencies:
   ```bash
   mvn clean install
   ```

2. Run the application:
   ```bash
   mvn spring-boot:run
   ```

The API will be available at `http://localhost:8080`

## Deployment

### Google Cloud Run

Use the provided deployment script:

```bash
# Set required environment variables
export GOOGLE_CLIENT_ID=your_client_id
export GEMINI_API_KEY=your_api_key
export SERVICE_NAME=expensia-backend
export PROJECT_ID=your_project_id

# Deploy
./deploy.sh
```

## API Documentation

The API provides endpoints for:
- Authentication (`/api/auth/*`)
- User management (`/api/users/*`)
- Transaction operations (`/api/transactions/*`)
- Gmail integration (`/api/gmail/*`)

## Health Check

- Health endpoint: `/api/health`
- Returns application status and basic metrics

## Security

- JWT tokens for stateless authentication
- OAuth2 integration with Google
- CORS configuration for cross-origin requests
- Input validation on all endpoints
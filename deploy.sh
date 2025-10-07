#!/bin/bash

echo "🚀 Deploying Expensia Backend"
echo "==============================="

# Check if required environment variables are set
if [ -z "$GOOGLE_CLIENT_ID" ] || [ -z "$GEMINI_API_KEY" ] || [ -z "$SERVICE_NAME" ] || [ -z "$PROJECT_ID" ]; then
    echo "❌ Error: Required environment variables are not set."
    echo "Please set: GOOGLE_CLIENT_ID, GEMINI_API_KEY, SERVICE_NAME, PROJECT_ID"
    exit 1
fi

# Deploy with configuration from environment variables
echo "Deploying $SERVICE_NAME to Google Cloud Run..."

gcloud run services update $SERVICE_NAME \
  --region us-central1 \
  --set-secrets="MONGODB_URI=mongodb-uri:latest,GOOGLE_CLIENT_SECRET=google-client-secret:latest,JWT_SECRET=jwt-secret:latest" \
  --set-env-vars="GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID,JWT_ACCESS_EXP=3600000,JWT_REFRESH_EXP=604800000,OAUTH2_REDIRECT_URIS=https://expensia.live/auth/callback,GEMINI_API_KEY=$GEMINI_API_KEY,APP_CORS_ALLOWED_ORIGINS=https://expensia.live,APP_BASE_URL=https://$SERVICE_NAME-$PROJECT_ID.us-central1.run.app,FRONTEND_BASE_URL=https://expensia.live"

if [ $? -eq 0 ]; then
    echo "✅ Deployment completed successfully!"
    echo ""
    echo "🔗 Service URL: https://$SERVICE_NAME-$PROJECT_ID.us-central1.run.app"
    echo "🔗 Frontend URL: https://expensia.live"
    echo ""
    echo "🧪 Testing the deployment..."
    echo "Health check:"
    curl -s "https://$SERVICE_NAME-$PROJECT_ID.us-central1.run.app/api/health" | jq . || echo "Health endpoint responded"
else
    echo "❌ Deployment failed!"
    echo "Check the logs at: https://console.cloud.google.com/logs/viewer?project=$PROJECT_ID&resource=cloud_run_revision"
    exit 1
fi
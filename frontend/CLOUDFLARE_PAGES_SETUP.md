# Cloudflare Pages Deployment Setup

This guide explains how to deploy the Electoral Control frontend to Cloudflare Pages with runtime environment configuration.

## Overview

The frontend is deployed as a static site to Cloudflare Pages, with API URL and Sentry DSN configured at **runtime** (not at build time). This allows the same build artifact to be deployed to different environments (development, staging, production) without rebuilding.

## Architecture

```
┌─────────────────────────┐
│  Cloudflare Pages       │
│  (static hosting)       │
└──────────┬──────────────┘
           │
           │ serves
           ▼
┌─────────────────────────┐
│  Build output: www/     │
│  - index.html           │
│  - main.*.js            │
│  - assets/config.json   │ ◄─── Runtime config (NOT cached)
│  - ngsw-worker.js       │
└─────────────────────────┘
           │
           │ at app init
           ▼
┌─────────────────────────┐
│  ConfigService.load()   │
│  reads config.json      │
│  sets API_URL, DSN      │
└─────────────────────────┘
```

## Configuration File

The runtime configuration is stored in `frontend/app/src/assets/config.json`:

```json
{
  "apiUrl": "https://api.example.com",
  "sentryDsn": "https://key@sentry.example.com/123"
}
```

### Key Points

- **config.json is NOT cached** by the service worker (`ngsw-config.json` excludes it)
- The browser fetches it **fresh** on every page load
- If the API or Sentry URL changes in deployment, users see the update immediately (no rebuild needed)

## Deployment Steps

### 1. Build the Application

```bash
cd frontend/app
pnpm build
```

Output: `dist/` with the final build.

### 2. Create config.json

Before uploading to Cloudflare Pages, create `www/assets/config.json` with the target environment values:

```bash
# For development
cat > www/assets/config.json << EOF
{
  "apiUrl": "http://localhost:8080",
  "sentryDsn": "https://dev-key@sentry.dev.example.com/456"
}
EOF

# For staging
cat > www/assets/config.json << EOF
{
  "apiUrl": "https://api.staging.example.com",
  "sentryDsn": "https://staging-key@sentry.example.com/789"
}
EOF

# For production
cat > www/assets/config.json << EOF
{
  "apiUrl": "https://api.example.com",
  "sentryDsn": "https://prod-key@sentry.example.com/123"
}
EOF
```

### 3. Upload to Cloudflare Pages

Using Wrangler CLI:

```bash
# Install Wrangler (if not already installed)
npm install -g wrangler

# Deploy the www/ directory
wrangler pages deploy www/
```

Or use the Cloudflare Pages UI:
1. Connect your Git repository
2. Build command: `cd frontend/app && pnpm build`
3. Build output directory: `frontend/app/dist`
4. Add environment variable hook (see below)

### 4. Environment Variable Hook (CI/CD)

To inject config.json dynamically during deployment, add a build step in your CI/CD:

**GitHub Actions example:**

```yaml
name: Deploy to Cloudflare Pages

on:
  push:
    branches:
      - main

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - uses: pnpm/action-setup@v2
      - uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: 'pnpm'
      
      - name: Install dependencies
        run: cd frontend/app && pnpm install
      
      - name: Build
        run: cd frontend/app && pnpm build
      
      - name: Create runtime config
        run: |
          mkdir -p frontend/app/dist/assets
          cat > frontend/app/dist/assets/config.json << EOF
{
  "apiUrl": "${{ secrets.API_URL }}",
  "sentryDsn": "${{ secrets.SENTRY_DSN }}"
}
          EOF
      
      - name: Deploy to Cloudflare Pages
        uses: cloudflare/wrangler-action@v3
        with:
          apiToken: ${{ secrets.CLOUDFLARE_API_TOKEN }}
          accountId: ${{ secrets.CLOUDFLARE_ACCOUNT_ID }}
          command: pages deploy frontend/app/dist --project-name=electoral-control-frontend
```

### 5. Verify Deployment

1. **Check config.json is accessible:**
   ```bash
   curl https://your-pages-url/assets/config.json
   ```
   Should return:
   ```json
   {
     "apiUrl": "https://api.example.com",
     "sentryDsn": "https://key@sentry.example.com/123"
   }
   ```

2. **Check app loads correctly:**
   - Open the deployed URL in a browser
   - Open DevTools → Network tab
   - Verify that requests to the API use the correct base URL
   - Check console for Sentry initialization message

3. **Test API calls work:**
   - Log in to the application
   - Verify that API calls reach the correct backend

## Troubleshooting

### "No se pudo cargar la configuración electoral"

**Cause:** The API URL is pointing to the wrong host or is not accessible.

**Check:**
```bash
# From your browser console:
console.log('API URL:', configService.getApiUrl())
# Should match your backend URL
```

**Fix:**
1. Verify `API_URL` environment variable is set correctly
2. Check CORS headers on your API server
3. Verify the API server is running and accessible

### Service Worker Serving Old config.json

**Cause:** `config.json` was cached by the service worker.

**Fix:**
1. Verify `ngsw-config.json` excludes `/assets/config.json`:
   ```json
   {
     "assetGroups": [
       {
         "name": "app",
         "resources": {
           "files": [
             "/favicon.ico",
             "/index.html",
             "/**/*.js",
             "/**/*.css"
           ],
           "urls": [
             "/"
           ]
         }
       },
       {
         "name": "assets",
         "resources": {
           "files": [
             "/assets/**",
             "!**/assets/config.json"  // Exclude runtime config
           ]
         }
       }
     ]
   }
   ```

2. Clear browser cache and unregister service worker:
   - DevTools → Application → Service Workers → Unregister
   - Clear Storage → Clear site data

### API Calls to Wrong Host

**Cause:** `ConfigService.getApiUrl()` is not being used consistently.

**Check:**
```bash
# From frontend root:
grep -r "environment.apiUrl" app/src/app --exclude-dir=node_modules
```

**Fix:** All services should import `ConfigService` and use `ConfigService.getApiUrl()` instead of `environment.apiUrl`.

## Local Development

For local development, the app uses `environment.development.ts` which points to `localhost:8080`:

```typescript
// frontend/app/src/environments/environment.development.ts
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080',
  sentryDsn: undefined, // No Sentry in dev
};
```

**No build needed** when developing locally—changes are reflected instantly via `ng serve`.

## Production Checklist

Before deploying to production:

- [ ] Verify `API_URL` points to your production API server
- [ ] Verify `SENTRY_DSN` points to your production Sentry project
- [ ] Test login flow end-to-end
- [ ] Verify offline sync works (Service Worker registered)
- [ ] Monitor Sentry dashboard for errors
- [ ] Check CloudFlare Analytics for requests

## References

- [Cloudflare Pages Documentation](https://developers.cloudflare.com/pages/)
- [ConfigService Implementation](./app/src/app/core/config/config.service.ts)
- [Service Worker Config](./app/ngsw-config.json)
- [Sentry Documentation](https://docs.sentry.io/)

/**
 * Priority order for configuration:
 * 1. Cloudflare Pages env var (NG_APP_API_URL)
 * 2. assets/config.json (dynamic, no rebuild needed)
 * 3. Hardcoded fallback
 */
declare const window: {
  __config?: {
    apiUrl?: string;
    sentryDsn?: string;
  };
} & Window;

export const environment = {
  production: true,
  // Priority: ENV → config.json → hardcoded
  apiUrl:
    (import.meta.env['NG_APP_API_URL'] as string | undefined) ||
    window.__config?.apiUrl ||
    'https://api.brydyan.me/api/v1',
  // Sentry error tracking and crash reporting (dev: 100% sampling, prod: 10%)
  sentryDsn: window.__config?.sentryDsn || '',
};

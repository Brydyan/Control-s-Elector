import { Injectable, inject } from '@angular/core';
import { CookieConsentService } from './cookie-consent.service';

/**
 * Analytics service that guards initialization of tracking services.
 * Respects cookie consent before initializing Google Analytics.
 * Sentry is always initialized but respects consent for custom events.
 */
@Injectable({
  providedIn: 'root',
})
export class AnalyticsService {
  private consentService = inject(CookieConsentService);
  private gaInitialized = false;

  /**
   * Initialize Google Analytics if user has given analytics consent.
   * Call this early in the app bootstrap.
   */
  initializeGoogleAnalytics(measurementId: string): void {
    if (!this.canUseAnalytics()) {
      console.debug('[Analytics] Google Analytics skipped: no user consent');
      return;
    }

    if (this.gaInitialized) {
      return;
    }

    // Load Google Analytics script only if consent given
    this.loadGoogleAnalyticsScript(measurementId);
    this.gaInitialized = true;
  }

  /**
   * Track a page view to Google Analytics
   */
  trackPageView(path: string, title?: string): void {
    if (!this.canUseAnalytics()) {
      return;
    }

    this.sendGAEvent('page_view', {
      page_path: path,
      page_title: title,
    });
  }

  /**
   * Track a custom event to Google Analytics
   */
  trackEvent(
    eventName: string,
    eventData?: Record<string, string | number | boolean>,
  ): void {
    if (!this.canUseAnalytics()) {
      return;
    }

    this.sendGAEvent(eventName, eventData);
  }

  /**
   * Check if analytics consent is granted
   */
  canUseAnalytics(): boolean {
    return this.consentService.hasAnalyticsConsent();
  }

  /**
   * Load Google Analytics script
   * Using gtag.js approach
   */
  private loadGoogleAnalyticsScript(measurementId: string): void {
    // Create gtag script
    const script = document.createElement('script');
    script.async = true;
    script.src = `https://www.googletagmanager.com/gtag/js?id=${measurementId}`;
    document.head.appendChild(script);

    // Initialize gtag
    window.dataLayer = window.dataLayer || [];
    function gtag(...args: any[]): void {
      (window.dataLayer as any).push(arguments);
    }
    (window as any).gtag = gtag;
    gtag('js', new Date());
    gtag('config', measurementId);
  }

  /**
   * Send event to Google Analytics
   */
  private sendGAEvent(
    eventName: string,
    eventData?: Record<string, string | number | boolean>,
  ): void {
    if (typeof (window as any).gtag !== 'function') {
      console.warn('[Analytics] gtag not available');
      return;
    }

    (window as any).gtag('event', eventName, eventData);
  }
}

// Type definitions for Google Analytics
declare global {
  interface Window {
    dataLayer?: any[];
    gtag?: (...args: any[]) => void;
  }
}

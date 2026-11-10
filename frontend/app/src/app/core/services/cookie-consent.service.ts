import { Injectable } from '@angular/core';

/**
 * Consent preferences structure
 */
export interface ConsentPreferences {
  essential: boolean; // Always true, required
  analytics: boolean; // Google Analytics, Sentry events
  marketing: boolean; // Tracking pixels
  preferences: boolean; // Remember settings
}

/**
 * Stored consent object
 */
export interface StoredConsent {
  version: number;
  timestamp: number;
  consent_given: boolean;
  preferences: ConsentPreferences;
}

/**
 * Service to manage cookie consent and preferences.
 * Stores consent in localStorage with key "cookie_consent".
 */
@Injectable({
  providedIn: 'root',
})
export class CookieConsentService {
  private readonly STORAGE_KEY = 'cookie_consent';
  private readonly CURRENT_VERSION = 1;

  /**
   * Get current consent from localStorage
   */
  getConsent(): StoredConsent | null {
    const stored = localStorage.getItem(this.STORAGE_KEY);
    if (!stored) {
      return null;
    }
    try {
      return JSON.parse(stored) as StoredConsent;
    } catch {
      return null;
    }
  }

  /**
   * Set consent preferences and persist to localStorage
   */
  setConsent(preferences: Partial<ConsentPreferences>): StoredConsent {
    const merged: ConsentPreferences = {
      essential: true, // Always required
      analytics: preferences.analytics ?? false,
      marketing: preferences.marketing ?? false,
      preferences: preferences.preferences ?? false,
    };

    const consent: StoredConsent = {
      version: this.CURRENT_VERSION,
      timestamp: Date.now(),
      consent_given: true,
      preferences: merged,
    };

    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(consent));
    return consent;
  }

  /**
   * Check if user has previously given consent
   */
  hasGivenConsent(): boolean {
    const consent = this.getConsent();
    return consent !== null && consent.consent_given === true;
  }

  /**
   * Check if analytics tracking is allowed
   */
  hasAnalyticsConsent(): boolean {
    const consent = this.getConsent();
    return consent?.preferences.analytics === true;
  }

  /**
   * Check if marketing tracking is allowed
   */
  hasMarketingConsent(): boolean {
    const consent = this.getConsent();
    return consent?.preferences.marketing === true;
  }

  /**
   * Check if preferences persistence is allowed
   */
  hasPreferencesConsent(): boolean {
    const consent = this.getConsent();
    return consent?.preferences.preferences === true;
  }

  /**
   * Clear consent and remove from localStorage
   */
  clearConsent(): void {
    localStorage.removeItem(this.STORAGE_KEY);
  }

  /**
   * Get current preferences
   */
  getPreferences(): ConsentPreferences | null {
    const consent = this.getConsent();
    return consent?.preferences ?? null;
  }
}

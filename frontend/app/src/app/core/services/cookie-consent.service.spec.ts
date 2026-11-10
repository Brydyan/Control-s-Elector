import { TestBed } from '@angular/core/testing';
import {
  CookieConsentService,
  ConsentPreferences,
  StoredConsent,
} from './cookie-consent.service';

describe('CookieConsentService', () => {
  let service: CookieConsentService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(CookieConsentService);
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  describe('getConsent', () => {
    it('should return null when no consent stored', () => {
      expect(service.getConsent()).toBeNull();
    });

    it('should retrieve stored consent from localStorage', () => {
      const prefs: ConsentPreferences = {
        essential: true,
        analytics: true,
        marketing: false,
        preferences: true,
      };
      service.setConsent(prefs);

      const retrieved = service.getConsent();
      expect(retrieved).not.toBeNull();
      expect(retrieved?.consent_given).toBe(true);
      expect(retrieved?.preferences).toEqual({
        essential: true,
        analytics: true,
        marketing: false,
        preferences: true,
      });
    });

    it('should return null if stored data is invalid JSON', () => {
      localStorage.setItem('cookie_consent', 'invalid json');
      expect(service.getConsent()).toBeNull();
    });
  });

  describe('setConsent', () => {
    it('should persist consent to localStorage', () => {
      const prefs: Partial<ConsentPreferences> = {
        analytics: true,
        marketing: false,
      };
      service.setConsent(prefs);

      const stored = localStorage.getItem('cookie_consent');
      expect(stored).not.toBeNull();
      const parsed = JSON.parse(stored!);
      expect(parsed.consent_given).toBe(true);
      expect(parsed.preferences.analytics).toBe(true);
    });

    it('should set essential to true regardless of input', () => {
      service.setConsent({
        analytics: false,
        marketing: false,
        preferences: false,
      });

      const consent = service.getConsent();
      expect(consent?.preferences.essential).toBe(true);
    });

    it('should include version and timestamp', () => {
      service.setConsent({});
      const consent = service.getConsent();

      expect(consent?.version).toBe(1);
      expect(consent?.timestamp).toBeGreaterThan(0);
      expect(typeof consent?.timestamp).toBe('number');
    });

    it('should default unspecified preferences to false', () => {
      service.setConsent({ analytics: true });

      const consent = service.getConsent();
      expect(consent?.preferences.essential).toBe(true);
      expect(consent?.preferences.analytics).toBe(true);
      expect(consent?.preferences.marketing).toBe(false);
      expect(consent?.preferences.preferences).toBe(false);
    });
  });

  describe('hasGivenConsent', () => {
    it('should return false when no consent stored', () => {
      expect(service.hasGivenConsent()).toBe(false);
    });

    it('should return true after setConsent is called', () => {
      service.setConsent({ analytics: true });
      expect(service.hasGivenConsent()).toBe(true);
    });

    it('should return false if consent.consent_given is false', () => {
      const malformed: StoredConsent = {
        version: 1,
        timestamp: Date.now(),
        consent_given: false,
        preferences: {
          essential: true,
          analytics: false,
          marketing: false,
          preferences: false,
        },
      };
      localStorage.setItem('cookie_consent', JSON.stringify(malformed));
      expect(service.hasGivenConsent()).toBe(false);
    });
  });

  describe('hasAnalyticsConsent', () => {
    it('should return false when no consent stored', () => {
      expect(service.hasAnalyticsConsent()).toBe(false);
    });

    it('should return true when analytics is true', () => {
      service.setConsent({ analytics: true });
      expect(service.hasAnalyticsConsent()).toBe(true);
    });

    it('should return false when analytics is false', () => {
      service.setConsent({ analytics: false });
      expect(service.hasAnalyticsConsent()).toBe(false);
    });
  });

  describe('hasMarketingConsent', () => {
    it('should return false when no consent stored', () => {
      expect(service.hasMarketingConsent()).toBe(false);
    });

    it('should return true when marketing is true', () => {
      service.setConsent({ marketing: true });
      expect(service.hasMarketingConsent()).toBe(true);
    });

    it('should return false when marketing is false', () => {
      service.setConsent({ marketing: false });
      expect(service.hasMarketingConsent()).toBe(false);
    });
  });

  describe('hasPreferencesConsent', () => {
    it('should return false when no consent stored', () => {
      expect(service.hasPreferencesConsent()).toBe(false);
    });

    it('should return true when preferences is true', () => {
      service.setConsent({ preferences: true });
      expect(service.hasPreferencesConsent()).toBe(true);
    });

    it('should return false when preferences is false', () => {
      service.setConsent({ preferences: false });
      expect(service.hasPreferencesConsent()).toBe(false);
    });
  });

  describe('clearConsent', () => {
    it('should remove consent from localStorage', () => {
      service.setConsent({ analytics: true });
      expect(service.hasGivenConsent()).toBe(true);

      service.clearConsent();
      expect(service.getConsent()).toBeNull();
      expect(service.hasGivenConsent()).toBe(false);
    });
  });

  describe('getPreferences', () => {
    it('should return null when no consent stored', () => {
      expect(service.getPreferences()).toBeNull();
    });

    it('should return preferences from stored consent', () => {
      const prefs: ConsentPreferences = {
        essential: true,
        analytics: true,
        marketing: false,
        preferences: true,
      };
      service.setConsent(prefs);

      const retrieved = service.getPreferences();
      expect(retrieved).toEqual(prefs);
    });
  });
});

import { TestBed } from '@angular/core/testing';
import { AnalyticsService } from './analytics.service';
import { CookieConsentService } from './cookie-consent.service';

describe('AnalyticsService', () => {
  let service: AnalyticsService;
  let consentService: CookieConsentService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AnalyticsService, CookieConsentService],
    });
    service = TestBed.inject(AnalyticsService);
    consentService = TestBed.inject(CookieConsentService);
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
    // Clean up gtag from window
    delete (window as any).gtag;
    delete (window as any).dataLayer;
  });

  describe('canUseAnalytics', () => {
    it('should return false when no analytics consent', () => {
      expect(service.canUseAnalytics()).toBe(false);
    });

    it('should return true when analytics consent given', () => {
      consentService.setConsent({ analytics: true });
      expect(service.canUseAnalytics()).toBe(true);
    });

    it('should return false when analytics consent explicitly denied', () => {
      consentService.setConsent({ analytics: false });
      expect(service.canUseAnalytics()).toBe(false);
    });
  });

  describe('initializeGoogleAnalytics', () => {
    it('should not load script if no analytics consent', () => {
      const initialScriptCount = document.scripts.length;
      service.initializeGoogleAnalytics('G-XXXXX');

      // No new script added
      expect(document.scripts.length).toBe(initialScriptCount);
    });

    it('should load Google Analytics script if consent given', () => {
      consentService.setConsent({ analytics: true });
      const initialScriptCount = document.scripts.length;

      service.initializeGoogleAnalytics('G-TEST123');

      // Script was added
      expect(document.scripts.length).toBeGreaterThan(initialScriptCount);

      // Find the GA script
      const gaScript = Array.from(document.scripts).find((s) =>
        s.src.includes('googletagmanager.com/gtag'),
      );
      expect(gaScript).toBeDefined();
      expect(gaScript?.src).toContain('G-TEST123');
    });

    it('should initialize gtag function', () => {
      consentService.setConsent({ analytics: true });
      service.initializeGoogleAnalytics('G-TEST123');

      expect(typeof (window as any).gtag).toBe('function');
    });

    it('should not initialize twice', () => {
      consentService.setConsent({ analytics: true });
      const initialScriptCount = document.scripts.length;

      service.initializeGoogleAnalytics('G-TEST123');
      const afterFirstInit = document.scripts.length;

      service.initializeGoogleAnalytics('G-TEST123');
      const afterSecondInit = document.scripts.length;

      // No additional scripts added on second call
      expect(afterFirstInit).toBeGreaterThan(initialScriptCount);
      expect(afterSecondInit).toBe(afterFirstInit);
    });
  });

  describe('trackPageView', () => {
    it('should not track if no analytics consent', () => {
      const gtagSpy = jasmine.createSpy('gtag');
      (window as any).gtag = gtagSpy;

      service.trackPageView('/home');

      expect(gtagSpy).not.toHaveBeenCalled();
    });

    it('should track page view if consent given', () => {
      consentService.setConsent({ analytics: true });
      const gtagSpy = jasmine.createSpy('gtag');
      (window as any).gtag = gtagSpy;

      service.trackPageView('/home', 'Home Page');

      expect(gtagSpy).toHaveBeenCalledWith('event', 'page_view', {
        page_path: '/home',
        page_title: 'Home Page',
      });
    });
  });

  describe('trackEvent', () => {
    it('should not track if no analytics consent', () => {
      const gtagSpy = jasmine.createSpy('gtag');
      (window as any).gtag = gtagSpy;

      service.trackEvent('user_signup', { method: 'email' });

      expect(gtagSpy).not.toHaveBeenCalled();
    });

    it('should track event if consent given', () => {
      consentService.setConsent({ analytics: true });
      const gtagSpy = jasmine.createSpy('gtag');
      (window as any).gtag = gtagSpy;

      service.trackEvent('user_signup', { method: 'email' });

      expect(gtagSpy).toHaveBeenCalledWith('event', 'user_signup', {
        method: 'email',
      });
    });

    it('should track event with no data if consent given', () => {
      consentService.setConsent({ analytics: true });
      const gtagSpy = jasmine.createSpy('gtag');
      (window as any).gtag = gtagSpy;

      service.trackEvent('page_scroll');

      expect(gtagSpy).toHaveBeenCalledWith('event', 'page_scroll', undefined);
    });
  });

  describe('Mutation Test: Consent Check', () => {
    it('should fail if consent check is removed (analytics loads without consent)', () => {
      // This test ensures analytics respects consent
      // If the hasAnalyticsConsent check is removed, this test fails
      const gtagSpy = jasmine.createSpy('gtag');
      (window as any).gtag = gtagSpy;

      service.trackEvent('test_event');

      // Without consent, gtag should not be called
      expect(gtagSpy).not.toHaveBeenCalled();
    });
  });
});

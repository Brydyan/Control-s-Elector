import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { CookieBannerComponent } from './cookie-banner.component';
import { CookieConsentService } from '../../../core/services/cookie-consent.service';

describe('CookieBannerComponent', () => {
  let component: CookieBannerComponent;
  let fixture: ComponentFixture<CookieBannerComponent>;
  let consentService: CookieConsentService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CookieBannerComponent, FormsModule],
      providers: [CookieConsentService],
    }).compileComponents();

    fixture = TestBed.createComponent(CookieBannerComponent);
    component = fixture.componentInstance;
    consentService = TestBed.inject(CookieConsentService);
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  describe('Banner visibility', () => {
    it('should show banner on first visit (no consent)', () => {
      fixture.detectChanges();
      expect(component.showBanner).toBe(true);
    });

    it('should not show banner when consent already given', () => {
      consentService.setConsent({ analytics: true });
      fixture.detectChanges();
      expect(component.showBanner).toBe(false);
    });

    it('should hide banner after acceptAll is called', () => {
      fixture.detectChanges();
      expect(component.showBanner).toBe(true);

      component.acceptAll();
      expect(component.showBanner).toBe(false);
    });

    it('should hide banner after rejectAll is called', () => {
      fixture.detectChanges();
      expect(component.showBanner).toBe(true);

      component.rejectAll();
      expect(component.showBanner).toBe(false);
    });
  });

  describe('acceptAll', () => {
    it('should save all preferences as true', () => {
      fixture.detectChanges();
      component.acceptAll();

      const consent = consentService.getConsent();
      expect(consent?.preferences.essential).toBe(true);
      expect(consent?.preferences.analytics).toBe(true);
      expect(consent?.preferences.marketing).toBe(true);
      expect(consent?.preferences.preferences).toBe(true);
    });

    it('should hide banner', () => {
      fixture.detectChanges();
      component.acceptAll();
      expect(component.showBanner).toBe(false);
    });
  });

  describe('rejectAll', () => {
    it('should save only essential as true', () => {
      fixture.detectChanges();
      component.rejectAll();

      const consent = consentService.getConsent();
      expect(consent?.preferences.essential).toBe(true);
      expect(consent?.preferences.analytics).toBe(false);
      expect(consent?.preferences.marketing).toBe(false);
      expect(consent?.preferences.preferences).toBe(false);
    });

    it('should hide banner', () => {
      fixture.detectChanges();
      component.rejectAll();
      expect(component.showBanner).toBe(false);
    });
  });

  describe('customize', () => {
    it('should show preferences modal', () => {
      fixture.detectChanges();
      component.customize();
      expect(component.showPreferencesModal).toBe(true);
    });

    it('should load existing preferences into form', () => {
      consentService.setConsent({
        analytics: true,
        marketing: true,
        preferences: false,
      });

      component.showBanner = true;
      component.customize();

      expect(component.preferences.analytics).toBe(true);
      expect(component.preferences.marketing).toBe(true);
      expect(component.preferences.preferences).toBe(false);
    });

    it('should have essential always true', () => {
      component.customize();
      expect(component.preferences.essential).toBe(true);
    });
  });

  describe('closePreferencesModal', () => {
    it('should close modal', () => {
      component.showPreferencesModal = true;
      component.closePreferencesModal();
      expect(component.showPreferencesModal).toBe(false);
    });
  });

  describe('savePreferences', () => {
    it('should save preferences to service', () => {
      fixture.detectChanges();
      component.customize();
      component.preferences = {
        essential: true,
        analytics: true,
        marketing: false,
        preferences: true,
      };

      component.savePreferences();

      const consent = consentService.getConsent();
      expect(consent?.preferences.analytics).toBe(true);
      expect(consent?.preferences.marketing).toBe(false);
      expect(consent?.preferences.preferences).toBe(true);
    });

    it('should close modal after save', () => {
      component.showPreferencesModal = true;
      component.savePreferences();
      expect(component.showPreferencesModal).toBe(false);
    });

    it('should hide banner after save', () => {
      fixture.detectChanges();
      component.customize();
      component.savePreferences();
      expect(component.showBanner).toBe(false);
    });
  });

  describe('Mutation Test: First-visit Check', () => {
    it('should fail if first-visit detection is removed (banner always shows)', () => {
      // This test ensures the banner only shows on first visit
      // If the check (hasGivenConsent) is removed, this test fails
      consentService.setConsent({ analytics: false });

      const fixture2 = TestBed.createComponent(CookieBannerComponent);
      const component2 = fixture2.componentInstance;
      fixture2.detectChanges();

      // After ngOnInit with existing consent, banner should NOT show
      expect(component2.showBanner).toBe(false);
    });
  });
});

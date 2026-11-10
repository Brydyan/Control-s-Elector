import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CookieConsentService } from '../../../core/services/cookie-consent.service';

/**
 * Cookie banner component that displays consent options to users on first visit.
 * The banner is sticky at the bottom and shows granular consent options.
 * Once consent is given, the banner is hidden and not shown again (stored in localStorage).
 */
@Component({
  selector: 'app-cookie-banner',
  templateUrl: './cookie-banner.component.html',
  styleUrl: './cookie-banner.component.scss',
  standalone: true,
  imports: [CommonModule, FormsModule],
})
export class CookieBannerComponent implements OnInit {
  private consentService = inject(CookieConsentService);

  showBanner = false;
  showPreferencesModal = false;

  // Preferences form state
  preferences = {
    essential: true, // Always true, disabled
    analytics: false,
    marketing: false,
    preferences: false,
  };

  ngOnInit(): void {
    // Show banner only on first visit (no stored consent)
    if (!this.consentService.hasGivenConsent()) {
      this.showBanner = true;
    }
  }

  /**
   * Accept all cookies
   */
  acceptAll(): void {
    this.consentService.setConsent({
      essential: true,
      analytics: true,
      marketing: true,
      preferences: true,
    });
    this.hideBanner();
  }

  /**
   * Reject all optional cookies (keep only essential)
   */
  rejectAll(): void {
    this.consentService.setConsent({
      essential: true,
      analytics: false,
      marketing: false,
      preferences: false,
    });
    this.hideBanner();
  }

  /**
   * Open preferences modal
   */
  customize(): void {
    this.showPreferencesModal = true;
    // Load current preferences if they exist
    const current = this.consentService.getPreferences();
    if (current) {
      this.preferences = { ...current };
    }
  }

  /**
   * Close preferences modal without saving
   */
  closePreferencesModal(): void {
    this.showPreferencesModal = false;
  }

  /**
   * Save customized preferences
   */
  savePreferences(): void {
    this.consentService.setConsent(this.preferences);
    this.showPreferencesModal = false;
    this.hideBanner();
  }

  /**
   * Hide the banner
   */
  private hideBanner(): void {
    this.showBanner = false;
  }
}

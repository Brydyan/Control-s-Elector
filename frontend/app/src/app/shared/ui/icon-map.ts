/**
 * Icon mapping from the Ionic names to the PrimeIcons classes.
 *
 * The app used Ionicons until the PrimeNG migration. The icons are referenced
 * by name in templates and by the `menu.icono` catalog in the backend, so the
 * translation lives here instead of being scattered across the components.
 *
 * PrimeIcons are plain CSS classes, so the target is a full class list such as
 * `pi pi-home`.
 */
const PRIME_ICON_MAP: Record<string, string> = {
  // Navigation and generic
  'home-outline': 'pi pi-home',
  'menu-outline': 'pi pi-bars',
  'close-outline': 'pi pi-times',
  'search-outline': 'pi pi-search',
  'settings-outline': 'pi pi-cog',
  'people-outline': 'pi pi-users',
  'analytics-outline': 'pi pi-chart-line',
  'notifications-outline': 'pi pi-bell',
  'log-out-outline': 'pi pi-sign-out',
  'grid-outline': 'pi pi-th-large',

  // Documents and evidence
  'document-text-outline': 'pi pi-file',
  'document-outline': 'pi pi-file',
  'add-circle-outline': 'pi pi-plus-circle',
  'create-outline': 'pi pi-pencil',
  'checkmark-outline': 'pi pi-check',
  'checkmark-circle-outline': 'pi pi-check-circle',
  'checkmark-done-outline': 'pi pi-check-circle',
  'alert-circle-outline': 'pi pi-exclamation-circle',
  'close-circle-outline': 'pi pi-times-circle',
  'close-circle': 'pi pi-times-circle',
  'arrow-back-outline': 'pi pi-arrow-left',
  'image-outline': 'pi pi-image',
  'camera-outline': 'pi pi-camera',
  'cloud-upload-outline': 'pi pi-cloud-upload',
  'cloud-upload': 'pi pi-cloud-upload',
  'pulse-outline': 'pi pi-chart-line',
  'cloud-download-outline': 'pi pi-cloud-download',
  'download-outline': 'pi pi-download',
  'filter-outline': 'pi pi-filter',
  'refresh-outline': 'pi pi-refresh',
  'lock-closed-outline': 'pi pi-lock',
  'eye-outline': 'pi pi-eye',
  'information-circle-outline': 'pi pi-info-circle',
  'help-circle-outline': 'pi pi-question-circle',
  'calendar-outline': 'pi pi-calendar',
  'location-outline': 'pi pi-map-marker',
  'person-outline': 'pi pi-user',
  'mail-outline': 'pi pi-envelope',
  'key-outline': 'pi pi-key',
  'save-outline': 'pi pi-save',
  'trash-outline': 'pi pi-trash',
  'copy-outline': 'pi pi-copy',
  'expand-outline': 'pi pi-window-maximize',
  'layers-outline': 'pi pi-clone',
  'flag-outline': 'pi pi-flag',
  'time-outline': 'pi pi-clock',
  'trending-up-outline': 'pi pi-chart-line',
  'pie-chart-outline': 'pi pi-chart-pie',
  'bar-chart-outline': 'pi pi-chart-bar',
  'list-outline': 'pi pi-list',
  'grid-view-outline': 'pi pi-th-large',
  'today-outline': 'pi pi-calendar-clock',
  'wallet-outline': 'pi pi-wallet',
  'business-outline': 'pi pi-briefcase',
  'ribbon-outline': 'pi pi-bookmark',
  'medal-outline': 'pi pi-bookmark-fill',
};

/**
 * Translates an Ionic icon name into its PrimeIcons class list.
 *
 * Unknown names fall back to a document icon so a missing entry never leaves
 * a blank space in the interface.
 */
export function toPrimeIcon(name?: string | null): string {
  if (!name) return 'pi pi-file';
  return PRIME_ICON_MAP[name] ?? 'pi pi-file';
}

export { PRIME_ICON_MAP };

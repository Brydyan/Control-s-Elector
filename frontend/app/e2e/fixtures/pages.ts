import { Page, expect } from '@playwright/test';

/**
 * T-FE-10 E2E: Page Object Model
 */

export class ActasListaPage {
  constructor(private page: Page) {}

  async goto() {
    await this.page.goto('/coordinator/actas');
    // Wait for table to load
    await this.page.waitForSelector('[role="table"]', { timeout: 5000 });
  }

  async clickEstadoTab(estado: string) {
    const tabButton = this.page.locator(`button:has-text("${estado}")`).first();
    await tabButton.click();
    // Wait for table to update
    await this.page.waitForTimeout(500);
  }

  async verifyEstadoTabsContain(estados: string[]) {
    for (const estado of estados) {
      const tab = this.page.locator(`button:has-text("${estado}")`);
      await expect(tab).toBeVisible();
    }
  }

  async filterBySearch(term: string) {
    const searchInput = this.page.locator('input[aria-label="Buscar por JRV"]');
    await searchInput.fill(term);
    await this.page.waitForTimeout(300); // debounce
  }

  async getTableRowCount(): Promise<number> {
    const rows = await this.page.locator('tbody tr');
    return rows.count();
  }

  async clickActaRow(index: number) {
    const rows = this.page.locator('tbody tr');
    await rows.nth(index).click();
  }

  async goToNextPage() {
    const nextButton = this.page.locator('button:has-text("Next")').first();
    await nextButton.click();
    await this.page.waitForTimeout(500);
  }

  async clickActionMenu(rowIndex: number) {
    const rows = this.page.locator('tbody tr');
    const menuButton = rows.nth(rowIndex).locator('[role="button"]').last();
    await menuButton.click();
  }

  async selectMenuOption(optionText: string) {
    const option = this.page.locator(`div[role="menuitem"]:has-text("${optionText}")`);
    await option.click();
  }

  async verifyToastMessage(message: string) {
    const toast = this.page.locator(`div:has-text("${message}")`);
    await expect(toast).toBeVisible({ timeout: 5000 });
  }
}

export class ActaDetallePage {
  constructor(private page: Page) {}

  async goto(actaId: number) {
    await this.page.goto(`/coordinator/actas/${actaId}`);
    await this.page.waitForSelector('h1', { timeout: 5000 });
  }

  async clickAprobar() {
    const button = this.page.locator('button:has-text("Aprobar")').first();
    await button.click();
  }

  async clickDisputar() {
    const button = this.page.locator('button:has-text("Disputar")').first();
    await button.click();
  }

  async clickCorregir() {
    const button = this.page.locator('button:has-text("Corregir")').first();
    await button.click();
  }

  async clickAnular() {
    const button = this.page.locator('button:has-text("Anular")').first();
    await button.click();
  }

  async fillModalInput(label: string, value: string) {
    const input = this.page.locator(`label:has-text("${label}") + *`);
    await input.fill(value);
  }

  async clickModalConfirm() {
    const button = this.page.locator('button:has-text("Confirmar")').last();
    await button.click();
  }

  async verifyModalOpen(title: string) {
    const modal = this.page.locator(`div[role="dialog"]:has-text("${title}")`);
    await expect(modal).toBeVisible();
  }

  async verifyModalClosed() {
    const modals = this.page.locator('[role="dialog"]');
    await expect(modals).toHaveCount(0);
  }

  async verifyChecksumValid() {
    const checkmark = this.page.locator('i.pi-check-circle');
    await expect(checkmark).toBeVisible();
  }

  async verifyErrorMessage(message: string) {
    const error = this.page.locator(`div:has-text("${message}")`);
    await expect(error).toBeVisible();
  }

  async getActaStatus() {
    const badge = this.page.locator('[class*="badge"]').first();
    return badge.textContent();
  }
}

export class AuthFixture {
  constructor(private page: Page) {}

  async loginAs(role: 'COORDINATOR' | 'MASTER' | 'ADMIN') {
    // Mock or actual login depending on setup
    // For now: assume we're already logged in or use API mock
    console.log(`Logged in as: ${role}`);
  }
}

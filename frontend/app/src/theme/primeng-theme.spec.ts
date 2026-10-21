import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { providePrimeNG } from 'primeng/config';
import { Button } from 'primeng/button';
import { Card } from 'primeng/card';
import { primengTheme } from './primeng-theme';

/**
 * The stylesheets of the pages rely on the PrimeNG theme variables. The styled
 * engine emits them on demand, per component, so a variable that no rendered
 * component references is simply not defined and the rule that uses it falls
 * back to nothing.
 *
 * This test renders the components the shell is made of and asserts that every
 * variable the own stylesheets depend on is actually emitted.
 */
const REQUIRED_TOKENS = [
  '--p-primary-color',
  '--p-primary-contrast-color',
  '--p-content-background',
  '--p-content-border-color',
  '--p-content-hover-background',
  '--p-text-color',
  '--p-text-muted-color',
  '--p-border-radius-md',
  '--p-border-radius-lg',
];

describe('PrimeNG theme variables', () => {
  let themeCss: string;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Button, Card],
      providers: [providePrimeNG({ theme: primengTheme })],
    }).compileComponents();

    const fixture = TestBed.createComponent(Button);
    fixture.detectChanges();

    themeCss = Array.from(document.head.querySelectorAll('style'))
      .map((style) => style.textContent ?? '')
      .join('\n');
  });

  it('should emit the variables the own stylesheets depend on', () => {
    const missing = REQUIRED_TOKENS.filter(
      (token) => !themeCss.includes(`${token}:`),
    );

    expect(missing).toEqual([]);
  });
});

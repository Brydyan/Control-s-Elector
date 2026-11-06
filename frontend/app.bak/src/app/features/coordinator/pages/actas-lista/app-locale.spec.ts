import { LOCALE_ID } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { bootstrapApplication } from '@angular/platform-browser';

/**
 * App locale contract.
 *
 * The Spanish locale is bootstrapped in `src/main.ts`, which sits outside the
 * feature folders. This spec lives next to the actas list because that is the
 * page whose dates exposed the en-US rendering; it pins the two pieces of
 * wiring that make the whole app speak Spanish:
 *
 * 1. `LOCALE_ID` is provided as 'es' at bootstrap.
 * 2. The locale data of '@angular/common/locales/es' is registered, otherwise
 *    every named format ('short', 'medium') throws "Missing locale data".
 *
 * The entry point is imported with `bootstrapApplication` mocked: the test
 * cares about the providers, not about mounting the app.
 */
vi.mock('@angular/platform-browser', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('@angular/platform-browser')>();
  return { ...actual, bootstrapApplication: vi.fn() };
});

describe('App locale', () => {
  it('registra el locale español y lo provee como LOCALE_ID', async () => {
    await import('../../../../../main');

    const providers =
      vi.mocked(bootstrapApplication).mock.calls[0]?.[1]?.providers ?? [];

    expect(providers).toContainEqual({ provide: LOCALE_ID, useValue: 'es' });

    // The locale data has to be registered: without it the DatePipe throws
    // "Missing locale data for the locale 'es'" for any named format.
    const pipe = TestBed.runInInjectionContext(() => new DatePipe('es'));

    expect(pipe.transform('2026-10-21T12:45:00Z', 'short', 'UTC')).toBe(
      '21/10/26, 12:45',
    );
  });
});

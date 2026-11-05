import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';
import { mapError } from './error-handler';

describe('mapError', () => {
  it('classifies 0 status as network error', () => {
    const err = new HttpErrorResponse({
      status: 0,
      statusText: 'Unknown Error',
    });
    const appErr = mapError(err);
    expect(appErr.type).toBe('network');
    expect(appErr.status).toBe(0);
    expect(appErr.retriable).toBe(true);
  });

  it('classifies 401 as unauthorized', () => {
    const err = new HttpErrorResponse({
      status: 401,
      statusText: 'Unauthorized',
    });
    const appErr = mapError(err);
    expect(appErr.type).toBe('unauthorized');
    expect(appErr.retriable).toBe(false);
  });

  it('classifies 403 as forbidden', () => {
    const err = new HttpErrorResponse({ status: 403, statusText: 'Forbidden' });
    const appErr = mapError(err);
    expect(appErr.type).toBe('forbidden');
  });

  it('classifies 404 as not-found', () => {
    const err = new HttpErrorResponse({ status: 404, statusText: 'Not Found' });
    const appErr = mapError(err);
    expect(appErr.type).toBe('not-found');
  });

  it('classifies 400 as validation', () => {
    const err = new HttpErrorResponse({
      status: 400,
      statusText: 'Bad Request',
    });
    const appErr = mapError(err);
    expect(appErr.type).toBe('validation');
  });

  it('classifies 409 as conflict and marks non-retriable', () => {
    const err = new HttpErrorResponse({
      status: 409,
      statusText: 'Conflict',
      error: { message: 'Estado cambió' },
    });
    const appErr = mapError(err);
    expect(appErr.type).toBe('conflict');
    expect(appErr.message).toBe('Estado cambió');
    expect(appErr.retriable).toBe(false);
  });

  it('classifies 5xx as server and marks retriable', () => {
    const err = new HttpErrorResponse({
      status: 500,
      statusText: 'Server Error',
    });
    const appErr = mapError(err);
    expect(appErr.type).toBe('server');
    expect(appErr.retriable).toBe(true);
  });

  it('falls back to backend message when present', () => {
    const err = new HttpErrorResponse({
      status: 422,
      statusText: 'Unprocessable',
      error: { message: 'Detalle custom del backend' },
    });
    const appErr = mapError(err);
    expect(appErr.message).toBe('Detalle custom del backend');
  });

  it('handles non-HTTP errors', () => {
    const appErr = mapError(new Error('algo se rompió'));
    expect(appErr.type).toBe('unknown');
    expect(appErr.message).toBe('algo se rompió');
  });

  it('handles null and undefined gracefully', () => {
    expect(mapError(null).type).toBe('unknown');
    expect(mapError(undefined).type).toBe('unknown');
  });
});

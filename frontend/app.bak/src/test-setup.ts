import 'zone.js';
import 'zone.js/testing';
// jsdom polyfill para IndexedDB (OfflineStorageService en T-FE-09).
import 'fake-indexeddb/auto';
import { getTestBed } from '@angular/core/testing';
import {
  BrowserDynamicTestingModule,
  platformBrowserDynamicTesting,
} from '@angular/platform-browser-dynamic/testing';
import { vi } from 'vitest';

if (!globalThis.ResizeObserver) {
  class ResizeObserverStub {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  }
  globalThis.ResizeObserver = ResizeObserverStub as typeof ResizeObserver;
}

if (!window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })) as typeof window.matchMedia;
}

// jsdom polyfills para ImageCompressionService (T-FE-08).
// createImageBitmap no existe en jsdom; tests específicos mockean el global.
// OffscreenCanvas idem. Solo necesitamos los símbolos para que el módulo compile.
if (typeof globalThis.createImageBitmap === 'undefined') {
  globalThis.createImageBitmap = (() =>
    Promise.reject(
      new Error('createImageBitmap no mockeado en este test'),
    )) as typeof createImageBitmap;
}
if (typeof globalThis.OffscreenCanvas === 'undefined') {
  class OffscreenCanvasStub {
    constructor(_w: number, _h: number) {}
    getContext(): unknown {
      return null;
    }
    convertToBlob(): Promise<Blob> {
      return Promise.reject(new Error('OffscreenCanvas no mockeado'));
    }
  }
  (
    globalThis as unknown as { OffscreenCanvas: typeof OffscreenCanvas }
  ).OffscreenCanvas = OffscreenCanvasStub as unknown as typeof OffscreenCanvas;
}

// jsdom polyfill para Web Crypto API (PinAuthService en T-FE-09).
// Implementación mínima: SHA-256 sobre buffer; suficiente para tests del PIN.
import { createHash } from 'node:crypto';
if (
  typeof globalThis.crypto === 'undefined' ||
  typeof globalThis.crypto.subtle === 'undefined'
) {
  const subtlePolyfill: SubtleCrypto = {
    digest: async (
      algorithm: AlgorithmIdentifier,
      data: BufferSource,
    ): Promise<ArrayBuffer> => {
      const algoName =
        typeof algorithm === 'string'
          ? algorithm
          : (algorithm as { name: string }).name;
      const hashName = (() => {
        if (algoName === 'SHA-256' || algoName === 'sha-256') return 'sha256';
        if (algoName === 'SHA-384') return 'sha384';
        if (algoName === 'SHA-512') return 'sha512';
        return 'sha256';
      })();
      const nodeHash = createHash(hashName);
      nodeHash.update(Buffer.from(data as ArrayBuffer));
      const buf = nodeHash.digest();
      // Copy into a fresh ArrayBuffer so the result is an ArrayBuffer view, not Node Buffer.
      const ab = new ArrayBuffer(buf.byteLength);
      new Uint8Array(ab).set(buf);
      return ab;
    },
  } as unknown as SubtleCrypto;
  const cryptoPolyfill = {
    getRandomValues<T extends ArrayBufferView>(arr: T): T {
      for (let i = 0; i < arr.byteLength; i++) {
        (arr as unknown as Uint8Array)[i] = Math.floor(Math.random() * 256);
      }
      return arr;
    },
    subtle: subtlePolyfill,
    randomUUID: () =>
      'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = (Math.random() * 16) | 0;
        const v = c === 'x' ? r : (r & 0x3) | 0x8;
        return v.toString(16);
      }),
  } as unknown as Crypto;
  Object.defineProperty(globalThis, 'crypto', {
    value: cryptoPolyfill,
    configurable: true,
    writable: true,
  });
}

getTestBed().initTestEnvironment(
  BrowserDynamicTestingModule,
  platformBrowserDynamicTesting(),
);

// Decorate vitest mock function with Jasmine-compatible `.and` property
const wrapFn = (fn: any) => {
  fn.and = {
    returnValue: (val: any) => fn.mockReturnValue(val),
    returnValues: (...vals: any[]) => {
      vals.forEach((v) => fn.mockReturnValueOnce(v));
      return fn;
    },
    callFake: (impl: any) => fn.mockImplementation(impl),
    callThrough: () => fn,
    throwError: (err: any) =>
      fn.mockImplementation(() => {
        throw err;
      }),
  };
  return fn;
};

const createSpy = (name?: string) => wrapFn(vi.fn());

const createSpyObj = (
  baseNameOrObj: any,
  methodNames?: string[] | Record<string, any>,
) => {
  const obj: Record<string, any> = {};
  if (Array.isArray(methodNames)) {
    for (const method of methodNames) {
      obj[method] = wrapFn(vi.fn());
    }
    return obj;
  }
  if (typeof baseNameOrObj === 'object' && baseNameOrObj !== null) {
    for (const [key, value] of Object.entries(baseNameOrObj)) {
      const fn = wrapFn(vi.fn());
      fn.mockReturnValue(value);
      obj[key] = fn;
    }
    return obj;
  }
  return obj;
};

(globalThis as any).jasmine = {
  createSpy,
  createSpyObj,
};

(globalThis as any).spyOn = (obj: any, method: string) => {
  const spy = vi.spyOn(obj, method as any);
  return wrapFn(spy);
};

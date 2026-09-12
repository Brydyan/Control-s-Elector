import 'zone.js';
import 'zone.js/testing';
import { getTestBed } from '@angular/core/testing';
import {
  BrowserDynamicTestingModule,
  platformBrowserDynamicTesting,
} from '@angular/platform-browser-dynamic/testing';
import { vi } from 'vitest';

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

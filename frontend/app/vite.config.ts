/// <reference types="vitest" />
import { defineConfig } from 'vite';
import angular from '@analogjs/vite-plugin-angular';

export default defineConfig({
  plugins: [
    angular(),
  ],
  resolve: {
    tsconfigPaths: true,
    alias: {
      '@shared': '/src/app/shared',
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['src/test-setup.ts'],
    include: ['src/**/*.spec.ts'],
    reporters: ['default'],
    hookTimeout: 10000,
    testTimeout: 10000,
    isolate: true,
    server: {
      deps: {
        inline: [/@ionic/],
      },
    },
  },
});

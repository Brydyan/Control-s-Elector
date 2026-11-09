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
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // Documentos legales (T-LEGAL-01): los sirve el backend, no la SPA.
      // La expresion es ANCLADA a proposito: con un prefijo plano ('/terms')
      // el proxy se tragaba tambien el chunk lazy
      // 'terms-acceptance.page-<hash>.js' y la ruta '/terminos' de la app, que
      // devolvian 502 y dejaban la pantalla de aceptacion sin cargar.
      '^/privacy(\\?.*)?$': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '^/terms(\\?.*)?$': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
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

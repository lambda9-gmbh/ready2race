import { defineConfig, Plugin } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tsconfigPaths from "vite-tsconfig-paths";
import { VitePWA } from 'vite-plugin-pwa'
import { renameSync, mkdirSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * Legt den gebauten Service Worker nach dist/app/ um.
 *
 * Der Scope eines Service Workers folgt seinem Ablageort. Nur unter /app/ kontrolliert er die
 * Helfer-App und sonst nichts - der Header `Service-Worker-Allowed` waere die Alternative und
 * setzt Zugriff aufs Hosting voraus, den wir nicht haben. vite-plugin-pwa schreibt die Datei ins
 * Wurzelverzeichnis von dist; das Verschieben danach ist der verlaessliche Weg, weil keine
 * Plugin-Option dafuer dokumentiert ist.
 *
 * Die Precache-Eintraege im Worker sind absolute Pfade ab '/', das Verschieben beruehrt sie nicht.
 */
const moveServiceWorkerToApp = (): Plugin => ({
  name: 'r2r-move-sw-to-app',
  apply: 'build',
  closeBundle() {
    const dist = resolve(__dirname, 'dist')
    mkdirSync(resolve(dist, 'app'), {recursive: true})
    renameSync(resolve(dist, 'sw.js'), resolve(dist, 'app/sw.js'))
  },
})

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    tsconfigPaths(),
    react(),
    VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src/pwa',
      filename: 'sw.ts',
      injectRegister: false,
      manifestFilename: 'app/manifest.webmanifest',
      injectManifest: {
        // Die index.html wird bewusst nicht vorgeladen, sie laeuft ueber NetworkFirst.
        globIgnores: ['**/index.html'],
      },
      manifest: {
        name: 'Ready2Race',
        short_name: 'R2R',
        start_url: '/app',
        scope: '/app/',
        display: 'standalone',
        background_color: '#ffffff',
        theme_color: '#4d9f85',
        icons: [
          {src: '/app/icon-192.png', sizes: '192x192', type: 'image/png'},
          {src: '/app/icon-512.png', sizes: '512x512', type: 'image/png'},
          {src: '/app/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable'},
        ],
      },
    }),
    moveServiceWorkerToApp(),
  ],
  server: {
    host: '0.0.0.0',
    port: 5123,
  },
  test: {
    include: ['src/**/*.test.ts'],
  },
})

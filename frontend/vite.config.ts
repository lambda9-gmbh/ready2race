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
 * Helfer-App und sonst nichts - der Header `Service-Worker-Allowed` wäre die Alternative und
 * setzt Zugriff aufs Hosting voraus, den wir nicht haben. vite-plugin-pwa schreibt die Datei ins
 * Wurzelverzeichnis von dist; das Verschieben danach ist der verlässliche Weg, weil keine
 * Plugin-Option dafür dokumentiert ist.
 *
 * Die Precache-Einträge im Worker sind absolute Pfade ab '/', das Verschieben berührt sie nicht.
 *
 * `sequential` und `order: 'post'` sind hier tragend, nicht kosmetisch: Vite ruft `closeBundle`
 * sonst parallel auf, und dann läuft der Umzug, bevor vite-plugin-pwa die Datei überhaupt
 * geschrieben hat - der Build bricht mit ENOENT auf dist/sw.js ab.
 */
const moveServiceWorkerToApp = (): Plugin => ({
  name: 'r2r-move-sw-to-app',
  apply: 'build',
  closeBundle: {
    sequential: true,
    order: 'post',
    handler() {
      const dist = resolve(__dirname, 'dist')
      mkdirSync(resolve(dist, 'app'), {recursive: true})
      renameSync(resolve(dist, 'sw.js'), resolve(dist, 'app/sw.js'))
    },
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
        // Die index.html wird bewusst nicht vorgeladen, sie läuft über NetworkFirst.
        globIgnores: ['**/index.html'],
        // Das Bundle ist ein einziger Brocken von gut 3 MB und wird vom Server unkomprimiert
        // ausgeliefert. Workbox lässt standardmäßig nur 2 MiB in den Precache und würde es
        // stillschweigend auslassen - womit die Helfer-App offline nichts mehr hätte. Die
        // Grenze liegt deshalb darüber. Fällt das Bundle durch Code-Splitting einmal kleiner
        // aus, kann sie zurück.
        maximumFileSizeToCacheInBytes: 5 * 1024 * 1024,
      },
      manifest: {
        name: 'Ready2Race',
        short_name: 'R2R',
        // Der Plan wollte gar keine Sprache setzen, weil die App Deutsch, Englisch und Dänisch
        // kann. vite-plugin-pwa trägt dann aber 'en' ein - für eine deutsche Regatta die
        // schlechteste der drei. Die Oberfläche selbst bleibt davon unberührt und folgt
        // weiterhin der Spracherkennung.
        lang: 'de',
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

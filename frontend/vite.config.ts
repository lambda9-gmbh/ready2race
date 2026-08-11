import { defineConfig, Plugin } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tsconfigPaths from "vite-tsconfig-paths";
import { VitePWA } from 'vite-plugin-pwa'
import type { ManifestOptions } from 'vite-plugin-pwa'
import { mkdirSync, readFileSync, writeFileSync, rmSync, existsSync } from 'node:fs'
import { resolve } from 'node:path'

/**
 * Das Web-App-Manifest der Helfer-App - als eigene Konstante, weil es zwei Abnehmer hat: den
 * Produktions-Build über vite-plugin-pwa und den Dev-Server über [serveManifestInDev].
 */
const appManifest: Partial<ManifestOptions> = {
  name: 'Ready2Race',
  short_name: 'R2R',
  // Der Plan wollte gar keine Sprache setzen, weil die App Deutsch, Englisch und Dänisch
  // kann. vite-plugin-pwa trägt dann aber 'en' ein - für eine deutsche Regatta die
  // schlechteste der drei. Die Oberfläche selbst bleibt davon unberührt und folgt
  // weiterhin der Spracherkennung.
  lang: 'de',
  // Mit Schrägstrich am Ende: '/app' liegt formal NICHT innerhalb von '/app/'. Der Browser
  // verwirft dann den Scope und setzt ihn auf das Verzeichnis der Startadresse, also '/' -
  // die installierte App würde Navigationen der gesamten Herkunft an sich ziehen,
  // einschließlich /board, /results und der Verwaltungsoberfläche.
  start_url: '/app/',
  scope: '/app/',
  display: 'standalone',
  background_color: '#ffffff',
  theme_color: '#4d9f85',
  icons: [
    {src: '/app/icon-192.png', sizes: '192x192', type: 'image/png'},
    {src: '/app/icon-512.png', sizes: '512x512', type: 'image/png'},
    {src: '/app/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable'},
  ],
}

/**
 * Liefert das Manifest auch im Dev-Server aus.
 *
 * vite-plugin-pwa schreibt das Manifest nur beim Build; im Dev-Betrieb beantwortete der
 * SPA-Fallback die Adresse mit der index.html - ein ungültiges Manifest, und Chrome machte aus
 * "Zum Startbildschirm hinzufügen" eine bloße Verknüpfung auf die gerade offene Seite statt
 * einer App, die unter /app/ startet (beobachtet am 10.08.2026 beim LAN-Test über den
 * Dev-Server). Die Symbole liegen als echte Dateien in public/app/ und brauchen keine Hilfe.
 * Einen Service Worker gibt es im Dev-Betrieb weiterhin nicht - Chrome installiert auch ohne;
 * nur offline kann die Dev-Fassung nichts, und das soll sie auch nicht.
 */
const serveManifestInDev = (): Plugin => ({
  name: 'r2r-serve-manifest-in-dev',
  apply: 'serve',
  configureServer(server) {
    server.middlewares.use('/app/manifest.webmanifest', (_req, res) => {
      res.setHeader('Content-Type', 'application/manifest+json')
      res.end(JSON.stringify(appManifest))
    })
  },
})

/**
 * Legt den gebauten Service Worker nach dist/app/ um.
 *
 * Der Scope eines Service Workers folgt seinem Ablageort. Nur unter /app/ kontrolliert er die
 * Helfer-App und sonst nichts - der Header `Service-Worker-Allowed` wäre die Alternative und
 * setzt Zugriff aufs Hosting voraus, den wir nicht haben. vite-plugin-pwa schreibt die Datei ins
 * Wurzelverzeichnis von dist; das Verschieben danach ist der verlässliche Weg, weil keine
 * Plugin-Option dafür dokumentiert ist.
 *
 * ACHTUNG: Workbox schreibt die Precache-Einträge RELATIV ('assets/index-x.js') und löst sie zur
 * Laufzeit mit `new URL(eintrag, location.href)` auf - location ist der Worker selbst. Unter
 * /app/ würde daraus /app/assets/index-x.js, und das gibt es nicht. Deshalb setzt
 * `modifyURLPrefix` unten jedem Eintrag ein '/' voran. Ohne das installiert sich der Worker
 * entweder gar nicht oder legt achtmal die index.html unter Asset-Schlüsseln ab, und die App ist
 * offline leer.
 *
 * Die Symbole und das Manifest hängt vite-plugin-pwa erst NACH dieser Umschreibung an, sie kommen
 * deshalb weiterhin relativ heraus und werden hier nachgezogen. Zum Schluss prüft der Hook, dass
 * kein relativer Eintrag übrig ist, und bricht sonst den Build ab - ohne Serverzugriff ist ein
 * fehlerhafter Worker nur durch einen neuen Build zu ersetzen, das darf nicht unbemerkt raus.
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
      const built = readFileSync(resolve(dist, 'sw.js'), 'utf8')
      const fixed = built.replace(/"url":"(?!\/)/g, '"url":"/')

      const relative = [...fixed.matchAll(/"url":"([^"]*)"/g)]
        .map(m => m[1])
        .filter(url => !url.startsWith('/'))
      if (relative.length > 0) {
        throw new Error(`Precache-Eintraege ohne fuehrenden Schraegstrich: ${relative.join(', ')}`)
      }

      const missing = [...fixed.matchAll(/"url":"([^"]*)"/g)]
        .map(m => m[1])
        .filter(url => !existsSync(resolve(dist, url.slice(1))))
      if (missing.length > 0) {
        throw new Error(`Precache zeigt auf nicht vorhandene Dateien: ${missing.join(', ')}`)
      }

      mkdirSync(resolve(dist, 'app'), {recursive: true})
      writeFileSync(resolve(dist, 'app/sw.js'), fixed)
      rmSync(resolve(dist, 'sw.js'))
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
        // Macht die relativen Precache-Adressen absolut, siehe die Erläuterung oben. Ohne das
        // sucht der Worker seine Dateien unter /app/assets/ statt unter /assets/.
        modifyURLPrefix: {'': '/'},
      },
      manifest: appManifest,
    }),
    moveServiceWorkerToApp(),
    serveManifestInDev(),
  ],
  server: {
    host: '0.0.0.0',
    port: 5123,
  },
  test: {
    include: ['src/**/*.test.ts'],
  },
})

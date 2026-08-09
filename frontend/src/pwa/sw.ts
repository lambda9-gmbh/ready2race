/// <reference lib="webworker" />
import {cleanupOutdatedCaches, precacheAndRoute} from 'workbox-precaching'
import {NavigationRoute, registerRoute} from 'workbox-routing'
import {NetworkFirst} from 'workbox-strategies'

declare const self: ServiceWorkerGlobalScope

/**
 * Service Worker der Helfer-App.
 *
 * Liegt unter /app/, damit sein Scope allein aus dem Ablageort folgt - der Header
 * `Service-Worker-Allowed` braucht Zugriff aufs Hosting, den wir nicht haben.
 *
 * Er bedient ausschließlich die Shell. Die API bleibt netzwerk-only: Antworten enthalten
 * Teilnehmerdaten mit Klarnamen, die nichts in der CacheStorage eines geteilten Geräts zu
 * suchen haben. Der Lese-Cache liegt stattdessen in der App-Schicht (src/pwa/readCache.ts).
 */

precacheAndRoute(self.__WB_MANIFEST)
cleanupOutdatedCaches()

// Bewusst NICHT createHandlerBoundToURL('index.html'): Das setzt voraus, dass die index.html im
// Precache liegt, und genau die ist per globIgnores ausgenommen (sie wird beim Ausliefern nicht
// veraendert, aber ein eingefrorener Shell-Einstieg ist bei einem Regatta-Update das Letzte, was
// wir wollen). NetworkFirst holt sie frisch, faellt offline auf die zuletzt gesehene zurueck.
registerRoute(
    new NavigationRoute(new NetworkFirst({cacheName: 'app-shell'}), {
        allowlist: [/^\/app(\/|$)/],
        denylist: [/^\/api\//, /^\/static\//],
    }),
)

// Kein stilles Übernehmen: Die Oberfläche fragt erst, dann wird gewechselt. Mitten im
// Rennbetrieb soll sich das Dashboard nicht unter den Händen austauschen.
self.addEventListener('message', event => {
    if (event.data?.type === 'SKIP_WAITING') {
        void self.skipWaiting()
    }
})

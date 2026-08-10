/// <reference lib="webworker" />

declare const self: ServiceWorkerGlobalScope

/**
 * Notfallvariante. Wird NUR eingespielt, wenn eine ausgelieferte Fassung flächendeckend klemmt:
 * In vite.config.ts `filename` auf 'swKill.ts' stellen, bauen, ausliefern. Der Worker ersetzt
 * seinen Vorgänger, räumt alles weg und verschwindet selbst.
 *
 * Ohne Zugriff aufs Hosting ist ein neuer Build der einzige Weg, einen kaputten Service Worker
 * loszuwerden - deshalb liegt diese Datei fertig im Repo und wird nicht erst im Ernstfall
 * geschrieben.
 */

self.addEventListener('install', () => {
    void self.skipWaiting()
})

self.addEventListener('activate', event => {
    event.waitUntil(
        (async () => {
            const keys = await caches.keys()
            await Promise.all(keys.map(key => caches.delete(key)))
            await self.registration.unregister()
            const clients = await self.clients.matchAll({type: 'window'})
            clients.forEach(client => {
                if ('navigate' in client) {
                    void (client as WindowClient).navigate(client.url)
                }
            })
        })(),
    )
})

export {}

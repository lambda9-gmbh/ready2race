// Startet einen Nimiq-QrScanner mit automatischen Wiederholungen. Auf der Regatta
// ist die Kamera oft nur für einen Moment belegt (App-Wechsel, vorheriger Stream
// noch nicht freigegeben, andere App hält die Kamera) — ein einzelner
// fehlgeschlagener getUserMedia-Aufruf darf die Vorschau deshalb nicht dauerhaft
// schwarz lassen.
//
// Bewusst vom React-Lebenszyklus entkoppelt (isCancelled-Abfrage statt
// AbortController, injizierbares sleep), damit sich die Logik ohne echte Kamera
// mit vitest testen lässt.

export interface StartableScanner {
    start(): Promise<void>
}

export type ScannerStartResult = 'started' | 'cancelled' | 'failed'

// Kurzer Backoff: die typische Blockade (Stream des vorherigen Scanners noch
// nicht freigegeben) löst sich innerhalb von Sekundenbruchteilen, eine fremde
// App kann etwas länger brauchen. Danach sichtbar scheitern statt endlos zu warten.
export const SCANNER_RETRY_DELAYS_MS = [500, 1000, 2000, 4000]

export const startScannerWithRetry = async (
    scanner: StartableScanner,
    isCancelled: () => boolean,
    delaysMs: readonly number[] = SCANNER_RETRY_DELAYS_MS,
    sleep: (ms: number) => Promise<void> = ms => new Promise(resolve => setTimeout(resolve, ms)),
): Promise<ScannerStartResult> => {
    for (let attempt = 0; ; attempt++) {
        if (isCancelled()) return 'cancelled'
        try {
            await scanner.start()
            // Auch nach erfolgreichem Start kann der Aufrufer inzwischen abgebrochen
            // haben (Unmount während getUserMedia lief) — dann zählt der Abbruch,
            // der Aufrufer räumt den Scanner auf.
            return isCancelled() ? 'cancelled' : 'started'
        } catch {
            if (isCancelled()) return 'cancelled'
            if (attempt >= delaysMs.length) return 'failed'
            await sleep(delaysMs[attempt])
        }
    }
}

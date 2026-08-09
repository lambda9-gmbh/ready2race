/**
 * Der Stand einer Hintergrundaktualisierung.
 *
 * [data] ist der letzte ERFOLGREICHE Stand und bleibt bei einem Fehlversuch stehen — eine Seite,
 * die nach einem Funkloch leer wird, ist der schlechteste Ausgang. [failed] trägt dann die
 * Stand-von-Warnung.
 *
 * [initialLoad] unterscheidet „noch nie etwas gewusst" von „geladen, aber leer". Ohne dieses Feld
 * behauptete eine Anzeige nach einem gescheiterten ersten Abruf, es sei kein Lauf angesetzt.
 */
export type PollerState<T> = {
    data: T | null
    lastUpdated: Date | null
    initialLoad: boolean
    failed: boolean
}

export const initialPollerState = <T,>(): PollerState<T> => ({
    data: null,
    lastUpdated: null,
    initialLoad: true,
    failed: false,
})

export type PollerOptions<T> = {
    /**
     * Ein Abruf. Liefert die Daten bei Erfolg und `null` für eine Antwort ohne Nutzdaten (z.B.
     * HTTP 500) — die zählt als Fehlversuch statt als leeres Ergebnis. Ein Netzabbruch darf
     * werfen; ein `AbortError` gilt nicht als Fehlversuch, sondern als abgelöster Abruf.
     */
    load: (signal: AbortSignal) => Promise<T | null>
    intervalMs: number
    onState: (state: PollerState<T>) => void
    /** Nur für Tests: die Uhr für [PollerState.lastUpdated]. */
    now?: () => Date
}

export type Poller = {
    start: () => void
    stop: () => void
    /** Sofort neu laden; bricht einen laufenden Abruf ab. */
    refreshNow: () => void
    /** In den Hintergrund: der Takt hält an, ein laufender Abruf darf zu Ende gehen. */
    suspend: () => void
    /** Zurück in den Vordergrund: sofort ein Abruf, danach wieder im Takt. */
    resume: () => void
}

/**
 * Ein Takter, der im Hintergrund nachlädt.
 *
 * Bewusst ohne React: das Frontend prüft mit Vitest im Node-Umfeld, ohne DOM und ohne
 * Rendering-Bibliothek. Stünde diese Logik in einem Hook, wäre sie ungeprüft. `usePolledFetch`
 * ist deshalb nur noch Verdrahtung.
 *
 * Drei Eigenschaften sind hier wichtiger als Kürze:
 *
 * - **Kein Überlappen.** Höchstens ein Abruf ist unterwegs, und der Timer für den nächsten startet
 *   erst, wenn der vorige abgeschlossen ist. Mit `setInterval` würde ein hängender Abruf über einer
 *   langsamen Mobilfunkverbindung eine Warteschlange aufbauen, die nie wieder abfließt.
 * - **Fehler behalten den Stand.** Siehe [PollerState].
 * - **Im Hintergrund wird nicht geladen**, beim Zurückkehren sofort einmal.
 */
export const createPoller = <T,>({
    load,
    intervalMs,
    onState,
    now = () => new Date(),
}: PollerOptions<T>): Poller => {
    let state = initialPollerState<T>()
    let timer: ReturnType<typeof setTimeout> | null = null
    // Der Abruf, der gerade gilt. Ein älterer, abgelöster Abruf erkennt sich daran, dass hier
    // nicht mehr sein eigener Controller steht - er rührt dann weder Zustand noch Timer an.
    let current: AbortController | null = null
    let started = false
    let awake = true

    const emit = (patch: Partial<PollerState<T>>) => {
        state = {...state, ...patch}
        onState(state)
    }

    const clearTimer = () => {
        if (timer !== null) {
            clearTimeout(timer)
            timer = null
        }
    }

    const schedule = () => {
        clearTimer()
        if (!started || !awake) return
        timer = setTimeout(() => {
            timer = null
            void run()
        }, intervalMs)
    }

    const run = async () => {
        current?.abort()
        const own = new AbortController()
        current = own
        try {
            const data = await load(own.signal)
            if (current !== own) return
            if (data === null) {
                emit({initialLoad: false, failed: true})
            } else {
                emit({data, lastUpdated: now(), initialLoad: false, failed: false})
            }
        } catch (error) {
            if (current !== own) return
            // Abgelöst oder gestoppt - kein echter Fehler, und der Nachfolger hat bereits
            // übernommen.
            if (error instanceof Error && error.name === 'AbortError') return
            emit({initialLoad: false, failed: true})
        } finally {
            // Wurde dieser Abruf zwischenzeitlich abgelöst, überlässt er Timer und Zustand
            // vollständig dem neueren.
            if (current === own) {
                current = null
                schedule()
            }
        }
    }

    const restart = () => {
        clearTimer()
        void run()
    }

    return {
        start: () => {
            if (started) return
            started = true
            // Den Anfangszustand sofort melden: der erste Abruf kann noch unterwegs sein oder gar
            // abgelöst werden, bevor er je ankommt (siehe [run]) - ohne diese Meldung gäbe es bis
            // dahin überhaupt keinen Zustand zu lesen.
            emit({})
            void run()
        },
        stop: () => {
            started = false
            clearTimer()
            current?.abort()
            current = null
        },
        refreshNow: () => {
            if (!started || !awake) return
            restart()
        },
        suspend: () => {
            awake = false
            clearTimer()
        },
        resume: () => {
            if (awake) return
            awake = true
            if (started) restart()
        },
    }
}

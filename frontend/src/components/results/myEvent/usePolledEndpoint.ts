import {useCallback, useEffect, useRef, useState} from 'react'

const FALLBACK_INTERVAL_SECONDS = 15

export interface PolledState<T> {
    data: T | null
    lastUpdated: Date | null
    notFound: boolean
    initialLoad: boolean
    // true, solange der letzte Versuch fehlgeschlagen ist (Backend tot, HTTP-Fehler,
    // kein Netz). Vor dem ersten Erfolg unterscheidet das Flag "geladen, aber leer" von
    // "nichts gewusst"; danach bleibt `data` als letzter guter Stand stehen und das Flag
    // trägt die Stand-von-Warnung — bewusstes Pausieren im Hintergrund zählt nicht als
    // Fehler, weil dabei gar kein Abruf stattfindet.
    loadFailed: boolean
}

/**
 * Lädt einen Endpunkt im Takt, den der Server vorgibt.
 *
 * Drei Eigenschaften sind hier wichtiger als Kürze:
 * - Bei einem Netzabbruch bleibt der letzte gute Stand stehen. Ein fest montierter
 *   Bildschirm, der nach einem Aussetzer leer bleibt, ist der schlechteste Ausgang.
 * - Scheitert dagegen der allererste Abruf (also bevor je ein guter Stand da war),
 *   muss das von außen von "geladen, aber leer" unterscheidbar sein — sonst behauptet
 *   die Anzeige fälschlich, es sei kein Lauf auf dem Wasser.
 * - Im Hintergrund wird nicht geladen; beim Zurückkehren sofort einmal.
 */
export const usePolledEndpoint = <T>(
    load: (signal: AbortSignal) => Promise<{data?: T; response: Response}>,
    intervalOf: (data: T) => number,
    deps: unknown[],
): PolledState<T> => {
    const [data, setData] = useState<T | null>(null)
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
    const [notFound, setNotFound] = useState(false)
    const [initialLoad, setInitialLoad] = useState(true)
    const [loadFailed, setLoadFailed] = useState(false)

    const timerRef = useRef<number | null>(null)
    const intervalRef = useRef(FALLBACK_INTERVAL_SECONDS)
    const abortRef = useRef<AbortController | null>(null)
    const mountedRef = useRef(true)

    useEffect(() => {
        mountedRef.current = true
        return () => {
            mountedRef.current = false
        }
    }, [])

    const clearTimer = () => {
        if (timerRef.current !== null) {
            window.clearTimeout(timerRef.current)
            timerRef.current = null
        }
    }

    const runLoad = useCallback(async () => {
        abortRef.current?.abort()
        const controller = new AbortController()
        abortRef.current = controller

        // Ein falsch gedruckter QR-Code soll nicht dauerhaft gegen einen toten Endpoint
        // takten: nach einem 404 hält der Takt an, die Meldung bleibt stehen.
        let stopPolling = false

        try {
            const result = await load(controller.signal)
            if (!mountedRef.current) return
            if (result.response.status === 404) {
                setNotFound(true)
                setLoadFailed(false)
                stopPolling = true
            } else if (result.data) {
                setNotFound(false)
                setLoadFailed(false)
                setData(result.data)
                setLastUpdated(new Date())
                intervalRef.current = intervalOf(result.data)
            } else {
                // Antwort ohne Nutzdaten (z. B. HTTP 500): als fehlgeschlagenen Versuch werten,
                // statt ihn stillschweigend zu ignorieren.
                setLoadFailed(true)
            }
        } catch (err) {
            if (err instanceof DOMException && err.name === 'AbortError') {
                // Ein neuerer load()-Aufruf hat diesen Abruf abgelöst — kein echter Fehler.
            } else if (mountedRef.current) {
                // Netzabbruch o.ä.: letzten guten Stand stehen lassen, aber als
                // fehlgeschlagenen Versuch markieren (siehe loadFailed oben).
                setLoadFailed(true)
            }
        } finally {
            // Wurde dieser Aufruf zwischenzeitlich durch einen neueren abgelöst, überlässt
            // er Timer und State vollständig dem neueren Aufruf.
            if (mountedRef.current && abortRef.current === controller) {
                setInitialLoad(false)
                clearTimer()
                if (!stopPolling && !document.hidden) {
                    timerRef.current = window.setTimeout(() => {
                        void runLoad()
                    }, intervalRef.current * 1000)
                }
            }
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, deps)

    useEffect(() => {
        void runLoad()

        const onVisibilityChange = () => {
            if (document.hidden) {
                clearTimer()
            } else {
                void runLoad()
            }
        }

        document.addEventListener('visibilitychange', onVisibilityChange)
        return () => {
            document.removeEventListener('visibilitychange', onVisibilityChange)
            clearTimer()
            abortRef.current?.abort()
        }
    }, [runLoad])

    return {data, lastUpdated, notFound, initialLoad, loadFailed}
}

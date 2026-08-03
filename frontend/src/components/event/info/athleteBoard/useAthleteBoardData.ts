import {useCallback, useEffect, useRef, useState} from 'react'
import {getAthleteBoard} from '@api/sdk.gen'
import {AthleteBoardDto} from '@api/types.gen'

const FALLBACK_INTERVAL_SECONDS = 15

export interface AthleteBoardState {
    data: AthleteBoardDto | null
    lastUpdated: Date | null
    notFound: boolean
    initialLoad: boolean
    // true, solange noch nie erfolgreich geladen wurde und der letzte Versuch
    // fehlgeschlagen ist (Backend tot, HTTP-Fehler, kein Netz). Sobald einmal ein Abruf
    // erfolgreich war, bleibt `data` stehen und dieses Flag ist für die Anzeige irrelevant
    // — der letzte gute Stand hat Vorrang, siehe Hook-Kommentar unten.
    loadFailed: boolean
}

/**
 * Lädt die Athleten-Anzeige im Takt, den der Server vorgibt.
 *
 * Drei Eigenschaften sind hier wichtiger als Kürze:
 * - Bei einem Netzabbruch bleibt der letzte gute Stand stehen. Ein fest montierter
 *   Bildschirm, der nach einem Aussetzer leer bleibt, ist der schlechteste Ausgang.
 * - Scheitert dagegen der allererste Abruf (also bevor je ein guter Stand da war),
 *   muss das von außen von "geladen, aber leer" unterscheidbar sein — sonst behauptet
 *   die Anzeige fälschlich, es sei kein Lauf auf dem Wasser.
 * - Im Hintergrund wird nicht geladen; beim Zurückkehren sofort einmal.
 */
export const useAthleteBoardData = (eventId: string): AthleteBoardState => {
    const [data, setData] = useState<AthleteBoardDto | null>(null)
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

    const load = useCallback(async () => {
        abortRef.current?.abort()
        const controller = new AbortController()
        abortRef.current = controller

        // Ein falsch gedruckter QR-Code soll nicht dauerhaft gegen einen toten Endpoint
        // takten: nach einem 404 hält der Takt an, die Meldung bleibt stehen.
        let stopPolling = false

        try {
            const result = await getAthleteBoard({signal: controller.signal, path: {eventId}})
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
                intervalRef.current =
                    result.data.refreshIntervalSeconds > 0
                        ? result.data.refreshIntervalSeconds
                        : FALLBACK_INTERVAL_SECONDS
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
                        void load()
                    }, intervalRef.current * 1000)
                }
            }
        }
    }, [eventId])

    useEffect(() => {
        void load()

        const onVisibilityChange = () => {
            if (document.hidden) {
                clearTimer()
            } else {
                void load()
            }
        }

        document.addEventListener('visibilitychange', onVisibilityChange)
        return () => {
            document.removeEventListener('visibilitychange', onVisibilityChange)
            clearTimer()
            abortRef.current?.abort()
        }
    }, [load])

    return {data, lastUpdated, notFound, initialLoad, loadFailed}
}

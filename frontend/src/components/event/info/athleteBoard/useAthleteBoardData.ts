import {useCallback, useEffect, useRef, useState} from 'react'
import {getAthleteBoard} from '@api/sdk.gen'
import {AthleteBoardDto} from '@api/types.gen'

const FALLBACK_INTERVAL_SECONDS = 15

export interface AthleteBoardState {
    data: AthleteBoardDto | null
    lastUpdated: Date | null
    notFound: boolean
    initialLoad: boolean
}

/**
 * Lädt die Athleten-Anzeige im Takt, den der Server vorgibt.
 *
 * Zwei Eigenschaften sind hier wichtiger als Kürze:
 * - Bei einem Netzabbruch bleibt der letzte gute Stand stehen. Ein fest montierter
 *   Bildschirm, der nach einem Aussetzer leer bleibt, ist der schlechteste Ausgang.
 * - Im Hintergrund wird nicht geladen; beim Zurückkehren sofort einmal.
 */
export const useAthleteBoardData = (eventId: string): AthleteBoardState => {
    const [data, setData] = useState<AthleteBoardDto | null>(null)
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
    const [notFound, setNotFound] = useState(false)
    const [initialLoad, setInitialLoad] = useState(true)

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

        try {
            const result = await getAthleteBoard({signal: controller.signal, path: {eventId}})
            if (!mountedRef.current) return
            if (result.response.status === 404) {
                setNotFound(true)
            } else if (result.data) {
                setNotFound(false)
                setData(result.data)
                setLastUpdated(new Date())
                intervalRef.current =
                    result.data.refreshIntervalSeconds > 0
                        ? result.data.refreshIntervalSeconds
                        : FALLBACK_INTERVAL_SECONDS
            }
        } catch {
            // Netzabbruch: letzten guten Stand stehen lassen und im nächsten Takt neu versuchen.
        } finally {
            if (mountedRef.current) {
                setInitialLoad(false)
                clearTimer()
                if (!document.hidden) {
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

    return {data, lastUpdated, notFound, initialLoad}
}

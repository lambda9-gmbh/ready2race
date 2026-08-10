import {useEffect, useRef, useState} from 'react'
import {PollerState, createPoller, initialPollerState} from '@utils/polling.ts'

/**
 * Lädt im Hintergrund nach, ohne die Seite neu zu laden.
 *
 * Der Hook ist absichtlich nur Verdrahtung: was der Takt leistet — kein Überlappen, letzter guter
 * Stand bei Fehlern, Pause im Hintergrund — steht in [createPoller] und ist dort ohne DOM geprüft.
 * Hier kommen nur die beiden Ereignisse des Browsers dazu:
 *
 * - `visibilitychange`: im Hintergrund wird nicht getaktet; beim Zurückkehren sofort einmal.
 * - `online`: nach einem Funkloch soll der Stand nicht bis zum nächsten Takt alt bleiben.
 *
 * [load] darf sich bei jedem Rendern ändern (Pfeilfunktion im Aufrufer); der Takt wird deshalb
 * über [deps] gesteuert und nicht über die Identität der Funktion — sonst startete er bei jedem
 * Rendern neu.
 */
export const usePolledFetch = <T,>(
    load: (signal: AbortSignal) => Promise<T | null>,
    intervalMs: number,
    deps: unknown[],
): PollerState<T> => {
    const [state, setState] = useState<PollerState<T>>(initialPollerState<T>)

    const loadRef = useRef(load)
    loadRef.current = load

    useEffect(() => {
        // Neue Abhängigkeiten heißen: andere Daten. Der alte Stand gehörte zu einer anderen
        // Veranstaltung und darf nicht stehen bleiben.
        setState(initialPollerState<T>())

        const poller = createPoller<T>({
            load: signal => loadRef.current(signal),
            intervalMs,
            onState: setState,
        })
        poller.start()
        // Hängt sich der Hook in einem bereits versteckten Tab ein, feuert kein
        // `visibilitychange` mehr - ohne diese Prüfung würde bis zur ersten Sichtbarkeitsänderung
        // trotzdem im Hintergrund weitergetaktet, im Widerspruch zum JSDoc oben. Der erste Abruf
        // aus `poller.start()` läuft trotzdem durch: `suspend()` hält nur den Timer für den
        // NÄCHSTEN Abruf an, sodass beim Zurückkehren schon Daten da sind.
        if (document.hidden) {
            poller.suspend()
        }

        const onVisibilityChange = () => {
            if (document.hidden) {
                poller.suspend()
            } else {
                poller.resume()
            }
        }
        const onOnline = () => poller.refreshNow()

        document.addEventListener('visibilitychange', onVisibilityChange)
        window.addEventListener('online', onOnline)
        return () => {
            document.removeEventListener('visibilitychange', onVisibilityChange)
            window.removeEventListener('online', onOnline)
            poller.stop()
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [intervalMs, ...deps])

    return state
}

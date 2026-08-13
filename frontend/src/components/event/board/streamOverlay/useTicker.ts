import {useEffect, useState} from 'react'

/**
 * Aktuelle Client-Zeit, aktualisiert alle `intervalMs` — solange `enabled` ist. Ohne
 * Bedarf (z. B. kein Countdown zu zeigen, kein Lauf gestartet) läuft kein
 * Intervall-Timer im Hintergrund weiter.
 */
const useTicker = (intervalMs: number, enabled: boolean): number => {
    const [now, setNow] = useState(() => Date.now())

    useEffect(() => {
        if (!enabled) return
        setNow(Date.now())
        const id = setInterval(() => setNow(Date.now()), intervalMs)
        return () => clearInterval(id)
    }, [intervalMs, enabled])

    return now
}

export default useTicker

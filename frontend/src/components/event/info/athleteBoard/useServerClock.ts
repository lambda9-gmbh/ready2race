import {useEffect, useRef, useState} from 'react'

/**
 * Liefert die laufende Serverzeit im Sekundentakt.
 *
 * Verankert wird an der `serverTime` der letzten Antwort; lokal wird nur die seither
 * verstrichene Zeit addiert. Damit zeigt auch ein Bildschirm mit falsch gestellter Uhr
 * den richtigen Countdown.
 */
export const useServerClock = (serverTime: string | undefined): Date => {
    const [tick, setTick] = useState(() => Date.now())
    const anchorRef = useRef<{server: number; local: number} | null>(null)

    if (serverTime) {
        const server = new Date(serverTime).getTime()
        if (!Number.isNaN(server) && anchorRef.current?.server !== server) {
            anchorRef.current = {server, local: Date.now()}
        }
    }

    useEffect(() => {
        const id = window.setInterval(() => setTick(Date.now()), 1000)
        return () => window.clearInterval(id)
    }, [])

    const anchor = anchorRef.current
    return anchor ? new Date(anchor.server + (tick - anchor.local)) : new Date(tick)
}

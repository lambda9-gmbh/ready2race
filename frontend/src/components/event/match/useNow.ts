import {useEffect, useState} from 'react'

/**
 * Uhr für die verstrichenen Minuten auf den Status-Chips („Läuft · 4 min", „Überfällig · 8 min").
 *
 * Ohne eigene Uhr stünde die Zahl auf dem Chip still und behauptete nach einer Viertelstunde noch
 * immer „Läuft · 1 min" — auch dort, wo die Ansicht im Hintergrund nachlädt: der Chip zählt so
 * zwischen zwei Abrufen weiter. 30 Sekunden reichen für eine Minutenangabe.
 */
export const useNow = (intervalMs = 30_000): Date => {
    const [now, setNow] = useState(() => new Date())
    useEffect(() => {
        const id = window.setInterval(() => setNow(new Date()), intervalMs)
        return () => window.clearInterval(id)
    }, [intervalMs])
    return now
}

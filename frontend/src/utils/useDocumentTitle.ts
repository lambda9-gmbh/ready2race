import {useEffect} from 'react'

const BASE = 'Ready2Race'

/**
 * Setzt den Tab-Titel und stellt beim Verlassen wieder `Ready2Race` her.
 *
 * Bis hierher stand in jedem Tab nur „Ready2Race" (statisches <title> in index.html) — bei
 * mehreren offenen Tabs (Zeitplan, Schiedsrichter-Board, Ergebnisse einer Regatta) waren sie
 * nicht auseinanderzuhalten. [parts] werden zu „Teil · Teil · Ready2Race" zusammengesetzt; leere
 * Teile (z. B. ein noch nicht geladener Eventname) fallen weg, bis die Daten da sind.
 */
export const useDocumentTitle = (...parts: Array<string | null | undefined>) => {
    const title = [...parts.filter((p): p is string => !!p && p.trim() !== ''), BASE].join(' · ')
    useEffect(() => {
        const previous = document.title
        document.title = title
        return () => {
            document.title = previous
        }
    }, [title])
}

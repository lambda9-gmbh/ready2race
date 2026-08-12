import {useCallback, useEffect, useState} from 'react'

/**
 * Geräte-lokale Ja/Nein-Einstellungen nach dem Muster von `shortLabels.ts`: Der Wert liegt im
 * localStorage (überlebt Reload und Browser-Neustart, gilt aber nur für diesen Browser auf diesem
 * Gerät) und wird zusätzlich über ein Fenster-Ereignis verteilt — `storage` feuert nur in ANDEREN
 * Tabs, zwei Abnehmer im selben Tab liefen sonst auseinander.
 *
 * Anders als bei `shortLabels` gibt es hier keine „noch keine Wahl getroffen"-Stufe: Diese
 * Schalter haben eine feste Voreinstellung, und der gespeicherte Wert ersetzt sie einfach.
 */

/** Öffnet der Sprung „Zur Durchführung" aus dem Zeitplan in einem neuen Fenster? */
export const EXECUTION_NEW_TAB_KEY = 'schedule_execution_new_tab'
/**
 * Veranstaltungs-Modus des Zeitplan-Tabs: Zeitplan und Durchführung nebeneinander statt des
 * Sprungs auf die Wettkampf-Seite. Geräte-lokal, weil die Wahl am Arbeitsplatz hängt — der
 * Leitstand-Rechner im Regattabüro arbeitet so, das Tablet am Steg nicht.
 */
export const SCHEDULE_EVENT_MODE_KEY = 'schedule_event_mode'
/** Kompakte Darstellung des Schiedsrichter-Boards (dichtere Karten, kleinere Schrift). */
export const DASHBOARD_COMPACT_KEY = 'live_dashboard_compact'

// --- Schiedsrichter-Board: die acht Anpassungen vom 12.08.2026 -------------------------------
// Alle geräte-lokal, alle im selben Muster. Die Voreinstellungen stehen bei den jeweiligen
// Abnehmern (LiveDashboardPage / DashboardSettingsPopover), nicht hier — hier liegen nur die
// Schlüssel, damit kein zweiter Ort dieselbe Zeichenkette erfindet.

/**
 * Mehrfachauswahl der Wettkämpfe (JSON-Liste von competitionIds, leer = alle). Bewusst je
 * Veranstaltung ein eigener Schlüssel (siehe [dashboardCompetitionFilterKey]): die Ids einer
 * anderen Veranstaltung würden hier sonst ALLE Läufe wegfiltern — genau das „Verlieren" von
 * Läufen am Renntag, das der Filter-Chip verhindern soll.
 */
export const dashboardCompetitionFilterKey = (eventId: string): string =>
    `live_dashboard_competition_filter_${eventId}`
/** Läufe-Spalte nur mit dem aktuellen Renntag (der Tag des Zeitstrahl-Indikators). */
export const DASHBOARD_ONLY_TODAY_KEY = 'live_dashboard_only_today'
/** Beendete Läufe in der Läufe-Spalte ausblenden (die letzten zwei bleiben als Kontext). */
export const DASHBOARD_HIDE_FINISHED_KEY = 'live_dashboard_hide_finished'
/** Auto-Zentrierung des laufenden/nächsten Laufs in der Läufe-Spalte bei Datenupdates. */
export const DASHBOARD_FOLLOW_CURRENT_KEY = 'live_dashboard_follow_current'
/** Jüngste Schiedsrichter-Notiz als einzeilige Vorschau an der Bootszeile. */
export const DASHBOARD_NOTE_PREVIEW_KEY = 'live_dashboard_note_preview'
/** Crew-Zeilen (Aufstellung) auf den Karten zeigen — radikaler als der Kompaktmodus. */
export const DASHBOARD_SHOW_CREW_KEY = 'live_dashboard_show_crew'
/** Dreistufige Schriftgröße der Karten (normal / large / xlarge), neben dem Kompaktmodus. */
export const DASHBOARD_FONT_SCALE_KEY = 'live_dashboard_font_scale'
/** An den Bootszeilen nur noch Prüfungs-Icons mit Severity CRITICAL zeigen. */
export const DASHBOARD_CRITICAL_CHECKS_ONLY_KEY = 'live_dashboard_critical_checks_only'

const CHANGE_EVENT = 'r2r:device-setting'

/**
 * Der gespeicherte Wert, oder [fallback], solange keiner gespeichert ist. Abgesichert gegen
 * Umgebungen ohne localStorage (privater Modus, Node in den Tests) — dann gilt die Voreinstellung.
 */
export const storedFlag = (key: string, fallback: boolean): boolean => {
    try {
        const value = localStorage.getItem(key)
        return value === null ? fallback : value === 'true'
    } catch {
        return fallback
    }
}

/**
 * Speichert die Wahl und stößt die Verteilung im selben Tab an. Ein voller oder gesperrter
 * Speicher schluckt nur die Persistenz — die Abnehmer im Tab bekommen den Klick trotzdem nicht
 * mit, weil es keinen neuen Wert zu lesen gibt; das ist der ehrlichere der beiden Fehler.
 */
export const setStoredFlag = (key: string, value: boolean): void => {
    try {
        localStorage.setItem(key, String(value))
    } catch {
        return
    }
    window.dispatchEvent(new Event(CHANGE_EVENT))
}

/**
 * Der gespeicherte Zeichenketten-Wert, oder [fallback] — dasselbe Muster wie [storedFlag] für
 * Einstellungen mit mehr als zwei Stufen (z. B. die dreistufige Schriftgröße). [allowed] hält
 * kaputte oder veraltete Ablagen draußen: was nicht in der Liste steht, gilt als nicht gespeichert.
 */
export const storedChoice = <T extends string>(
    key: string,
    fallback: T,
    allowed: readonly T[],
): T => {
    try {
        const value = localStorage.getItem(key)
        return value !== null && (allowed as readonly string[]).includes(value)
            ? (value as T)
            : fallback
    } catch {
        return fallback
    }
}

/** Speichert eine Stufen-Wahl — dieselbe Fehler-Abwägung wie [setStoredFlag]. */
export const setStoredChoice = (key: string, value: string): void => {
    try {
        localStorage.setItem(key, value)
    } catch {
        return
    }
    window.dispatchEvent(new Event(CHANGE_EVENT))
}

/**
 * Die gespeicherte Liste (JSON-Array aus Zeichenketten), oder [] — für die Mehrfachauswahl der
 * Wettkämpfe. Alles, was kein sauberes Zeichenketten-Array ist (kaputtes JSON, gemischte Typen),
 * gilt als nicht gespeichert: lieber ungefiltert zeigen als mit einem Ratewert Läufe verstecken.
 */
export const storedList = (key: string): string[] => {
    try {
        const raw = localStorage.getItem(key)
        if (raw === null) {
            return []
        }
        const parsed: unknown = JSON.parse(raw)
        return Array.isArray(parsed) && parsed.every(entry => typeof entry === 'string')
            ? parsed
            : []
    } catch {
        return []
    }
}

/** Speichert die Liste als JSON — dieselbe Fehler-Abwägung wie [setStoredFlag]. */
export const setStoredList = (key: string, value: string[]): void => {
    try {
        localStorage.setItem(key, JSON.stringify(value))
    } catch {
        return
    }
    window.dispatchEvent(new Event(CHANGE_EVENT))
}

/**
 * Gemeinsames Innenleben der drei Hooks: liest über [read] und hält den Wert über das
 * Fenster-Ereignis (gleicher Tab) und `storage` (andere Tabs) synchron.
 */
const useSyncedValue = <T>(read: () => T): T => {
    const [value, setValue] = useState(read)

    useEffect(() => {
        // Der JSON-Vergleich hält die Referenz stabil, wenn sich nichts geändert hat — [read]
        // liefert bei Listen sonst bei jedem Ereignis ein frisches Array, und jede fremde
        // Einstellungs-Änderung würde alle Listen-Abnehmer grundlos neu rendern.
        const sync = () =>
            setValue(prev => {
                const next = read()
                return JSON.stringify(prev) === JSON.stringify(next) ? prev : next
            })
        sync()
        window.addEventListener(CHANGE_EVENT, sync)
        // Aus einem zweiten Tab derselben Veranstaltung — dort feuert nur `storage`.
        window.addEventListener('storage', sync)
        return () => {
            window.removeEventListener(CHANGE_EVENT, sync)
            window.removeEventListener('storage', sync)
        }
    }, [read])

    return value
}

/** React-Anbindung: liest die Einstellung und hält sie über Tabs und Abnehmer hinweg synchron. */
export const useDeviceFlag = (
    key: string,
    fallback: boolean = false,
): [boolean, (value: boolean) => void] => {
    const read = useCallback(() => storedFlag(key, fallback), [key, fallback])
    const value = useSyncedValue(read)
    const set = useCallback((next: boolean) => setStoredFlag(key, next), [key])
    return [value, set]
}

/** Wie [useDeviceFlag], für Stufen-Einstellungen mit fester Werteliste. */
export const useDeviceChoice = <T extends string>(
    key: string,
    fallback: T,
    allowed: readonly T[],
): [T, (value: T) => void] => {
    // `allowed` ist bei den Aufrufern ein Modul-Konstantenarray — die Referenz ist stabil.
    const read = useCallback(() => storedChoice(key, fallback, allowed), [key, fallback, allowed])
    const value = useSyncedValue(read)
    const set = useCallback((next: T) => setStoredChoice(key, next), [key])
    return [value, set]
}

/** Wie [useDeviceFlag], für Listen-Einstellungen (Mehrfachauswahl). */
export const useDeviceList = (key: string): [string[], (value: string[]) => void] => {
    const read = useCallback(() => storedList(key), [key])
    const value = useSyncedValue(read)
    const set = useCallback((next: string[]) => setStoredList(key, next), [key])
    return [value, set]
}

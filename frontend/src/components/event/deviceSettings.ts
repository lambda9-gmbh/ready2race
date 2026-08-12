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

/** React-Anbindung: liest die Einstellung und hält sie über Tabs und Abnehmer hinweg synchron. */
export const useDeviceFlag = (
    key: string,
    fallback: boolean = false,
): [boolean, (value: boolean) => void] => {
    const [value, setValue] = useState(() => storedFlag(key, fallback))

    useEffect(() => {
        const sync = () => setValue(storedFlag(key, fallback))
        sync()
        window.addEventListener(CHANGE_EVENT, sync)
        // Aus einem zweiten Tab derselben Veranstaltung — dort feuert nur `storage`.
        window.addEventListener('storage', sync)
        return () => {
            window.removeEventListener(CHANGE_EVENT, sync)
            window.removeEventListener('storage', sync)
        }
    }, [key, fallback])

    const set = useCallback((next: boolean) => setStoredFlag(key, next), [key])

    return [value, set]
}

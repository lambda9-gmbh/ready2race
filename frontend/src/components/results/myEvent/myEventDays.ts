import {format} from 'date-fns'

/**
 * Tagesgruppierung für „Mein Event": bei einer Regatta über mehrere Tage stand an den Läufen
 * nur die Uhrzeit — am zweiten Regattatag war nicht zu erkennen, welche Einträge heute und
 * welche morgen oder gestern dran sind (Rückmeldung vom Regattatag am 14.08.2026).
 */

export type DayGroup<T> = {day: string | null; items: T[]}

/** Kalendertag eines Zeitstempels als Gruppenschlüssel; null ohne Zeit. */
export const dayKeyOf = (startTime?: string | null): string | null =>
    startTime ? format(new Date(startTime), 'yyyy-MM-dd') : null

/**
 * Gruppiert aufeinanderfolgende Einträge nach Kalendertag — dasselbe Muster wie der Tab
 * „Zeitplan" der öffentlichen Ergebnisanzeige (ResultsProgram). Die Sortierung des Servers
 * bleibt unangetastet: kommende Läufe aufsteigend, Ergebnisse neuestes zuerst — die Gruppen
 * folgen einfach der Reihenfolge. Einträge ohne Startzeit bilden Gruppen ohne Tag; ihre
 * Zeile sagt selbst „noch nicht terminiert" und braucht keine Überschrift.
 */
export const groupByDay = <T extends {startTime?: string | null}>(items: T[]): DayGroup<T>[] =>
    items.reduce<DayGroup<T>[]>((groups, item) => {
        const day = dayKeyOf(item.startTime)
        const last = groups[groups.length - 1]
        if (last && last.day === day) {
            last.items.push(item)
        } else {
            groups.push({day, items: [item]})
        }
        return groups
    }, [])

type DatedEntry = {startTime?: string | null}

/**
 * Ob die Listen Datums-Zwischenüberschriften brauchen. Bei einer eintägigen Veranstaltung
 * bleibt die Ansicht schlank wie bisher — eine Tagesüberschrift, die nur den heutigen Tag
 * wiederholt, trägt nichts. Gezeigt wird das Datum, sobald die eigenen Einträge mehr als
 * einen Kalendertag berühren, oder wenn alles an einem anderen Tag als heute liegt (wer am
 * Vorabend aufs Telefon schaut, soll „morgen" erkennen, nicht „gleich").
 */
export const showDayHeadings = (data: {
    running: DatedEntry[]
    upcoming: DatedEntry[]
    results: DatedEntry[]
    serverTime: string
}): boolean => {
    const days = new Set(
        [...data.running, ...data.upcoming, ...data.results]
            .map(entry => dayKeyOf(entry.startTime))
            .filter((day): day is string => day !== null),
    )
    if (days.size > 1) {
        return true
    }
    // Gerechnet wird gegen die Uhr des Servers, nicht des Telefons — wie überall auf
    // dieser Seite (siehe MyEventMatchList).
    return days.size === 1 && !days.has(dayKeyOf(data.serverTime)!)
}

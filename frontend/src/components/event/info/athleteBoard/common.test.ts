import {describe, expect, test} from 'vitest'
import {TFunction} from 'i18next'
import {COUNTDOWN_MAX_SECONDS, formatRemaining, isSameDay, scaled, sortRunningTeams, teamLabel} from './common'

// Gibt den letzten Abschnitt des Schlüssels zurück, damit die Erwartungen unabhängig
// von den echten Übersetzungen lesbar bleiben: "…hoursUnit" -> "hoursUnit".
const t = ((key: string) => key.split('.').pop()) as unknown as TFunction

describe('formatRemaining', () => {
    test('unter einer Minute in Sekunden', () => {
        expect(formatRemaining(45, t)).toBe('45 secondsUnit')
    })

    test('unter einer Stunde in Minuten', () => {
        expect(formatRemaining(20 * 60, t)).toBe('20 minutesUnit')
    })

    test('ab einer Stunde mit Stundenstufe', () => {
        expect(formatRemaining(3 * 3600 + 12 * 60, t)).toBe('3 hoursUnit 12 minutesUnit')
    })

    test('volle Stunde ohne Minutenrest', () => {
        expect(formatRemaining(2 * 3600, t)).toBe('2 hoursUnit')
    })

    // Der Fehler, der die Korrektur ausgelöst hat: ein Lauf in einer Woche ergab
    // "in 9886 min" statt einer lesbaren Größenordnung.
    test('grosse Abstaende erzeugen keine sechsstellige Minutenzahl', () => {
        const result = formatRemaining(9886 * 60, t)
        expect(result).not.toContain('9886')
        expect(result).toContain('hoursUnit')
    })

    test('negative Werte werden auf null geklemmt', () => {
        expect(formatRemaining(-500, t)).toBe('0 secondsUnit')
    })
})

describe('isSameDay', () => {
    test('gleicher Kalendertag', () => {
        expect(isSameDay(new Date(2026, 7, 7, 8, 0), new Date(2026, 7, 7, 23, 59))).toBe(true)
    })

    test('benachbarte Tage', () => {
        expect(isSameDay(new Date(2026, 7, 7, 23, 59), new Date(2026, 7, 8, 0, 1))).toBe(false)
    })

    test('gleicher Tag im anderen Jahr', () => {
        expect(isSameDay(new Date(2026, 7, 7), new Date(2025, 7, 7))).toBe(false)
    })
})

// Welche der beiden Ketten sichtbar wird, entscheidet eine Media Query und ist in jsdom nicht
// prüfbar. Prüfbar ist, was die Zeile für die jeweilige Stufe zusammensetzt.
describe('teamLabel', () => {
    const mixed = {
        clubsShort: 'Mainzer RV / RK Flensburg',
        clubsFull: 'Mainzer Ruder-Verein 1878 e.V. / Ruderklub Flensburg e.V.',
        teamName: null,
        teamNumber: 2,
    }

    test('grosser Schirm: volle Vereinsnamen', () => {
        expect(teamLabel(mixed, t, 'full')).toBe(
            'Mainzer Ruder-Verein 1878 e.V. / Ruderklub Flensburg e.V. | teamNumber',
        )
    })

    test('schmaler Viewport: Kurzformen', () => {
        expect(teamLabel(mixed, t, 'short')).toBe('Mainzer RV / RK Flensburg | teamNumber')
    })

    test('gepflegter Mannschaftsname verdraengt die Nummer', () => {
        expect(teamLabel({...mixed, teamName: 'Mix Nord'}, t, 'short')).toBe(
            'Mainzer RV / RK Flensburg | Mix Nord',
        )
    })

    // Eine Zeile ohne jeden Verein waere schlechter als eine in der falschen Laenge.
    test('fehlende Kurzform faellt auf die volle Kette zurueck', () => {
        expect(teamLabel({clubsFull: 'Rostocker Ruderclub'}, t, 'short')).toBe('Rostocker Ruderclub')
    })

    test('ohne Verein bleibt der Mannschaftsname allein stehen', () => {
        expect(teamLabel({teamName: 'Mix Nord'}, t, 'full')).toBe('Mix Nord')
    })
})

describe('COUNTDOWN_MAX_SECONDS', () => {
    test('entspricht einem Tag', () => {
        expect(COUNTDOWN_MAX_SECONDS).toBe(86400)
    })
})

describe('scaled', () => {
    test('haengt die Groesse an die Dichte der Buehne', () => {
        expect(scaled('1rem', '2vw', '3rem')).toBe(
            'calc(var(--ab-scale, 1) * clamp(1rem, 2vw, 3rem))',
        )
    })
})

describe('sortRunningTeams', () => {
    const team = (
        startNumber: number,
        place: number | null = null,
        failed = false,
        timeString: string | null = null,
    ) => ({startNumber, place, failed, timeString})

    // Der Auslöser (11.08.2026, Zeitfahren am Prod-Abzug): Startreihenfolge 1–8, Plätze
    // kreuz und quer daneben — sobald Zwischenstände da sind, zählt die Platzierung.
    test('mit Zwischenständen sortiert die Platzierung, DNF/DQ ans Ende', () => {
        const sorted = sortRunningTeams([
            team(1, 1, false, '0:02:04.3'),
            team(2, 4, false, '0:05:27.3'),
            team(3, 3, false, '0:05:21.2'),
            team(4, 5, false, '0:05:39.9'),
            team(5, null, true),
            team(6, 2, false, '0:04:41.2'),
            team(7, null, true),
            team(8, null, true),
        ])
        expect(sorted.map(t => t.startNumber)).toEqual([1, 6, 3, 2, 4, 5, 7, 8])
    })

    test('ohne jedes Teilergebnis bleibt die Startreihenfolge', () => {
        const teams = [team(3), team(1), team(2)]
        expect(sortRunningTeams(teams).map(t => t.startNumber)).toEqual([3, 1, 2])
    })

    // Noch fahrende Boote (ohne Platz, nicht ausgeschieden) stehen zwischen den
    // Gewerteten und den Ausgeschiedenen, in Startreihenfolge.
    test('noch fahrende Boote stehen hinter den gewerteten, vor DNF/DQ', () => {
        const sorted = sortRunningTeams([
            team(1),
            team(2, 1, false, '0:04:00.0'),
            team(3, null, true),
        ])
        expect(sorted.map(t => t.startNumber)).toEqual([2, 1, 3])
    })
})

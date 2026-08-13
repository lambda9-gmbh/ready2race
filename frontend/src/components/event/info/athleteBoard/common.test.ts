import {describe, expect, test} from 'vitest'
import {TFunction} from 'i18next'
import {COUNTDOWN_MAX_SECONDS, compactLapLabel, finishComplete, formatClockTimeWithSeconds, formatRemaining, isSameDay, scaled, sortRunningTeams, teamLabel} from './common'

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

// Die Kurzform der Rundennamen an der Zeit: nur „ein Wort plus Zahl" wird eingedampft,
// freier Text aus RaceClocker bleibt unangetastet.
describe('compactLapLabel', () => {
    test('Runde 1 wird zu R1', () => {
        expect(compactLapLabel('Runde 1')).toBe('R1')
        expect(compactLapLabel('Runde 12')).toBe('R12')
    })

    test('englische Namen ebenso', () => {
        expect(compactLapLabel('Lap 2')).toBe('L2')
    })

    test('ohne Leerzeichen zwischen Wort und Zahl', () => {
        expect(compactLapLabel('Runde2')).toBe('R2')
    })

    // Eine Marke wie „500m" beginnt mit einer Zahl und passt nicht ins Muster —
    // sie bleibt stehen, bevor eine zu forsche Kürzung den Sinn kostet.
    test('freie Namen bleiben unverändert', () => {
        expect(compactLapLabel('500m')).toBe('500m')
        expect(compactLapLabel('Boje Ost')).toBe('Boje Ost')
    })

    test('kleingeschriebene Namen liefern ein grosses Kürzel', () => {
        expect(compactLapLabel('runde 3')).toBe('R3')
    })
})

describe('scaled', () => {
    test('haengt die Groesse an die Dichte der Buehne', () => {
        expect(scaled('1rem', '2vw', '3rem')).toBe(
            'calc(var(--ab-scale, 1) * clamp(1rem, 2vw, 3rem))',
        )
    })
})

// Die Wartestand-Kennzeichnung der „Im Rennen"-Karte: genau dann, wenn jedes Boot
// Platz/Zeit hat oder gescheitert ist — dieselbe Auslegung wie teamHasResult im Backend.
describe('finishComplete', () => {
    test('alle Boote gewertet oder gescheitert: komplett', () => {
        expect(finishComplete([{place: 1}, {place: 2}, {failed: true}])).toBe(true)
    })

    test('ein Boot noch ohne Ergebnis: nicht komplett', () => {
        expect(finishComplete([{place: 1}, {place: null, failed: false}])).toBe(false)
    })

    // Leere Aufstellung (Platzhalter, wartende Runde): kein Zieleinlauf, kein Band.
    test('ohne Boote nie komplett', () => {
        expect(finishComplete([])).toBe(false)
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

describe('formatClockTimeWithSeconds', () => {
    // Locale-tolerant geprüft (Trennzeichen und 12/24h variieren mit der Testumgebung):
    // entscheidend ist, dass die Sekunden dabei sind — bei Einzelstarts zählen sie.
    test('trägt die Sekunden', () => {
        expect(formatClockTimeWithSeconds('2026-08-14T10:31:04')).toMatch(/10[:.]31[:.]04/)
    })

    test('unterscheidet Boote, die dieselbe Minute starten', () => {
        const a = formatClockTimeWithSeconds('2026-08-14T10:31:04')
        const b = formatClockTimeWithSeconds('2026-08-14T10:31:34')
        expect(a).not.toBe(b)
    })
})

import {describe, expect, test} from 'vitest'
import {TFunction} from 'i18next'
import {COUNTDOWN_MAX_SECONDS, formatRemaining, isSameDay} from './common'

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

describe('COUNTDOWN_MAX_SECONDS', () => {
    test('entspricht einem Tag', () => {
        expect(COUNTDOWN_MAX_SECONDS).toBe(86400)
    })
})

import {describe, expect, test} from 'vitest'
import {dateTimeToFormValue, invalidDateTimeValue, nowAsFormValue} from './dateTimeValue'

describe('dateTimeToFormValue', () => {
    test('gültiger Zeitpunkt wird zur lokalen Zeit ohne Zone', () => {
        expect(dateTimeToFormValue(new Date(2026, 7, 14, 10, 35))).toBe('2026-08-14T10:35:00')
        // Minutengenau: die Zeit darf nicht auf 5er-Schritte gerundet werden.
        expect(dateTimeToFormValue(new Date(2026, 7, 14, 10, 37))).toBe('2026-08-14T10:37:00')
    })

    test('leeres Feld bleibt leer', () => {
        expect(dateTimeToFormValue(null)).toBeUndefined()
    })

    test('Eingabezustand: halb getippte Zeit stürzt nicht ab, sondern bleibt ungültig', () => {
        // Der Bug: `formatISO` wirft bei einem ungültigen Datum eine RangeError. Weil der
        // Picker beim Tippen jeden Zwischenstand meldet, riss der erste Tastendruck in ein
        // leeres Feld die ganze Seite mit - deshalb war das Feld bisher schreibgeschützt.
        expect(() => dateTimeToFormValue(new Date('nicht fertig'))).not.toThrow()
        expect(dateTimeToFormValue(new Date('nicht fertig'))).toBe(invalidDateTimeValue)
    })

    test('der Marker ergibt zurückgelesen wieder ein ungültiges Datum', () => {
        // Nur dann behält MUI die getippten Stellen im Feld stehen.
        expect(Number.isNaN(new Date(invalidDateTimeValue).getTime())).toBe(true)
    })
})

describe('nowAsFormValue', () => {
    test('liefert ein Format, das der Picker zurücklesen kann', () => {
        const value = nowAsFormValue()
        expect(value).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/)
        expect(Number.isNaN(new Date(value).getTime())).toBe(false)
    })

    test('ohne krumme Sekunden - das Feld geht nur bis zur Minute', () => {
        expect(nowAsFormValue().endsWith(':00')).toBe(true)
    })
})

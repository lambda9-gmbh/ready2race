import {describe, expect, test} from 'vitest'
import {delayColor, delayParts, latestStartDelaySeconds} from './scheduleDelay'

describe('delayParts', () => {
    test('unter ±60 Sekunden gilt der Zeitplan als eingehalten', () => {
        expect(delayParts(0)).toEqual({kind: 'onTime', minutes: 0})
        expect(delayParts(59)).toEqual({kind: 'onTime', minutes: 0})
        expect(delayParts(-59)).toEqual({kind: 'onTime', minutes: 0})
    })

    test('rundet auf ganze Minuten, Verfrühung eigenes Vorzeichen', () => {
        expect(delayParts(18 * 60)).toEqual({kind: 'late', minutes: 18})
        expect(delayParts(90)).toEqual({kind: 'late', minutes: 2})
        expect(delayParts(-300)).toEqual({kind: 'early', minutes: 5})
    })
})

describe('delayColor', () => {
    // Verzug warnt, „pünktlich" bestätigt grün, Verfrühung bleibt dezent —
    // Begründung (auch gegen Info-Blau) am Helfer selbst.
    test('färbt nach Lage', () => {
        expect(delayColor('late')).toBe('warning.main')
        expect(delayColor('onTime')).toBe('success.main')
        expect(delayColor('early')).toBe('text.secondary')
    })
})

describe('latestStartDelaySeconds', () => {
    test('nichts gestartet: keine Aussage', () => {
        expect(latestStartDelaySeconds([])).toBeNull()
        expect(latestStartDelaySeconds([{startTime: '2026-08-11T10:00:00'}])).toBeNull()
    })

    // Der zuletzt GESTARTETE zählt — nicht der zuletzt geplante (dieselbe Regel wie
    // BoardLogic.currentDelaySeconds im Backend).
    test('der zuletzt gestartete Eintrag zählt', () => {
        const seconds = latestStartDelaySeconds([
            {startTime: '2026-08-11T10:00:00', startedAt: '2026-08-11T10:00:00'},
            {startTime: '2026-08-11T11:00:00', startedAt: '2026-08-11T11:18:00'},
            {startTime: '2026-08-11T12:00:00'},
        ])
        expect(seconds).toBe(18 * 60)
    })

    test('Verfrühung ist negativ', () => {
        expect(
            latestStartDelaySeconds([
                {startTime: '2026-08-11T10:00:00', startedAt: '2026-08-11T09:55:00'},
            ]),
        ).toBe(-300)
    })

    test('zuletzt gestartet ohne geplante Zeit: keine Aussage', () => {
        expect(
            latestStartDelaySeconds([
                {startTime: '2026-08-11T10:00:00', startedAt: '2026-08-11T10:00:00'},
                {startedAt: '2026-08-11T11:00:00'},
            ]),
        ).toBeNull()
    })
})

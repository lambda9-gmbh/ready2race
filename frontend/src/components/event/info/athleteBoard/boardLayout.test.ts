import {describe, expect, test} from 'vitest'
import {AthleteBoardDto, AthleteBoardMatch, AthleteBoardResult} from '@api/types.gen'
import {
    MAX_RUNNING_CARDS,
    MIN_DENSITY_SCALE,
    densityScale,
    maxBoats,
    selectBoardCards,
} from './boardLayout'

const match = (id: string, boats = 4): AthleteBoardMatch =>
    ({
        matchId: id,
        competitionName: 'CM 4x+',
        categoryName: null,
        roundName: 'Vorlauf',
        matchName: null,
        startTime: null,
        state: 'RUNNING',
        startState: 'UNSCHEDULED',
        teams: Array.from({length: boats}, (_, i) => ({
            startNumber: i + 1,
            participants: [],
            failed: false,
        })),
    }) as unknown as AthleteBoardMatch

const result = (id: string, boats = 4): AthleteBoardResult =>
    ({
        matchId: id,
        competitionName: 'CM 4x+',
        categoryName: null,
        roundName: 'Vorlauf',
        matchName: null,
        startTime: null,
        actualStartTime: null,
        teams: Array.from({length: boats}, (_, i) => ({
            place: i + 1,
            startNumber: i + 1,
            failed: false,
            deregistered: false,
        })),
    }) as unknown as AthleteBoardResult

const board = (partial: Partial<AthleteBoardDto>): AthleteBoardDto =>
    ({
        eventName: 'Förde-Regatta',
        serverTime: '2026-08-09T14:32:00',
        refreshIntervalSeconds: 15,
        showCountdown: true,
        running: [],
        upcoming: [],
        results: [],
        ...partial,
    }) as unknown as AthleteBoardDto

describe('selectBoardCards', () => {
    // Ein fest montierter Bildschirm soll seine Struktur nicht wechseln, nur weil gerade
    // nichts fährt: die drei Statusspalten stehen auch leer.
    test('ohne Daten stehen drei leere Statusspalten', () => {
        const {cards, hiddenRunning} = selectBoardCards(null)
        expect(cards.map(c => c.kind)).toEqual(['running', 'upcoming', 'result'])
        expect(cards.every(c => c.match === null && c.result === null)).toBe(true)
        expect(hiddenRunning).toBe(0)
    })

    test('ein Lauf je Status ergibt drei Spalten', () => {
        const {cards} = selectBoardCards(
            board({running: [match('r1')], upcoming: [match('u1')], results: [result('e1')]}),
        )
        expect(cards.map(c => c.kind)).toEqual(['running', 'upcoming', 'result'])
        expect(cards[0].match?.matchId).toBe('r1')
        expect(cards[1].match?.matchId).toBe('u1')
        expect(cards[2].result?.matchId).toBe('e1')
    })

    test('zwei Läufe in der Arena ergeben vier gleichwertige Spalten', () => {
        const {cards, hiddenRunning} = selectBoardCards(
            board({running: [match('r1'), match('r2')], upcoming: [match('u1')], results: [result('e1')]}),
        )
        expect(cards.map(c => c.kind)).toEqual(['running', 'running', 'upcoming', 'result'])
        expect(hiddenRunning).toBe(0)
    })

    // Ein verschwundener Lauf ist von einem Anzeigefehler nicht zu unterscheiden — deshalb
    // wird die Kappung gemeldet statt verschwiegen.
    test('mehr als zwei Läufe in der Arena werden gekappt und gemeldet', () => {
        const {cards, hiddenRunning} = selectBoardCards(
            board({running: [match('r1'), match('r2'), match('r3')]}),
        )
        expect(cards.filter(c => c.kind === 'running')).toHaveLength(MAX_RUNNING_CARDS)
        expect(cards.map(c => c.match?.matchId)).toEqual(['r1', 'r2', undefined, undefined])
        expect(hiddenRunning).toBe(1)
    })

    test('nur der nächste Lauf: die übrigen Spalten bleiben leer stehen', () => {
        const {cards} = selectBoardCards(board({upcoming: [match('u1')]}))
        expect(cards).toHaveLength(3)
        expect(cards[0].match).toBeNull()
        expect(cards[1].match?.matchId).toBe('u1')
        expect(cards[2].result).toBeNull()
    })

    test('nur der erste kommende Lauf und das erste Ergebnis kommen auf die Bühne', () => {
        const {cards} = selectBoardCards(
            board({upcoming: [match('u1'), match('u2')], results: [result('e1'), result('e2')]}),
        )
        expect(cards[1].match?.matchId).toBe('u1')
        expect(cards[2].result?.matchId).toBe('e1')
    })

    test('jede Spalte hat einen stabilen, eindeutigen Schlüssel', () => {
        const {cards} = selectBoardCards(
            board({running: [match('r1'), match('r2')], upcoming: [match('u1')], results: [result('e1')]}),
        )
        expect(new Set(cards.map(c => c.key)).size).toBe(cards.length)
    })
})

describe('maxBoats', () => {
    test('nimmt das vollste Boot-Feld über alle Spalten', () => {
        const {cards} = selectBoardCards(
            board({running: [match('r1', 3)], upcoming: [match('u1', 7)], results: [result('e1', 5)]}),
        )
        expect(maxBoats(cards)).toBe(7)
    })

    test('leere Bühne ergibt null Boote', () => {
        expect(maxBoats(selectBoardCards(null).cards)).toBe(0)
    })
})

describe('densityScale', () => {
    test('kleines Feld in drei Spalten bleibt in voller Größe', () => {
        expect(densityScale(4, 3)).toBe(1)
    })

    test('mehr Boote verkleinern nie stärker als die Untergrenze', () => {
        expect(densityScale(40, 4)).toBe(MIN_DENSITY_SCALE)
    })

    test('monoton fallend in der Bootszahl', () => {
        for (let boats = 1; boats < 20; boats++) {
            expect(densityScale(boats + 1, 3)).toBeLessThanOrEqual(densityScale(boats, 3))
        }
    })

    test('monoton fallend in der Spaltenzahl', () => {
        expect(densityScale(8, 4)).toBeLessThanOrEqual(densityScale(8, 3))
    })

    test('bleibt immer zwischen Unter- und Obergrenze', () => {
        for (let boats = 0; boats < 30; boats++) {
            for (let columns = 3; columns <= 4; columns++) {
                const scale = densityScale(boats, columns)
                expect(scale).toBeGreaterThanOrEqual(MIN_DENSITY_SCALE)
                expect(scale).toBeLessThanOrEqual(1)
            }
        }
    })
})

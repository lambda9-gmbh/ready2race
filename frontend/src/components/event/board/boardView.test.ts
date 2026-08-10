import {describe, expect, test} from 'vitest'
import {AthleteBoardMatch, BoardElement, BoardViewDto} from '@api/types.gen'
import {densityScale} from '../info/athleteBoard/boardLayout'
import {elementScale, gridForLayout, listForElement, slotForElement} from './boardView'

const match = (id: string, boats = 4): AthleteBoardMatch =>
    ({
        matchId: id,
        competitionName: 'CM 4x+',
        state: 'RUNNING',
        startState: 'UNSCHEDULED',
        teams: Array.from({length: boats}, (_, i) => ({
            startNumber: i + 1,
            participants: [],
            failed: false,
        })),
    }) as unknown as AthleteBoardMatch

const view = (partial: Partial<BoardViewDto>): BoardViewDto =>
    ({
        boardId: 'b1',
        eventName: 'Förde-Regatta',
        serverTime: '2026-08-10T14:32:00',
        refreshIntervalSeconds: 15,
        config: {layout: 'THREE_COLUMNS', refreshIntervalSeconds: 15, tiles: []},
        slots: [],
        lists: [],
        ...partial,
    }) as BoardViewDto

const matchElement = (offset: number, extra: Partial<BoardElement> = {}): BoardElement => ({
    type: 'MATCH',
    offset,
    ...extra,
})

describe('gridForLayout', () => {
    test('die vier Layouts ergeben ihr Raster', () => {
        expect(gridForLayout('ONE_COLUMN')).toEqual({columns: 1, rows: 1})
        expect(gridForLayout('TWO_COLUMNS')).toEqual({columns: 2, rows: 1})
        expect(gridForLayout('THREE_COLUMNS')).toEqual({columns: 3, rows: 1})
        expect(gridForLayout('SIX_TILES')).toEqual({columns: 3, rows: 2})
    })
})

describe('slotForElement', () => {
    const v = view({
        slots: [
            {offset: -1, match: null, result: null},
            {offset: 0, match: match('r1'), result: null},
        ],
    })

    test('findet den Slot über den Offset', () => {
        expect(slotForElement(v, matchElement(0))?.match?.matchId).toBe('r1')
    })

    test('ein leerer Slot ist ein Treffer, kein Fehlen', () => {
        const slot = slotForElement(v, matchElement(-1))
        expect(slot).not.toBeNull()
        expect(slot?.match).toBeNull()
    })

    // Konfiguration und Daten können aus verschiedenen Ständen stammen (Board eben
    // umkonfiguriert, Antwort noch aus dem Cache) — dann fehlt der Offset in der Antwort.
    test('ein nicht gelieferter Offset ergibt null', () => {
        expect(slotForElement(v, matchElement(3))).toBeNull()
    })

    test('andere Elementtypen haben keinen Slot', () => {
        expect(slotForElement(v, {type: 'CLOCK'})).toBeNull()
    })
})

describe('listForElement', () => {
    const v = view({
        lists: [
            {
                mode: 'UPCOMING',
                matches: [match('u1'), match('u2'), match('u3')],
                results: [],
            },
        ],
    })

    test('schneidet auf das Limit des Elements zu', () => {
        const list = listForElement(v, {type: 'MATCH_LIST', listMode: 'UPCOMING', limit: 2})
        expect(list?.matches.map(m => m.matchId)).toEqual(['u1', 'u2'])
    })

    test('ein nicht gelieferter Modus ergibt null', () => {
        expect(listForElement(v, {type: 'MATCH_LIST', listMode: 'RESULTS', limit: 2})).toBeNull()
    })

    test('andere Elementtypen haben keine Liste', () => {
        expect(listForElement(v, matchElement(0))).toBeNull()
    })
})

describe('elementScale', () => {
    const content = {match: match('r1', 8), result: null}

    test('verdrahtet den Kachel-Inhalt mit der Dichteformel', () => {
        expect(elementScale(matchElement(0), content, 3)).toBe(densityScale(8, 3))
    })

    // autoFit aus heißt: volle Größe, die Kachel scrollt statt zu schrumpfen.
    test('autoFit=false erzwingt volle Größe', () => {
        expect(elementScale(matchElement(0, {autoFit: false}), content, 3)).toBe(1)
    })

    test('leerer Inhalt skaliert wie eine leere Kachel', () => {
        expect(elementScale(matchElement(0), null, 3)).toBe(densityScale(0, 3))
    })
})

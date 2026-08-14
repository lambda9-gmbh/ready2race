import {describe, expect, test} from 'vitest'
import {AthleteBoardMatch, BoardElement, BoardTile, BoardViewDto} from '@api/types.gen'
import {densityScale} from '../info/athleteBoard/boardLayout'
import {boardColumns, ceremonyForElement, elementScale, gridPlacement, hasMatchDetail, listForElement, programForElement, rowSizes, slotForElement, tileColor} from './boardView'

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
        config: {columns: 3, refreshIntervalSeconds: 15, tiles: []},
        slots: [],
        lists: [],
        ...partial,
    }) as BoardViewDto

const matchElement = (offset: number, extra: Partial<BoardElement> = {}): BoardElement => ({
    type: 'MATCH',
    offset,
    ...extra,
})

const tile = (colSpan = 1, rowSpan = 1): BoardTile => ({
    colSpan,
    rowSpan,
    elements: [matchElement(0)],
})

describe('gridPlacement', () => {
    test('einfache Kacheln füllen zeilenweise auf', () => {
        const {rows, positions} = gridPlacement([tile(), tile(), tile(), tile()], 3)
        expect(rows).toBe(2)
        expect(positions.map(p => [p.column, p.row])).toEqual([
            [1, 1],
            [2, 1],
            [3, 1],
            [1, 2],
        ])
    })

    // Der Kern des freien Rasters: eine große Hauptkachel, kleine fließen drumherum.
    test('eine 2×2-Hauptkachel lässt die kleinen rechts daneben fließen', () => {
        const {rows, positions} = gridPlacement([tile(2, 2), tile(), tile(), tile()], 3)
        expect(positions.map(p => [p.column, p.row])).toEqual([
            [1, 1],
            [3, 1],
            [3, 2],
            // Der Cursor läuft nur vorwärts (wie CSS-Auto-Placement ohne dense):
            // die vierte Kachel beginnt eine neue Zeile, statt zurückzuspringen.
            [1, 3],
        ])
        expect(rows).toBe(3)
    })

    test('eine zu breite Kachel rückt in die nächste Zeile', () => {
        const {positions} = gridPlacement([tile(), tile(2)], 2)
        expect(positions[1]).toMatchObject({column: 1, row: 2})
    })

    test('colSpan wird auf die Spaltenzahl gekappt', () => {
        const {positions, rows} = gridPlacement([tile(4)], 2)
        expect(positions[0]).toMatchObject({column: 1, row: 1, colSpan: 2})
        expect(rows).toBe(1)
    })

    test('volle Breite über mehrere Zeilen', () => {
        const {rows} = gridPlacement([tile(3, 1), tile(3, 2)], 3)
        expect(rows).toBe(3)
    })

    // Der Editor erlaubt bis zu 4 Spalten (MAX_COLUMNS); mit dem immer aktiven Raster
    // muss auch das volle 4×4 sauber platziert werden — 16 Kacheln, keine Lücke.
    test('ein volles 4×4-Raster platziert alle 16 Kacheln lückenlos', () => {
        const tiles = Array.from({length: 16}, () => tile())
        const {rows, positions} = gridPlacement(tiles, 4)
        expect(rows).toBe(4)
        expect(positions.map(p => [p.column, p.row])).toEqual(
            Array.from({length: 16}, (_, i) => [(i % 4) + 1, Math.floor(i / 4) + 1]),
        )
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

    // Die Sprecher-Kachel wählt über denselben Offset wie MATCH.
    test('die Sprecher-Kachel greift denselben Slot wie MATCH', () => {
        expect(slotForElement(v, {type: 'MATCH_DETAIL', offset: 0})?.match?.matchId).toBe('r1')
    })
})

// Der Editor sperrt „Kachel hinzufügen", solange eine Sprecher-Kachel existiert —
// die Vollbild-Regel des Backends soll in der Maske gar nicht erst verletzbar sein.
describe('hasMatchDetail', () => {
    test('findet die Sprecher-Kachel auch als Rotations-Element', () => {
        expect(
            hasMatchDetail([
                {elements: [matchElement(0), {type: 'MATCH_DETAIL', offset: 0}]},
            ]),
        ).toBe(true)
    })

    test('ohne Sprecher-Kachel bleibt alles erlaubt', () => {
        expect(hasMatchDetail([tile(), tile()])).toBe(false)
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

describe('ceremonyForElement', () => {
    const ceremony = {
        competitionId: 'c1',
        ratingCategoryId: null,
        competitionIdentifier: '8',
        competitionShortName: 'CMix 4x+',
        competitionName: 'Mixed-Doppelvierer',
        ratingCategoryName: null,
        ranks: [],
    }
    const v = view({ceremonies: [ceremony]} as never)

    test('findet die Ehrung über Wettkampf und Wertung', () => {
        expect(
            ceremonyForElement(v, {type: 'AWARD_CEREMONY', competitionId: 'c1'}),
        ).toMatchObject({competitionId: 'c1'})
    })

    test('eine fremde Wertung trifft nichts', () => {
        expect(
            ceremonyForElement(v, {type: 'AWARD_CEREMONY', competitionId: 'c1', ratingCategoryId: 'r9'}),
        ).toBeNull()
    })
})

describe('programForElement', () => {
    const entry = (state: string, time: string) => ({startTime: time, state}) as never
    const v = view({
        lists: [
            {
                mode: 'SCHEDULE',
                matches: [],
                results: [],
                program: [
                    entry('FINISHED', '08:00'),
                    entry('FINISHED', '08:30'),
                    entry('FINISHED', '09:00'),
                    entry('RUNNING', '09:30'),
                    entry('UPCOMING', '10:00'),
                    entry('UPCOMING', '10:30'),
                ],
            } as never,
        ],
    })

    // Zentriert um „jetzt": zwei beendete als Kontext, dann Laufendes und Anstehendes.
    test('schneidet um den laufenden Lauf herum zu', () => {
        const program = programForElement(v, {type: 'MATCH_LIST', listMode: 'SCHEDULE', limit: 4})
        expect(program?.map(e => e.startTime)).toEqual(['08:30', '09:00', '09:30', '10:00'])
    })

    // Alles beendet: die letzten Zeilen bleiben stehen, statt den Morgen zu zeigen.
    test('nach dem letzten Lauf zeigt es das Ende des Tages', () => {
        const done = view({
            lists: [
                {
                    mode: 'SCHEDULE',
                    matches: [],
                    results: [],
                    program: [entry('FINISHED', '08:00'), entry('FINISHED', '09:00'), entry('FINISHED', '10:00')],
                } as never,
            ],
        })
        const program = programForElement(done, {type: 'MATCH_LIST', listMode: 'SCHEDULE', limit: 2})
        expect(program?.map(e => e.startTime)).toEqual(['09:00', '10:00'])
    })

    test('andere Modi haben kein Programm', () => {
        expect(programForElement(v, {type: 'MATCH_LIST', listMode: 'UPCOMING', limit: 4})).toBeNull()
    })

    // FOLLOW explizit gesetzt verhält sich wie das Alt-Verhalten ohne Feld.
    test('scheduleMode FOLLOW schneidet wie ohne Feld zu', () => {
        const program = programForElement(v, {
            type: 'MATCH_LIST',
            listMode: 'SCHEDULE',
            scheduleMode: 'FOLLOW',
            limit: 4,
        })
        expect(program?.map(e => e.startTime)).toEqual(['08:30', '09:00', '09:30', '10:00'])
    })

    // FULL: ganzer Tag ohne Zuschnitt — auch das Limit greift nicht, die Kachel scrollt.
    test('scheduleMode FULL liefert den ganzen Tag und ignoriert das Limit', () => {
        const program = programForElement(v, {
            type: 'MATCH_LIST',
            listMode: 'SCHEDULE',
            scheduleMode: 'FULL',
            limit: 2,
        })
        expect(program?.map(e => e.startTime)).toEqual([
            '08:00',
            '08:30',
            '09:00',
            '09:30',
            '10:00',
            '10:30',
        ])
    })
})

describe('elementScale', () => {
    const content = {match: match('r1', 8), result: null}

    test('verdrahtet den Kachel-Inhalt mit der Dichteformel', () => {
        expect(elementScale(matchElement(0), content, 3, 1)).toBe(densityScale(8, 3))
    })

    // Eine breite Kachel verhält sich wie weniger Spalten: 2 von 3 Spalten ≙ 1,5.
    test('breite Kacheln rechnen mit ihrem Breitenanteil', () => {
        expect(elementScale(matchElement(0), content, 1.5, 1)).toBe(densityScale(8, 1.5))
    })

    // Halbe Höhe (6-Kachel-Raster): eine Stufe kleiner, sonst überlappen die Zeilen.
    test('halbhohe Kacheln schrumpfen zusätzlich', () => {
        expect(elementScale(matchElement(0), content, 3, 0.5)).toBeLessThan(
            elementScale(matchElement(0), content, 3, 1),
        )
    })

    // autoFit aus heißt: volle Größe, die Kachel scrollt statt zu schrumpfen.
    test('autoFit=false erzwingt volle Größe', () => {
        expect(elementScale(matchElement(0, {autoFit: false}), content, 3, 0.5)).toBe(1)
    })

    test('leerer Inhalt skaliert wie eine leere Kachel', () => {
        expect(elementScale(matchElement(0), null, 3, 1)).toBe(densityScale(0, 3))
    })
})

describe('tileColor', () => {
    // Beide Hex-Formen der Konfiguration gehen unverändert durch — für Fläche und Rand.
    test('lässt gültiges Hex in beiden Formen durch', () => {
        expect(tileColor('#C62828')).toBe('#C62828')
        expect(tileColor('#0a0')).toBe('#0a0')
    })

    // Ohne (gültige) Farbe bleibt das bisherige Aussehen — undefined statt Farbwert.
    test('fehlende oder ungültige Farbe ergibt undefined', () => {
        expect(tileColor(undefined)).toBeUndefined()
        expect(tileColor(null)).toBeUndefined()
        expect(tileColor('rot')).toBeUndefined()
        expect(tileColor('C62828')).toBeUndefined()
        expect(tileColor('#C6282')).toBeUndefined()
        expect(tileColor('#GGHHII')).toBeUndefined()
    })
})

describe('rowSizes', () => {
    const tileOf = (types: BoardElement['type'][], colSpan = 1, rowSpan = 1): BoardTile => ({
        colSpan,
        rowSpan,
        elements: types.map(type => ({type}) as BoardElement),
    })

    const sizesFor = (tiles: BoardTile[], columns: number) => {
        const {rows, positions} = gridPlacement(tiles, columns)
        return rowSizes(tiles, positions, rows)
    }

    // Der Anlass: eine Zeile, in der nur eine Uhr (oder die Verspätung) liegt,
    // verschwendete als 1fr ein Drittel des Bildschirms.
    test('eine reine Uhr/Verspätungs-Zeile wird kompakt', () => {
        const sizes = sizesFor(
            [tileOf(['MATCH'], 2), tileOf(['CLOCK']), tileOf(['DELAY'])],
            2,
        )
        expect(sizes).toEqual(['1fr', 'auto'])
    })

    test('eine gemischte Zeile bleibt 1fr', () => {
        const sizes = sizesFor([tileOf(['CLOCK']), tileOf(['MATCH'])], 2)
        expect(sizes).toEqual(['1fr'])
    })

    // Rotation Uhr+Lauf in EINER Kachel: die Kachel ist Inhalt, nicht kompakt.
    test('eine rotierende Kachel mit Lauf zählt als Inhalt', () => {
        const sizes = sizesFor([tileOf(['CLOCK', 'MATCH'])], 1)
        expect(sizes).toEqual(['1fr'])
    })

    // Eine Inhalts-Kachel, die per rowSpan auch die Uhr-Zeile überspannt, braucht
    // ihre Höhe über die ganze Spannweite — beide Zeilen bleiben 1fr.
    test('rowSpan über Inhalts- und Kompakt-Zeile macht beide 1fr', () => {
        const sizes = sizesFor(
            [tileOf(['MATCH'], 1, 2), tileOf(['CLOCK']), tileOf(['CLOCK'])],
            2,
        )
        expect(sizes).toEqual(['1fr', '1fr'])
    })

    // Sonderfall Board nur aus Kompakt-Kacheln: alle Zeilen 'auto' — der Rest der
    // Bildschirmhöhe bleibt leer, statt dass sich eine Uhr auf 100% aufbläst.
    test('ohne Inhalts-Kacheln sind alle Zeilen auto', () => {
        const sizes = sizesFor([tileOf(['CLOCK']), tileOf(['DELAY'])], 1)
        expect(sizes).toEqual(['auto', 'auto'])
    })
})

describe('boardColumns', () => {
    const detailTile: BoardTile = {
        colSpan: 1,
        rowSpan: 1,
        elements: [{type: 'MATCH_DETAIL', offset: 0} as BoardElement],
    }

    const streamTile: BoardTile = {
        colSpan: 1,
        rowSpan: 1,
        elements: [{type: 'STREAM'} as BoardElement],
    }

    // Der Nutzer-Befund vom 12.08.2026: 3 Spalten + Sprecher-Kachel quetschte die
    // Kachel in eine Spalte. Das Rendering ignoriert die Geometrie und heilt damit
    // auch gespeicherte Fehlkonfigurationen — ohne neuen Validierungsfehler.
    test('ein Sprecher-Board rendert immer einspaltig', () => {
        expect(boardColumns({columns: 3, tiles: [detailTile]})).toBe(1)
        expect(boardColumns({columns: 1, tiles: [detailTile]})).toBe(1)
    })

    test('ein Stream-Board rendert immer einspaltig', () => {
        expect(boardColumns({columns: 3, tiles: [streamTile]})).toBe(1)
    })

    test('normale Boards behalten ihre Spaltenwahl', () => {
        expect(boardColumns({columns: 4, tiles: [tile(), tile()]})).toBe(4)
        // Ohne Spaltenwahl gilt der alte Default.
        expect(boardColumns({tiles: [tile()]})).toBe(3)
    })

    // Die Heilung gilt nur für das Vollbild-Board (einzige Kachel) — ein hypothetischer
    // Altbestand mit Nachbarkacheln bleibt beim konfigurierten Raster.
    test('mit Nachbarkacheln greift die Heilung nicht', () => {
        expect(boardColumns({columns: 3, tiles: [detailTile, tile()]})).toBe(3)
    })
})

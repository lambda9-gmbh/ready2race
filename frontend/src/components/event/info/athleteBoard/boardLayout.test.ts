import {describe, expect, test} from 'vitest'
import {AthleteBoardDto, AthleteBoardMatch, AthleteBoardResult} from '@api/types.gen'
import {
    MAX_DENSITY_SCALE,
    MAX_RUNNING_CARDS,
    MIN_DENSITY_SCALE,
    boardScale,
    densityScale,
    longestTeamLabel,
    maxBoats,
    rowTextLines,
    selectBoardCards,
} from './boardLayout'

const match = (
    id: string,
    boats = 4,
    extra: Partial<AthleteBoardMatch> = {},
    clubsFull: string | null = null,
): AthleteBoardMatch =>
    ({
        matchId: id,
        competitionName: 'CM 4x+',
        categoryName: null,
        roundName: 'Vorlauf',
        matchName: null,
        startTime: null,
        state: 'RUNNING',
        startState: 'UNSCHEDULED',
        pendingRound: false,
        name: null,
        cancelled: false,
        teams: Array.from({length: boats}, (_, i) => ({
            startNumber: i + 1,
            participants: [],
            failed: false,
            clubsFull,
        })),
        ...extra,
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

    // "Ein Lauf verschwindet nie" ist das Kernversprechen der Bühne, und der abgesagte Lauf ist
    // der Fall, der einem Verschwinden am ähnlichsten sieht — er bleibt an seiner Stelle stehen.
    test('ein abgesagter Lauf bleibt auf der Bühne', () => {
        const cancelled = match('u1', 0, {cancelled: true, teams: []})
        const {cards} = selectBoardCards(board({upcoming: [cancelled]}))
        expect(cards[1].match?.matchId).toBe('u1')
        expect(cards[1].match?.cancelled).toBe(true)
    })

    // Ein Lauf, der gerade erst gestartet ist, kann zwischen zwei Backend-Abfragen kurz sowohl
    // in `running` als auch noch in `upcoming` stehen — ohne Präfix im Schlüssel bekämen zwei
    // nebeneinanderstehende Spalten denselben React-Schlüssel.
    test('Schlüssel bleiben eindeutig, wenn derselbe Lauf in zwei Blöcken steht', () => {
        const {cards} = selectBoardCards(
            board({running: [match('r1')], upcoming: [match('r1')]}),
        )
        expect(new Set(cards.map(c => c.key)).size).toBe(cards.length)
    })

    // Bewusste Asymmetrie zum laufenden Block: dort wird eine Kappung über hiddenRunning
    // gemeldet, hier nicht — die Bühne zeigt ohnehin immer nur den je ersten Eintrag. Ein
    // künftiger Leser soll das nicht versehentlich als fehlende Meldung "reparieren".
    test('über den ersten hinaus werden kommende Läufe und Ergebnisse ohne Meldung verworfen', () => {
        const {cards, hiddenRunning} = selectBoardCards(
            board({upcoming: [match('u1'), match('u2')], results: [result('e1'), result('e2')]}),
        )
        expect(cards[1].match?.matchId).toBe('u1')
        expect(cards[2].result?.matchId).toBe('e1')
        expect(hiddenRunning).toBe(0)
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

    // Das ist der Grund für MAX_DENSITY_SCALE: ein Rennen mit zwei Booten soll die freie Fläche
    // der Zeile nutzen dürfen, statt in der für ein volles Feld bemessenen Schrift zu ertrinken.
    test('kleines Feld in drei Spalten wächst über volle Größe hinaus', () => {
        expect(densityScale(2, 3)).toBeGreaterThan(1)
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
                expect(scale).toBeLessThanOrEqual(MAX_DENSITY_SCALE)
            }
        }
    })

    // Leere Bühne ist der häufigste Zustand über einen ganzen Regattatag — der konkrete Wert
    // hält fest, wie groß die drei leeren Statusspalten dabei tatsächlich stehen.
    test('leere Bühne', () => {
        expect(densityScale(0, 3)).toBe(1.24)
    })

    test('eine dritte Textzeile verkleinert zusätzlich', () => {
        expect(densityScale(6, 3, 3)).toBeLessThan(densityScale(6, 3, 2))
    })

    // Eine kurze Kette spart keine Höhe, sie lässt nur Luft — der Faktor darf davon nicht steigen.
    test('weniger als zwei Textzeilen vergrössern nicht', () => {
        expect(densityScale(6, 3, 1)).toBe(densityScale(6, 3, 2))
    })

    test('die Untergrenze gilt auch mit dritter Zeile', () => {
        expect(densityScale(40, 4, 3)).toBe(MIN_DENSITY_SCALE)
    })
})

describe('boardScale', () => {
    // Die einzige Stelle, an der maxBoats und densityScale falsch verdrahtet werden könnten —
    // deshalb eigens getestet statt nur implizit über die Ansicht.
    test('verdrahtet die Kartenauswahl mit dem Dichtefaktor', () => {
        const layout = selectBoardCards(
            board({running: [match('r1', 3)], upcoming: [match('u1', 7)], results: [result('e1', 5)]}),
        )
        expect(boardScale(layout)).toBe(densityScale(maxBoats(layout.cards), layout.cards.length))
        expect(boardScale(layout)).toBe(densityScale(7, 3))
    })

    test('leere Bühne ergibt densityScale(0, 3)', () => {
        expect(boardScale(selectBoardCards(null))).toBe(densityScale(0, 3))
    })

    // Der eigentliche Zweck der Zeilenzählung: eine umbrechende Vereinskette macht jede
    // Bootszeile höher, also muss die Schrift eine Stufe kleiner werden.
    test('lange Vereinsketten drücken den Faktor unter den kurzer Ketten', () => {
        const kurz = selectBoardCards(board({running: [match('r1', 6, {}, 'RV Kiel')]}))
        const lang = selectBoardCards(
            board({
                running: [
                    match(
                        'r1',
                        6,
                        {},
                        'Rudergemeinschaft Flensburg-Glücksburg-Eckernförde-Kappeln von 1893 e.V.',
                    ),
                ],
            }),
        )
        expect(boardScale(lang)).toBeLessThan(boardScale(kurz))
    })
})

describe('longestTeamLabel', () => {
    test('nimmt die längste Kette über alle Spalten', () => {
        const {cards} = selectBoardCards(
            board({
                running: [match('r1', 2, {}, 'RV Kiel')],
                upcoming: [match('u1', 2, {}, 'Rudergemeinschaft Schlei-Ostsee e.V.')],
            }),
        )
        expect(longestTeamLabel(cards)).toBe('Rudergemeinschaft Schlei-Ostsee e.V.'.length)
    })

    test('ohne Vereinsangabe null Zeichen', () => {
        expect(longestTeamLabel(selectBoardCards(null).cards)).toBe(0)
    })
})

describe('rowTextLines', () => {
    // Zwei Zeilen im Normalfall: Vereinskette plus die kleine Zeile darunter.
    test('kurze Kette bleibt bei zwei Zeilen', () => {
        expect(rowTextLines(10, 3)).toBe(2)
    })

    test('lange Kette bringt eine dritte Zeile', () => {
        expect(rowTextLines(120, 3)).toBe(3)
    })

    // Vier Spalten sind schmaler, dieselbe Kette bricht dort früher um.
    test('bei vier Spalten bricht dieselbe Kette früher um', () => {
        const kette = 30
        expect(rowTextLines(kette, 3)).toBe(2)
        expect(rowTextLines(kette, 4)).toBe(3)
    })

    test('mehr als drei Zeilen kann es nicht geben', () => {
        expect(rowTextLines(10_000, 4)).toBe(3)
    })
})

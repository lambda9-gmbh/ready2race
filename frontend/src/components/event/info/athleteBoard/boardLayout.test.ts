import {describe, expect, test} from 'vitest'
import {AthleteBoardMatch, AthleteBoardResult} from '@api/types.gen'
import {
    BoardContent,
    MAX_DENSITY_SCALE,
    MIN_DENSITY_SCALE,
    contentScale,
    densityScale,
    longestTeamLabel,
    maxBoats,
    rowTextLines,
} from './boardLayout'

const match = (id: string, boats = 4, clubsFull: string | null = null): AthleteBoardMatch =>
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

const matchContent = (id: string, boats = 4, clubsFull: string | null = null): BoardContent => ({
    match: match(id, boats, clubsFull),
    result: null,
})

const resultContent = (id: string, boats = 4): BoardContent => ({
    match: null,
    result: result(id, boats),
})

const empty: BoardContent = {match: null, result: null}

describe('maxBoats', () => {
    test('nimmt das vollste Boot-Feld über alle Inhalte', () => {
        expect(
            maxBoats([matchContent('r1', 3), matchContent('u1', 7), resultContent('e1', 5)]),
        ).toBe(7)
    })

    test('leere Inhalte ergeben null Boote', () => {
        expect(maxBoats([empty, empty])).toBe(0)
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

    // Ein-/zweispaltige Boards sind neu seit dem Board-Umbau: unterhalb von drei Spalten
    // wird nicht weiter vergrößert, es gilt derselbe Faktor wie bei dreien.
    test('weniger als drei Spalten vergrößern nicht über den Dreispalter hinaus', () => {
        expect(densityScale(8, 1)).toBe(densityScale(8, 3))
    })

    test('bleibt immer zwischen Unter- und Obergrenze', () => {
        for (let boats = 0; boats < 30; boats++) {
            for (let columns = 1; columns <= 4; columns++) {
                const scale = densityScale(boats, columns)
                expect(scale).toBeGreaterThanOrEqual(MIN_DENSITY_SCALE)
                expect(scale).toBeLessThanOrEqual(MAX_DENSITY_SCALE)
            }
        }
    })

    // Leere Kacheln sind der häufigste Zustand über einen ganzen Regattatag — der konkrete Wert
    // hält fest, wie groß eine leere Kachel dabei tatsächlich steht.
    test('leere Kachel bei drei Spalten', () => {
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

describe('contentScale', () => {
    // Die einzige Stelle, an der maxBoats und densityScale falsch verdrahtet werden könnten —
    // deshalb eigens getestet statt nur implizit über die Ansicht.
    test('verdrahtet den Inhalt mit dem Dichtefaktor', () => {
        const contents = [matchContent('r1', 7)]
        expect(contentScale(contents, 3)).toBe(densityScale(7, 3))
    })

    test('leere Kachel ergibt densityScale(0, columns)', () => {
        expect(contentScale([empty], 3)).toBe(densityScale(0, 3))
    })

    // Der eigentliche Zweck der Zeilenzählung: eine umbrechende Vereinskette macht jede
    // Bootszeile höher, also muss die Schrift eine Stufe kleiner werden.
    test('lange Vereinsketten drücken den Faktor unter den kurzer Ketten', () => {
        const kurz = [matchContent('r1', 6, 'RV Kiel')]
        const lang = [
            matchContent(
                'r1',
                6,
                'Rudergemeinschaft Flensburg-Glücksburg-Eckernförde-Kappeln von 1893 e.V.',
            ),
        ]
        expect(contentScale(lang, 3)).toBeLessThan(contentScale(kurz, 3))
    })
})

describe('longestTeamLabel', () => {
    test('nimmt die längste Kette über alle Inhalte', () => {
        expect(
            longestTeamLabel([
                matchContent('r1', 2, 'RV Kiel'),
                matchContent('u1', 2, 'Rudergemeinschaft Schlei-Ostsee e.V.'),
            ]),
        ).toBe('Rudergemeinschaft Schlei-Ostsee e.V.'.length)
    })

    test('ohne Vereinsangabe null Zeichen', () => {
        expect(longestTeamLabel([empty])).toBe(0)
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

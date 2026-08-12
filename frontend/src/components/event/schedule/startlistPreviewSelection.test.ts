import {describe, expect, test} from 'vitest'
import {
    initialSelection,
    matchIdsParam,
    toggleAll,
    toggleMatch,
} from './startlistPreviewSelection'

const rows = [
    {matchId: 'a', startTime: '2026-08-14T10:00:00'},
    {matchId: 'b', startTime: '2026-08-14T10:10:00'},
    // Ohne Startzeit: nicht exportierbar, nie Teil der Auswahl.
    {matchId: 'c', startTime: null},
]

describe('initialSelection', () => {
    test('wählt alles Exportierbare vor, Läufe ohne Startzeit nie', () => {
        expect([...initialSelection(rows)].sort()).toEqual(['a', 'b'])
    })
})

describe('toggleMatch', () => {
    test('nimmt einzelne Läufe aus der Auswahl und wieder hinein', () => {
        const partial = toggleMatch(initialSelection(rows), 'b')
        expect([...partial]).toEqual(['a'])
        expect([...toggleMatch(partial, 'b')].sort()).toEqual(['a', 'b'])
    })
})

describe('toggleAll', () => {
    test('alle → keine → alle (nur Exportierbare)', () => {
        const none = toggleAll(initialSelection(rows), rows)
        expect(none.size).toBe(0)
        expect([...toggleAll(none, rows)].sort()).toEqual(['a', 'b'])
        // teilweise gewählt: der Klick vervollständigt, statt zu leeren
        expect([...toggleAll(new Set(['a']), rows)].sort()).toEqual(['a', 'b'])
    })
})

describe('matchIdsParam', () => {
    test('alles gewählt und alles exportierbar: Parameter entfällt', () => {
        const allExportable = rows.slice(0, 2)
        expect(matchIdsParam(new Set(['a', 'b']), allExportable)).toBeUndefined()
    })

    test('Abwahl schickt die verbliebene Auswahl mit', () => {
        expect(matchIdsParam(new Set(['a']), rows.slice(0, 2))).toEqual(['a'])
    })

    test('ein Lauf ohne Startzeit erzwingt den Parameter - sonst bliebe der Export blockiert', () => {
        expect(matchIdsParam(new Set(['a', 'b']), rows)?.sort()).toEqual(['a', 'b'])
    })

    test('leere Auswahl: leere Liste, kein undefined - nichts exportieren heißt nichts', () => {
        expect(matchIdsParam(new Set(), rows)).toEqual([])
    })
})

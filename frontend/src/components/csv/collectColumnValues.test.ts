import {describe, expect, test} from 'vitest'
import {collectColumnValues, DISTINCT_VALUE_LIMIT} from './utils'

describe('collectColumnValues', () => {
    const headers = ['Vorname', 'Startberechtigt']

    test('zählt je Spalte und sortiert nach Häufigkeit', () => {
        const rows = [
            ['Nele', 'ja'],
            ['Rosa', 'erweitert'],
            ['Jan', 'ja'],
            ['Lars', 'ja'],
        ]

        // Der DRV-Fall: die Bedingungsspalte führt genau zwei Werte, beide bedeuten
        // startberechtigt. Die Anzahl daneben macht sichtbar, dass nichts übersehen wird.
        expect(collectColumnValues(headers, rows)['Startberechtigt']).toEqual([
            {value: 'ja', count: 3},
            {value: 'erweitert', count: 1},
        ])
    })

    test('sortiert gleich häufige Werte alphabetisch', () => {
        const rows = [
            ['Nele', 'ja'],
            ['Rosa', 'erweitert'],
        ]

        expect(collectColumnValues(headers, rows)['Startberechtigt']).toEqual([
            {value: 'erweitert', count: 1},
            {value: 'ja', count: 1},
        ])
    })

    test('ignoriert Leerzeichen am Rand und leere Zellen', () => {
        const rows = [
            ['Nele', ' ja '],
            ['Rosa', 'ja'],
            ['Jan', ''],
            ['Lars', '   '],
        ]

        expect(collectColumnValues(headers, rows)['Startberechtigt']).toEqual([
            {value: 'ja', count: 2},
        ])
    })

    test('lässt Spalten mit zu vielen verschiedenen Werten ganz weg', () => {
        // Namensspalten sollen gar nicht erst als Auswahlliste angeboten werden - eine
        // gekürzte Liste wäre schlimmer als keine, weil man aus etwas Unvollständigem wählt.
        const rows = Array.from({length: DISTINCT_VALUE_LIMIT + 5}, (_, i) => [
            `Person ${i}`,
            'ja',
        ])

        const result = collectColumnValues(headers, rows)
        expect(result['Vorname']).toBeUndefined()
        expect(result['Startberechtigt']).toEqual([
            {value: 'ja', count: DISTINCT_VALUE_LIMIT + 5},
        ])
    })

    test('behält eine Spalte, die den Deckel genau erreicht', () => {
        const rows = Array.from({length: DISTINCT_VALUE_LIMIT}, (_, i) => [`Person ${i}`, 'ja'])

        expect(collectColumnValues(headers, rows)['Vorname']).toHaveLength(DISTINCT_VALUE_LIMIT)
    })

    test('kommt mit fehlenden Zellen am Zeilenende zurecht', () => {
        const rows = [['Nele'], ['Rosa', 'ja']]

        const result = collectColumnValues(headers, rows)
        expect(result['Startberechtigt']).toEqual([{value: 'ja', count: 1}])
        expect(result['Vorname']).toEqual([
            {value: 'Nele', count: 1},
            {value: 'Rosa', count: 1},
        ])
    })

    test('liefert für eine leere Datei nichts', () => {
        expect(collectColumnValues(headers, [])).toEqual({})
    })
})

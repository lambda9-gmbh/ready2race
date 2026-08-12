import {describe, expect, test} from 'vitest'
import {formatPlaceOrdinal, placeOrdinalParts} from './placeOrdinal'

describe('formatPlaceOrdinal', () => {
    test('die drei Sonder-Suffixe', () => {
        expect(formatPlaceOrdinal(1)).toBe('1st')
        expect(formatPlaceOrdinal(2)).toBe('2nd')
        expect(formatPlaceOrdinal(3)).toBe('3rd')
        expect(formatPlaceOrdinal(4)).toBe('4th')
    })

    // Die englischen Ausnahmen: 11–13 enden immer auf „th".
    test('11 bis 13 sind immer th', () => {
        expect(formatPlaceOrdinal(11)).toBe('11th')
        expect(formatPlaceOrdinal(12)).toBe('12th')
        expect(formatPlaceOrdinal(13)).toBe('13th')
    })

    test('ab 20 zählen die Endziffern wieder', () => {
        expect(formatPlaceOrdinal(21)).toBe('21st')
        expect(formatPlaceOrdinal(22)).toBe('22nd')
        expect(formatPlaceOrdinal(23)).toBe('23rd')
        expect(formatPlaceOrdinal(111)).toBe('111th')
    })
})

describe('placeOrdinalParts', () => {
    // Dieselbe Suffix-Logik wie formatPlaceOrdinal, nur getrennt — für das
    // hochgestellte Suffix in der großen Platz-Typografie.
    test('trennt Ziffer und Suffix', () => {
        expect(placeOrdinalParts(1)).toEqual({number: '1', suffix: 'st'})
        expect(placeOrdinalParts(2)).toEqual({number: '2', suffix: 'nd'})
        expect(placeOrdinalParts(3)).toEqual({number: '3', suffix: 'rd'})
        expect(placeOrdinalParts(12)).toEqual({number: '12', suffix: 'th'})
        expect(placeOrdinalParts(21)).toEqual({number: '21', suffix: 'st'})
    })

    test('bleibt deckungsgleich mit dem Text-Formatter', () => {
        for (const place of [1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 111]) {
            const parts = placeOrdinalParts(place)
            expect(`${parts.number}${parts.suffix}`).toBe(formatPlaceOrdinal(place))
        }
    })
})

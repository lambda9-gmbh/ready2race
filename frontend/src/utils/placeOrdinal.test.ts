import {describe, expect, test} from 'vitest'
import {formatPlaceOrdinal} from './placeOrdinal'

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

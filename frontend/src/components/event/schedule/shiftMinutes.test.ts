import {describe, expect, test} from 'vitest'
import {parseShiftMinutes} from './shiftMinutes'

describe('parseShiftMinutes', () => {
    test('Eingabezustand: „-" allein ist tippbarer Zwischenzustand, geparst erst als Ganzzahl', () => {
        // Der Bug: `Number('-')` je Tastendruck ergab NaN, das Minus verschwand aus dem Feld.
        // Als Text bleibt der Zwischenzustand stehen und wird erst beim Absenden geparst -
        // dann ist „-" (noch) keine Zahl, „-15" aber sehr wohl eine.
        expect(parseShiftMinutes('-')).toBeNull()
        expect(parseShiftMinutes('-15')).toBe(-15)
        expect(parseShiftMinutes('15')).toBe(15)
        expect(parseShiftMinutes(' -5 ')).toBe(-5)
        expect(parseShiftMinutes('')).toBeNull()
        expect(parseShiftMinutes('1,5')).toBeNull()
    })
})

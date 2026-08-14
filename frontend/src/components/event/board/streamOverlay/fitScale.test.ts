import {describe, expect, it} from 'vitest'
import {fitScale, steadyScale} from './fitScale.ts'

/**
 * `fitScale` hält die Stream-Panels innerhalb ihrer Höhengrenze, ohne die letzte
 * Bootszeile abzuschneiden (siehe FitToHeight.tsx).
 */
describe('fitScale', () => {
    it('lässt passenden Inhalt in Originalgröße', () => {
        expect(fitScale(500, 400)).toBe(1)
    })

    it('lässt genau passenden Inhalt in Originalgröße', () => {
        expect(fitScale(400, 400)).toBe(1)
    })

    it('verkleinert zu hohen Inhalt auf das Verhältnis Platz/Bedarf', () => {
        expect(fitScale(300, 600)).toBe(0.5)
    })

    it('verkleinert ein Achterfeld im Ergebnispanel messbar (586 px Platz, 672 px Bedarf)', () => {
        expect(fitScale(586, 672)).toBeCloseTo(0.872, 3)
    })

    it('rundet auf drei Stellen, damit Bruchteile eines Pixels kein Zittern auslösen', () => {
        expect(fitScale(1000, 3000)).toBe(0.333)
    })

    it('bleibt bei 1, solange noch nichts gemessen ist', () => {
        expect(fitScale(0, 0)).toBe(1)
        expect(fitScale(500, 0)).toBe(1)
        expect(fitScale(0, 500)).toBe(1)
    })

    it('ignoriert unsinnige negative Messwerte', () => {
        expect(fitScale(-10, 500)).toBe(1)
    })
})

describe('steadyScale', () => {
    it('behält den alten Maßstab bei winziger Abweichung', () => {
        expect(steadyScale(0.872, 0.873)).toBe(0.872)
    })

    it('übernimmt einen echten neuen Maßstab', () => {
        expect(steadyScale(1, 0.8)).toBe(0.8)
    })

    it('übernimmt auch den Weg zurück auf Originalgröße', () => {
        expect(steadyScale(0.8, 1)).toBe(1)
    })
})

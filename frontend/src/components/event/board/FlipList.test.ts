import {describe, expect, it} from 'vitest'
import {localDelta, sameKeySequence} from './FlipList.tsx'

/**
 * `sameKeySequence` entscheidet, ob FlipList einen Renderdurchgang animiert oder nur
 * die Basisposition nachführt (Fix: ein Tick-Re-Render während laufender Animation
 * darf keine Slides erneut abspielen — siehe FlipList.tsx KDoc).
 */
describe('sameKeySequence', () => {
    it('erkennt identische Reihenfolge als unverändert', () => {
        expect(sameKeySequence(['a', 'b', 'c'], ['a', 'b', 'c'])).toBe(true)
    })

    it('erkennt zwei leere Folgen als unverändert', () => {
        expect(sameKeySequence([], [])).toBe(true)
    })

    it('erkennt eine Umsortierung derselben Schlüssel als geändert', () => {
        expect(sameKeySequence(['a', 'b', 'c'], ['b', 'a', 'c'])).toBe(false)
    })

    it('erkennt einen neuen Schlüssel als geändert', () => {
        expect(sameKeySequence(['a', 'b', 'c'], ['a', 'b'])).toBe(false)
    })

    it('erkennt einen entfernten Schlüssel als geändert', () => {
        expect(sameKeySequence(['a', 'b'], ['a', 'b', 'c'])).toBe(false)
    })

    it('erkennt einen ausgetauschten Schlüssel gleicher Länge als geändert', () => {
        expect(sameKeySequence(['a', 'b', 'c'], ['a', 'b', 'd'])).toBe(false)
    })
})

/**
 * `localDelta` rechnet den in Bildschirmpixeln gemessenen Weg in den lokalen Transform um
 * — nötig, seit die Panels ein zu großes Feld verkleinern (FitToHeight).
 */
describe('localDelta', () => {
    it('lässt den Weg in einer unskalierten Fläche unverändert', () => {
        expect(localDelta(80, 1)).toBe(80)
    })

    it('verlängert den lokalen Weg in einer verkleinerten Fläche', () => {
        expect(localDelta(40, 0.5)).toBe(80)
    })

    it('behält das Vorzeichen — eine Zeile wandert auch nach oben', () => {
        expect(localDelta(-40, 0.5)).toBe(-80)
    })

    it('nimmt einen noch nicht gelayouteten Knoten unverändert hin', () => {
        expect(localDelta(24, 0)).toBe(24)
    })
})

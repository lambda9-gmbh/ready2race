import {describe, expect, it} from 'vitest'
import {sameKeySequence} from './FlipList.tsx'

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

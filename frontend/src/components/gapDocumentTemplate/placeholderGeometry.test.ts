import {describe, expect, it} from 'vitest'
import {clampRect, MIN_EXTENT, nudgeRect, parsePercent} from './placeholderGeometry.ts'

const rect = {relLeft: 0.2, relTop: 0.3, relWidth: 0.5, relHeight: 0.1}

describe('parsePercent', () => {
    it('liest Punkt und Komma als Dezimaltrenner', () => {
        expect(parsePercent('44.7')).toBeCloseTo(0.447)
        expect(parsePercent('44,7')).toBeCloseTo(0.447)
    })

    it('gibt undefined für Unlesbares', () => {
        expect(parsePercent('')).toBeUndefined()
        expect(parsePercent('abc')).toBeUndefined()
    })

    it('begrenzt auf 0 bis 100 Prozent', () => {
        expect(parsePercent('-5')).toBe(0)
        expect(parsePercent('140')).toBe(1)
    })
})

describe('clampRect', () => {
    it('lässt einen Kasten innerhalb der Seite unverändert', () => {
        expect(clampRect(rect)).toEqual(rect)
    })

    it('schiebt einen überstehenden Kasten zurück auf die Seite', () => {
        expect(clampRect({relLeft: 0.8, relTop: 0.3, relWidth: 0.5, relHeight: 0.1})).toEqual({
            relLeft: 0.5,
            relTop: 0.3,
            relWidth: 0.5,
            relHeight: 0.1,
        })
    })

    it('kürzt einen Kasten, der breiter als die Seite ist', () => {
        expect(clampRect({relLeft: 0, relTop: 0, relWidth: 1.5, relHeight: 2})).toEqual({
            relLeft: 0,
            relTop: 0,
            relWidth: 1,
            relHeight: 1,
        })
    })

    it('erzwingt eine Mindestbreite und -höhe statt 0', () => {
        expect(clampRect({relLeft: 0.2, relTop: 0.3, relWidth: 0, relHeight: 0})).toEqual({
            relLeft: 0.2,
            relTop: 0.3,
            relWidth: MIN_EXTENT,
            relHeight: MIN_EXTENT,
        })
    })

    it('erzwingt die Mindestbreite auch bei negativer Eingabe', () => {
        const clamped = clampRect({relLeft: 0.2, relTop: 0.3, relWidth: -1, relHeight: -1})
        expect(clamped.relWidth).toBe(MIN_EXTENT)
        expect(clamped.relHeight).toBe(MIN_EXTENT)
    })
})

describe('nudgeRect', () => {
    it('verschiebt um 0,1 Prozent', () => {
        expect(nudgeRect(rect, 'right', false).relLeft).toBeCloseTo(0.201)
        expect(nudgeRect(rect, 'up', false).relTop).toBeCloseTo(0.299)
    })

    it('verschiebt mit Shift um 1 Prozent', () => {
        expect(nudgeRect(rect, 'down', true).relTop).toBeCloseTo(0.31)
        expect(nudgeRect(rect, 'left', true).relLeft).toBeCloseTo(0.19)
    })

    it('bleibt auf der Seite', () => {
        const atEdge = {relLeft: 0, relTop: 0, relWidth: 0.5, relHeight: 0.1}
        expect(nudgeRect(atEdge, 'left', true).relLeft).toBe(0)
    })
})

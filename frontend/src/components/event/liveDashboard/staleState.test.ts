import {describe, expect, it} from 'vitest'
import {describeStale} from './staleState.ts'

describe('describeStale', () => {
    it('meldet nichts, solange der Abruf frisch ist', () => {
        expect(describeStale(1_000, false, 2_000)).toEqual({
            show: false,
            fromCache: false,
            actionsLocked: false,
        })
    })

    it('sperrt Aktionen, sobald der Abruf fehlschlägt', () => {
        expect(describeStale(1_000, true, 2_000)).toEqual({
            show: true,
            fromCache: false,
            actionsLocked: true,
        })
    })

    it('erkennt einen Stand aus dem Cache an seinem Alter', () => {
        expect(describeStale(1_000, true, 1_000 + 60_000)).toEqual({
            show: true,
            fromCache: true,
            actionsLocked: true,
        })
    })

    it('sperrt Aktionen auch ohne jeden Abrufzeitpunkt', () => {
        expect(describeStale(null, true, 2_000)).toEqual({
            show: true,
            fromCache: false,
            actionsLocked: true,
        })
    })
})

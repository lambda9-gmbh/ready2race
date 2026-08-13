import {describe, expect, it} from 'vitest'
import {streamOverlayContent} from './streamOverlay.ts'
import {AthleteBoardMatch, AthleteBoardResult, BoardMatchSlotDto} from '@api/types.gen.ts'

const match = (name: string) => ({matchName: name}) as unknown as AthleteBoardMatch
const result = (name: string) => ({matchName: name}) as unknown as AthleteBoardResult
const slot = (offset: number, m?: AthleteBoardMatch, r?: AthleteBoardResult): BoardMatchSlotDto =>
    ({offset, match: m ?? null, result: r ?? null}) as BoardMatchSlotDto

describe('streamOverlayContent', () => {
    it('AUTO zeigt den laufenden Lauf, wenn einer läuft', () => {
        const content = streamOverlayContent([slot(0, match('VF1')), slot(-1, undefined, result('ZF'))], 'AUTO')
        expect(content).toMatchObject({kind: 'running'})
    })

    it('AUTO fällt ohne laufenden Lauf auf das jüngste Ergebnis zurück', () => {
        const content = streamOverlayContent([slot(0), slot(-1, undefined, result('ZF'))], undefined)
        expect(content).toMatchObject({kind: 'result'})
    })

    it('AUTO ohne beides bleibt leer', () => {
        expect(streamOverlayContent([slot(0), slot(-1)], 'AUTO')).toBeNull()
    })

    it('RUNNING zeigt kein Ergebnis als Rückfall', () => {
        expect(streamOverlayContent([slot(0), slot(-1, undefined, result('ZF'))], 'RUNNING')).toBeNull()
    })

    it('RESULTS zeigt nur das Ergebnis', () => {
        const content = streamOverlayContent([slot(-1, undefined, result('ZF'))], 'RESULTS')
        expect(content).toMatchObject({kind: 'result'})
    })

    it('UPCOMING zeigt den nächsten anstehenden Lauf', () => {
        const content = streamOverlayContent([slot(1, match('HF2'))], 'UPCOMING')
        expect(content).toMatchObject({kind: 'upcoming'})
    })

    it('ein Ergebnis im Slot −1 wird im AUTO-Modus nicht als laufend ausgegeben', () => {
        const content = streamOverlayContent([slot(0), slot(-1, match('noch laufender älterer Lauf'))], 'AUTO')
        // Slot −1 kann laut resolveOffset auch einen FRÜHER gestarteten, noch laufenden
        // Lauf tragen — der ist kein Ergebnis und wird im AUTO-Rückfall übersprungen.
        expect(content).toBeNull()
    })
})

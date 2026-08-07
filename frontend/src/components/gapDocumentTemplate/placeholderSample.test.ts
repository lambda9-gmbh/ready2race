import {describe, expect, it} from 'vitest'
import {sampleTextFor} from './placeholderSample.ts'

describe('sampleTextFor', () => {
    it('zeigt für jeden Typ einen Beispieltext', () => {
        expect(sampleTextFor('FULL_NAME')).toBe('Max Mustermann')
        expect(sampleTextFor('PLACE')).toBe('1. Platz')
    })

    it('zeigt bei freiem Text den eingegebenen Text', () => {
        expect(sampleTextFor('FREE_TEXT', 'Moritz Petri — Vorsitzender')).toBe(
            'Moritz Petri — Vorsitzender',
        )
    })

    it('fällt bei leerem freien Text auf einen Hinweis zurück', () => {
        expect(sampleTextFor('FREE_TEXT')).toBe('Fester Text')
    })
})

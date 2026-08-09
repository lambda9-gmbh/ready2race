import {describe, expect, it} from 'vitest'
import {sampleTextFor} from './placeholderSample.ts'

describe('sampleTextFor', () => {
    const fallback = 'Fester Text'

    it('zeigt für jeden Typ einen Beispieltext', () => {
        expect(sampleTextFor('FULL_NAME', undefined, fallback)).toBe('Max Mustermann')
        expect(sampleTextFor('PLACE', undefined, fallback)).toBe('1. Platz')
    })

    it('zeigt bei freiem Text den eingegebenen Text', () => {
        expect(sampleTextFor('FREE_TEXT', 'Moritz Petri — Vorsitzender', fallback)).toBe(
            'Moritz Petri — Vorsitzender',
        )
    })

    it('fällt bei leerem freien Text auf den übergebenen Hinweis zurück', () => {
        expect(sampleTextFor('FREE_TEXT', undefined, fallback)).toBe('Fester Text')
        expect(sampleTextFor('FREE_TEXT', undefined, 'Fixed Text')).toBe('Fixed Text')
    })
})

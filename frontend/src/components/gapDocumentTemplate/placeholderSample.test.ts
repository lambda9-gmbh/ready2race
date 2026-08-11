import {describe, expect, it} from 'vitest'
import {chainSegments, sampleTextFor} from './placeholderSample.ts'

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

    // Das Feld trägt seit dem 09.08.2026 die Vereine aller Athleten eines Bootes. Ein einzelner
    // kurzer Verein als Beispiel hätte die Vorschau zu einem Versprechen gemacht, das der Druck
    // nicht hält.
    it('zeigt beim Vereinsnamen eine Kette, nicht einen einzelnen Verein', () => {
        expect(chainSegments(sampleTextFor('CLUB_NAME', undefined, fallback)).length).toBeGreaterThan(1)
    })
})

// Die Vorschau setzt jedes Glied unzerbrechlich und lässt den Browser nur dazwischen umbrechen —
// dieselbe Regel wie im Renderer: ein Vereinsname wird nie zerrissen.
describe('chainSegments', () => {
    it('zerlegt eine Kette in ihre Vereine', () => {
        expect(chainSegments('Ruderklub Flensburg / Rostocker Ruderclub')).toEqual([
            'Ruderklub Flensburg',
            'Rostocker Ruderclub',
        ])
    })

    it('lässt einen einzelnen Verein unangetastet', () => {
        expect(chainSegments('Ruderklub Flensburg e.V.')).toEqual(['Ruderklub Flensburg e.V.'])
    })

    // Ein Schrägstrich ohne Leerzeichen gehört zum Namen (z.B. "(1879/83)") und ist kein Trenner.
    it('trennt nicht an einem Schrägstrich mitten im Namen', () => {
        expect(chainSegments('RV Waging (1879/83)')).toEqual(['RV Waging (1879/83)'])
    })
})

import {describe, expect, it} from 'vitest'
import {ClubShortNameDto} from '@api/types.gen.ts'
import {clubShortNameAction, mergedSpellings, primaryName} from './clubShortNames.ts'

const row = (overrides: Partial<ClubShortNameDto>): ClubShortNameDto => ({
    nameKey: 'rostockerruderclub',
    names: ['Rostocker Ruderclub'],
    shortName: 'Rostocker RC',
    maintained: false,
    ...overrides,
})

describe('clubShortNameAction', () => {
    it('schreibt nichts, wenn niemand das vorbelegte Feld angefasst hat', () => {
        // Der Fall, der 46 Zeilen still in gepflegte Einträge verwandeln würde.
        expect(clubShortNameAction(row({}), 'Rostocker RC')).toBe('none')
    })

    it('ignoriert Leerraum um einen unveränderten Wert', () => {
        expect(clubShortNameAction(row({}), '  Rostocker RC  ')).toBe('none')
    })

    it('speichert einen abweichenden Wert', () => {
        expect(clubShortNameAction(row({}), 'RRC')).toBe('save')
    })

    it('lässt eine gepflegte Kurzform unberührt, solange sie unverändert ist', () => {
        expect(clubShortNameAction(row({shortName: 'RRC', maintained: true}), 'RRC')).toBe('none')
    })

    it('löscht den gepflegten Eintrag, wenn das Feld geleert wird', () => {
        expect(clubShortNameAction(row({shortName: 'RRC', maintained: true}), '')).toBe('delete')
        expect(clubShortNameAction(row({shortName: 'RRC', maintained: true}), '   ')).toBe('delete')
    })

    it('löscht nichts, wenn die Zeile ohnehin aus der Heuristik kommt', () => {
        expect(clubShortNameAction(row({}), '')).toBe('none')
    })
})

describe('mergedSpellings', () => {
    it('zeigt die zusammengefassten Schreibweisen, ohne die führende zu wiederholen', () => {
        const merged = row({
            names: ['Rostocker Ruderclub', 'Rostocker Ruder-Club von 1885 e.V.'],
        })

        expect(primaryName(merged)).toBe('Rostocker Ruderclub')
        expect(mergedSpellings(merged)).toEqual(['Rostocker Ruder-Club von 1885 e.V.'])
    })

    it('hat für einen einzeln vorkommenden Verein nichts anzuzeigen', () => {
        expect(mergedSpellings(row({}))).toEqual([])
    })
})

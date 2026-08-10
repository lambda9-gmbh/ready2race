import {describe, expect, it} from 'vitest'
import {teamNameSuffix} from './helpers.ts'

describe('teamNameSuffix', () => {
    it('hängt Separator und Namen an, wenn ein Teamname vorhanden ist', () => {
        expect(teamNameSuffix('Herren I')).toBe(' | Herren I')
    })

    it('lässt Separator und Namen weg, wenn kein Teamname vorhanden ist', () => {
        expect(teamNameSuffix(undefined)).toBe('')
        expect(teamNameSuffix(null)).toBe('')
        expect(teamNameSuffix('')).toBe('')
    })

    it('behandelt reinen Leerraum wie leer', () => {
        expect(teamNameSuffix('   ')).toBe('')
    })

    it('erzeugt niemals das Literal "undefined"', () => {
        expect(teamNameSuffix(undefined)).not.toContain('undefined')
        expect(`gemeldet von Verein${teamNameSuffix(undefined)}`).toBe('gemeldet von Verein')
    })
})

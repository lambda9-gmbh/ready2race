import {describe, expect, test} from 'vitest'
import {
    matchingPreset,
    presetOptions,
    resultListQuery,
} from '@components/awardCeremony/resultListOptions.ts'

describe('resultListOptions', () => {
    test('das Aushang-Preset druckt alles, alle Plätze und groß', () => {
        expect(presetOptions.posting).toEqual({
            crew: true,
            times: true,
            podiumOnly: false,
            byRatingCategory: true,
            largePrint: true,
        })
    })

    test('das Siegerehrungs-Preset entspricht dem klassischen Bogen: Podium, Pult-Schriftgrad', () => {
        expect(presetOptions.ceremony).toEqual({
            crew: true,
            times: true,
            podiumOnly: true,
            byRatingCategory: true,
            largePrint: false,
        })
    })

    test('unveränderte Häkchen zeigen ihr Preset an', () => {
        expect(matchingPreset(presetOptions.posting)).toBe('posting')
        expect(matchingPreset(presetOptions.ceremony)).toBe('ceremony')
    })

    test('ein verstelltes Häkchen macht die Auswahl zur eigenen — kein Preset ist mehr markiert', () => {
        expect(matchingPreset({...presetOptions.posting, crew: false})).toBeNull()
        expect(matchingPreset({...presetOptions.ceremony, largePrint: true})).toBeNull()
    })

    test('die Häkchen werden vollständig zu Query-Parametern — largePrint wird zum Schriftgrad', () => {
        expect(resultListQuery(presetOptions.posting)).toEqual({
            crew: true,
            times: true,
            podiumOnly: false,
            byRatingCategory: true,
            size: 'POSTING',
        })
        expect(resultListQuery(presetOptions.ceremony)).toEqual({
            crew: true,
            times: true,
            podiumOnly: true,
            byRatingCategory: true,
            size: 'CEREMONY',
        })
    })
})

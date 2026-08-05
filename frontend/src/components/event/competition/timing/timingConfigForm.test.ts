import {describe, expect, it} from 'vitest'
import {
    emptyTimingForm,
    mapDtoToTimingForm,
    mapTimingFormToRequest,
    timingConfigWarnings,
} from './timingConfigForm.ts'

const qualificationPreset = '11111111-1111-1111-1111-111111111111'
const roundsPreset = '22222222-2222-2222-2222-222222222222'
const importPreset = '33333333-3333-3333-3333-333333333333'

describe('mapDtoToTimingForm', () => {
    it('setzt ein fehlendes Zeitnahme-System auf NONE', () => {
        const form = mapDtoToTimingForm({})

        expect(form.timingSystem).toBe('NONE')
    })

    it('belegt jedes Feld des Formulars, damit reset() keines verwirft', () => {
        const form = mapDtoToTimingForm({timingSystem: 'RACECLOCKER'})

        expect(Object.keys(form).sort()).toEqual(Object.keys(emptyTimingForm).sort())
    })
})

describe('mapTimingFormToRequest', () => {
    it('schickt NONE als null', () => {
        const request = mapTimingFormToRequest({...emptyTimingForm, timingSystem: 'NONE'})

        expect(request.timingSystem).toBeNull()
    })

    it('verwirft die URLs, wenn nicht RaceClocker gewählt ist', () => {
        // Sonst bliebe eine URL stehen, die im Tab gar nicht mehr sichtbar ist.
        const request = mapTimingFormToRequest({
            ...emptyTimingForm,
            timingSystem: 'WEBSCORER',
            heatsResultsUrl: 'https://www.raceclocker.com/7c854955',
        })

        expect(request.heatsResultsUrl).toBeNull()
    })

    it('verwirft das Qualifikations-Preset, wenn nicht RaceClocker gewählt ist', () => {
        // Webscorer kennt die Zweiteilung nicht und zeigt nur ein Preset-Feld.
        const request = mapTimingFormToRequest({
            ...emptyTimingForm,
            timingSystem: 'WEBSCORER',
            startlistConfigQualification: {id: qualificationPreset, label: 'Zeitfahren'},
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
        })

        expect(request.startlistConfigQualification).toBeNull()
        expect(request.startlistConfigRounds).toBe(roundsPreset)
    })

    it('übernimmt bei RaceClocker beide Presets und das Import-Preset', () => {
        const request = mapTimingFormToRequest({
            timingSystem: 'RACECLOCKER',
            timeTrialResultsUrl: 'https://www.raceclocker.com/7ffb822a',
            heatsResultsUrl: 'https://www.raceclocker.com/7c854955',
            startlistConfigQualification: {id: qualificationPreset, label: 'Zeitfahren'},
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
            resultImportConfig: {id: importPreset, label: 'Webscorer xlsx'},
        })

        expect(request.startlistConfigQualification).toBe(qualificationPreset)
        expect(request.startlistConfigRounds).toBe(roundsPreset)
        expect(request.resultImportConfig).toBe(importPreset)
    })

    it('macht aus einer leeren URL null statt eines Leerstrings', () => {
        const request = mapTimingFormToRequest({
            ...emptyTimingForm,
            timingSystem: 'RACECLOCKER',
            timeTrialResultsUrl: '   ',
        })

        expect(request.timeTrialResultsUrl).toBeNull()
    })
})

describe('timingConfigWarnings', () => {
    it('schweigt, solange kein System gewählt ist', () => {
        expect(timingConfigWarnings(emptyTimingForm)).toEqual([])
    })

    it('mahnt bei RaceClocker die Läufe-URL an', () => {
        const warnings = timingConfigWarnings({
            ...emptyTimingForm,
            timingSystem: 'RACECLOCKER',
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
        })

        expect(warnings).toEqual(['heatsUrl'])
    })

    it('mahnt das Startlisten-Preset an, auch bei Webscorer', () => {
        const warnings = timingConfigWarnings({...emptyTimingForm, timingSystem: 'WEBSCORER'})

        expect(warnings).toEqual(['startlistRounds'])
    })

    it('mahnt die Zeitfahren-URL nicht an — nicht jeder Wettkampf hat eine Qualifikation', () => {
        const warnings = timingConfigWarnings({
            ...emptyTimingForm,
            timingSystem: 'RACECLOCKER',
            heatsResultsUrl: 'https://www.raceclocker.com/7c854955',
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
        })

        expect(warnings).toEqual([])
    })
})

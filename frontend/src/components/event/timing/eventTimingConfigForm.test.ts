import {describe, expect, it} from 'vitest'
import {
    emptyEventTimingForm,
    mapDtoToEventTimingForm,
    mapEventTimingFormToRequest,
} from './eventTimingConfigForm.ts'

describe('mapDtoToEventTimingForm', () => {
    it('setzt ein fehlendes Zeitnahme-System auf NONE', () => {
        expect(mapDtoToEventTimingForm({}).timingSystem).toBe('NONE')
    })

    it('belegt jedes Feld des Formulars, damit reset() keines verwirft', () => {
        const form = mapDtoToEventTimingForm({timingSystem: 'RACECLOCKER'})

        expect(Object.keys(form).sort()).toEqual(Object.keys(emptyEventTimingForm).sort())
    })
})

describe('mapEventTimingFormToRequest', () => {
    it('schickt NONE als null', () => {
        expect(mapEventTimingFormToRequest(emptyEventTimingForm).timingSystem).toBeNull()
    })

    it('verwirft die URLs, wenn nicht RaceClocker gewählt ist', () => {
        // Eine unsichtbare Adresse, die alle Wettkämpfe erben, findet sonst niemand wieder.
        const request = mapEventTimingFormToRequest({
            ...emptyEventTimingForm,
            timingSystem: 'WEBSCORER',
            heatsResultsUrl: 'https://www.raceclocker.com/7c854955',
        })

        expect(request.heatsResultsUrl).toBeNull()
    })

    it('verwirft das Qualifikations-Format, wenn nicht RaceClocker gewählt ist', () => {
        // Webscorer kennt die Zweiteilung Zeitfahren/Läufe nicht und zeigt nur ein Export-Feld.
        const request = mapEventTimingFormToRequest({
            ...emptyEventTimingForm,
            timingSystem: 'WEBSCORER',
            startlistConfigQualification: {id: '11111111-1111-1111-1111-111111111111', label: 'Q'},
            startlistConfigRounds: {id: '22222222-2222-2222-2222-222222222222', label: 'R'},
            resultImportConfig: {id: '33333333-3333-3333-3333-333333333333', label: 'I'},
        })

        expect(request.startlistConfigQualification).toBeNull()
        expect(request.startlistConfigRounds).toBe('22222222-2222-2222-2222-222222222222')
        expect(request.resultImportConfig).toBe('33333333-3333-3333-3333-333333333333')
    })

    it('verwirft die Formate, wenn kein System gesetzt ist', () => {
        const request = mapEventTimingFormToRequest({
            ...emptyEventTimingForm,
            startlistConfigRounds: {id: '22222222-2222-2222-2222-222222222222', label: 'R'},
            resultImportConfig: {id: '33333333-3333-3333-3333-333333333333', label: 'I'},
        })

        expect(request.startlistConfigRounds).toBeNull()
        expect(request.resultImportConfig).toBeNull()
    })

    it('macht aus einer leeren URL null statt eines Leerstrings', () => {
        const request = mapEventTimingFormToRequest({
            ...emptyEventTimingForm,
            timingSystem: 'RACECLOCKER',
            timeTrialResultsUrl: '   ',
            heatsResultsUrl: 'https://www.raceclocker.com/7c854955',
        })

        expect(request.timeTrialResultsUrl).toBeNull()
        expect(request.heatsResultsUrl).toBe('https://www.raceclocker.com/7c854955')
    })
})

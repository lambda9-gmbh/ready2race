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

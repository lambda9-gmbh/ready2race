import {describe, expect, it} from 'vitest'
import {
    emptyEventTimingForm,
    mapDtoToEventTimingForm,
    mapEventTimingFormToRequest,
} from './eventTimingConfigForm.ts'

describe('mapDtoToEventTimingForm', () => {
    it('setzt ein fehlendes Zeitnahme-System auf NONE', () => {
        expect(
            mapDtoToEventTimingForm({
                autoPull: false,
                intervalActiveSeconds: 5,
                intervalUpcomingSeconds: 60,
                watchBeforeMinutes: 15,
                watchAfterMinutes: 120,
            }).timingSystem,
        ).toBe('NONE')
    })

    it('belegt jedes Feld des Formulars, damit reset() keines verwirft', () => {
        const form = mapDtoToEventTimingForm({
            timingSystem: 'RACECLOCKER',
            autoPull: false,
            intervalActiveSeconds: 5,
            intervalUpcomingSeconds: 60,
            watchBeforeMinutes: 15,
            watchAfterMinutes: 120,
        })

        expect(Object.keys(form).sort()).toEqual(Object.keys(emptyEventTimingForm).sort())
    })
})

describe('mapEventTimingFormToRequest', () => {
    it('schickt NONE als null', () => {
        expect(mapEventTimingFormToRequest(emptyEventTimingForm).timingSystem).toBeNull()
    })

    it('verwirft die Rennen-Anwahl, wenn nicht RaceClocker gewählt ist', () => {
        // Ein unsichtbar angewähltes Rennen, das alle Wettkämpfe erben, findet sonst niemand wieder.
        const request = mapEventTimingFormToRequest({
            ...emptyEventTimingForm,
            timingSystem: 'WEBSCORER',
            raceRounds: {id: '44444444-4444-4444-4444-444444444444', label: 'Kurzstrecke'},
        })

        expect(request.raceRounds).toBeNull()
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

    it('gibt eine leere Anwahl als null weiter, nicht als Leerwert', () => {
        // Ohne Zeitfahren-Rennen bleibt die Quali-Anwahl leer -- das ist der Normalfall einer
        // Regatta ohne Zeitfahren und muss als "nicht angewählt" ankommen.
        const request = mapEventTimingFormToRequest({
            ...emptyEventTimingForm,
            timingSystem: 'RACECLOCKER',
            raceQualification: null,
            raceRounds: {id: '44444444-4444-4444-4444-444444444444', label: 'Kurzstrecke'},
        })

        expect(request.raceQualification).toBeNull()
        expect(request.raceRounds).toBe('44444444-4444-4444-4444-444444444444')
    })
})

describe('automatischer Abruf', () => {
    it('übernimmt die Abruf-Einstellungen aus dem Dto', () => {
        const form = mapDtoToEventTimingForm({
            timingSystem: 'RACECLOCKER',
            raceQualification: null,
            raceRounds: null,
            startlistConfigQualification: null,
            startlistConfigRounds: null,
            resultImportConfig: null,
            autoPull: true,
            intervalActiveSeconds: 3,
            intervalUpcomingSeconds: 90,
            watchBeforeMinutes: 20,
            watchAfterMinutes: 60,
            deviatingCompetitions: [],
        })

        expect(form.autoPull).toBe(true)
        expect(form.intervalActiveSeconds).toBe(3)
        expect(form.intervalUpcomingSeconds).toBe(90)
        expect(form.watchBeforeMinutes).toBe(20)
        expect(form.watchAfterMinutes).toBe(60)
    })

    it('reicht die Abruf-Einstellungen unverändert an den Request weiter', () => {
        const request = mapEventTimingFormToRequest({
            ...emptyEventTimingForm,
            timingSystem: 'RACECLOCKER',
            autoPull: true,
            intervalActiveSeconds: 3,
            intervalUpcomingSeconds: 90,
            watchBeforeMinutes: 20,
            watchAfterMinutes: 60,
        })

        expect(request.autoPull).toBe(true)
        expect(request.intervalActiveSeconds).toBe(3)
        expect(request.intervalUpcomingSeconds).toBe(90)
    })

    // Ohne RaceClocker gibt es keinen Feed, den man abrufen könnte - der Schalter darf dann nicht
    // still eingeschaltet gespeichert bleiben, sonst steht in der Datenbank eine Automatik, die
    // die Oberfläche gar nicht mehr anzeigt.
    it('schaltet den Abruf ab, wenn das System nicht RaceClocker ist', () => {
        const request = mapEventTimingFormToRequest({
            ...emptyEventTimingForm,
            timingSystem: 'WEBSCORER',
            autoPull: true,
        })

        expect(request.autoPull).toBe(false)
    })
})

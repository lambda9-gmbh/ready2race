import {describe, expect, it} from 'vitest'
import {
    effectiveTimingSystem,
    emptyTimingForm,
    mapDtoToTimingForm,
    mapTimingFormToRequest,
    overridesTiming,
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

    it('übernimmt die Zeitnahme-Voreinstellung der Veranstaltung', () => {
        const form = mapDtoToTimingForm({
            eventTimingSystem: 'RACECLOCKER',
            eventHeatsResultsUrl: 'https://www.raceclocker.com/7c854955',
        })

        expect(form.eventTimingSystem).toBe('RACECLOCKER')
        expect(form.eventHeatsResultsUrl).toBe('https://www.raceclocker.com/7c854955')
        // Ein alter Server ohne die Felder darf nicht als „erbt etwas" gelesen werden.
        expect(mapDtoToTimingForm({}).eventTimingSystem).toBe('NONE')
    })

    it('übernimmt die Qualifikationsrunde des Ablaufs', () => {
        expect(mapDtoToTimingForm({hasQualificationRound: true}).hasQualificationRound).toBe(true)
        // Ein alter Server ohne das Feld darf keine Warnung ausloesen.
        expect(mapDtoToTimingForm({}).hasQualificationRound).toBe(false)
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
            ...emptyTimingForm,
            timingSystem: 'RACECLOCKER',
            timeTrialResultsUrl: 'https://www.raceclocker.com/7ffb822a',
            heatsResultsUrl: 'https://www.raceclocker.com/7c854955',
            startlistConfigQualification: {id: qualificationPreset, label: 'Zeitfahren'},
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
            resultImportConfig: {id: importPreset, label: 'Webscorer xlsx'},
            hasQualificationRound: true,
        })

        expect(request.startlistConfigQualification).toBe(qualificationPreset)
        expect(request.startlistConfigRounds).toBe(roundsPreset)
        expect(request.resultImportConfig).toBe(importPreset)
    })

    it('behält Override-URL und Presets, wenn RaceClocker von der Veranstaltung geerbt wird', () => {
        // Bei geerbtem System steht das lokale Feld auf NONE — trotzdem sind die Adressen und
        // Presets im Tab sichtbar, und was sichtbar eingetragen wurde, darf das Speichern nicht
        // wegwerfen.
        const request = mapTimingFormToRequest({
            ...emptyTimingForm,
            timingSystem: 'NONE',
            eventTimingSystem: 'RACECLOCKER',
            heatsResultsUrl: 'https://www.raceclocker.com/7c854955',
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
            resultImportConfig: {id: importPreset, label: 'Webscorer xlsx'},
        })

        expect(request.timingSystem).toBeNull()
        expect(request.heatsResultsUrl).toBe('https://www.raceclocker.com/7c854955')
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

    it('verwirft alle Presets, wenn kein System gesetzt ist', () => {
        // Bei NONE zeigt der Tab kein Preset-Feld. Ein gespeichertes Preset waere damit
        // unsichtbar und wuerde vom Export trotzdem benutzt, weil die serverseitige
        // Auflösung timing_system nicht liest.
        const request = mapTimingFormToRequest({
            ...emptyTimingForm,
            timingSystem: 'NONE',
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
            resultImportConfig: {id: importPreset, label: 'Webscorer xlsx'},
        })

        expect(request.startlistConfigRounds).toBeNull()
        expect(request.resultImportConfig).toBeNull()
    })
})

describe('effectiveTimingSystem', () => {
    it('nimmt die Voreinstellung der Veranstaltung, solange der Wettkampf keine eigene hat', () => {
        expect(
            effectiveTimingSystem({...emptyTimingForm, eventTimingSystem: 'RACECLOCKER'}),
        ).toBe('RACECLOCKER')
    })

    it('lässt den Wettkampf die Veranstaltung überstimmen', () => {
        expect(
            effectiveTimingSystem({
                ...emptyTimingForm,
                timingSystem: 'WEBSCORER',
                eventTimingSystem: 'RACECLOCKER',
            }),
        ).toBe('WEBSCORER')
    })
})

describe('overridesTiming', () => {
    it('erkennt einen Wettkampf ohne eigene Werte als „erbt"', () => {
        expect(
            overridesTiming({...emptyTimingForm, eventTimingSystem: 'RACECLOCKER'}),
        ).toBe(false)
    })

    it('zählt auch eine einzelne eigene Adresse als Abweichung', () => {
        // Teil-Override: System geerbt, aber ein eigenes Läufe-Rennen. Der Schalter im Tab muss
        // dafür an sein, sonst würde die Adresse beim nächsten Speichern still verschwinden.
        expect(
            overridesTiming({
                ...emptyTimingForm,
                eventTimingSystem: 'RACECLOCKER',
                heatsResultsUrl: 'https://www.raceclocker.com/7c854955',
            }),
        ).toBe(true)
    })

    it('lässt sich von Leerzeichen nicht täuschen', () => {
        expect(overridesTiming({...emptyTimingForm, heatsResultsUrl: '   '})).toBe(false)
    })

    it('zählt ein eigenes Dateiformat als Abweichung', () => {
        // Auch ein eigener Startlisten Export ist ein Override — sonst stünde der Schalter aus,
        // und das nächste Speichern würde das Format wegwerfen.
        expect(
            overridesTiming({
                ...emptyTimingForm,
                eventTimingSystem: 'RACECLOCKER',
                startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
            }),
        ).toBe(true)
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

    it('mahnt eine URL nicht an, die von der Veranstaltung kommt', () => {
        const warnings = timingConfigWarnings({
            ...emptyTimingForm,
            eventTimingSystem: 'RACECLOCKER',
            eventHeatsResultsUrl: 'https://www.raceclocker.com/7c854955',
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
        })

        expect(warnings).toEqual([])
    })

    it('mahnt bei geerbtem RaceClocker ohne Adressen trotzdem an', () => {
        // Sonst bliebe der einzige Hinweis auf eine halbfertige Veranstaltungs-Vorgabe aus, weil
        // der Wettkampf selbst „nichts gesetzt" hat.
        const warnings = timingConfigWarnings({
            ...emptyTimingForm,
            eventTimingSystem: 'RACECLOCKER',
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
        })

        expect(warnings).toEqual(['heatsUrl'])
    })

    it('mahnt einen Startlisten Export nicht an, der von der Veranstaltung kommt', () => {
        const warnings = timingConfigWarnings({
            ...emptyTimingForm,
            eventTimingSystem: 'RACECLOCKER',
            eventHeatsResultsUrl: 'https://www.raceclocker.com/7c854955',
            eventStartlistConfigRounds: roundsPreset,
        })

        expect(warnings).toEqual([])
    })

    it('mahnt bei einer Qualifikation den geerbten Quali-Export nicht an', () => {
        const warnings = timingConfigWarnings({
            ...emptyTimingForm,
            eventTimingSystem: 'RACECLOCKER',
            eventTimeTrialResultsUrl: 'https://www.raceclocker.com/7ffb822a',
            eventHeatsResultsUrl: 'https://www.raceclocker.com/7c854955',
            eventStartlistConfigQualification: qualificationPreset,
            eventStartlistConfigRounds: roundsPreset,
            hasQualificationRound: true,
        })

        expect(warnings).toEqual([])
    })

    it('mahnt das Startlisten-Preset an, auch bei Webscorer', () => {
        const warnings = timingConfigWarnings({...emptyTimingForm, timingSystem: 'WEBSCORER'})

        expect(warnings).toEqual(['startlistRounds'])
    })

    it('mahnt die Zeitfahren-URL nicht an, solange der Wettkampf keine Qualifikation hat', () => {
        const warnings = timingConfigWarnings({
            ...emptyTimingForm,
            timingSystem: 'RACECLOCKER',
            heatsResultsUrl: 'https://www.raceclocker.com/7c854955',
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
        })

        expect(warnings).toEqual([])
    })

    it('mahnt bei einer Qualifikationsrunde die Zeitfahren-URL und ihr Preset an', () => {
        // Genau der stille Fall: ohne Quali-Preset antwortet der Startlisten-Export mit
        // STARTLIST_CONFIG_NOT_CONFIGURED, und das faellt sonst erst am Renntag auf.
        const warnings = timingConfigWarnings({
            ...emptyTimingForm,
            timingSystem: 'RACECLOCKER',
            heatsResultsUrl: 'https://www.raceclocker.com/7c854955',
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
            hasQualificationRound: true,
        })

        expect(warnings).toEqual(['timeTrialUrl', 'startlistQualification'])
    })

    it('schweigt, wenn die Qualifikation vollstaendig eingerichtet ist', () => {
        const warnings = timingConfigWarnings({
            ...emptyTimingForm,
            timingSystem: 'RACECLOCKER',
            timeTrialResultsUrl: 'https://www.raceclocker.com/7ffb822a',
            heatsResultsUrl: 'https://www.raceclocker.com/7c854955',
            startlistConfigQualification: {id: qualificationPreset, label: 'Zeitfahren'},
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
            resultImportConfig: {id: importPreset, label: 'Webscorer xlsx'},
            hasQualificationRound: true,
        })

        expect(warnings).toEqual([])
    })

    it('mahnt die Quali-Felder bei Webscorer nicht an', () => {
        // Webscorer kennt die Zweiteilung nicht: es hat keine zweite URL, und die Qualifikation
        // faellt serverseitig auf das Runden-Preset zurueck.
        const warnings = timingConfigWarnings({
            ...emptyTimingForm,
            timingSystem: 'WEBSCORER',
            startlistConfigRounds: {id: roundsPreset, label: 'Läufe'},
            hasQualificationRound: true,
        })

        expect(warnings).toEqual([])
    })
})

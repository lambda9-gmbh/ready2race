import {describe, expect, it} from 'vitest'
import {
    effectiveTimingSystem,
    emptyTimingForm,
    mapDtoToTimingForm,
    mapTimingFormToRequest,
    overridesTiming,
    timingConfigWarnings,
} from './timingConfigForm.ts'

const startlistPreset = '22222222-2222-2222-2222-222222222222'
const importPreset = '33333333-3333-3333-3333-333333333333'
const shortCourseRace = '66666666-6666-6666-6666-666666666666'

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
            eventStartlistConfig: startlistPreset,
        })

        expect(form.eventTimingSystem).toBe('RACECLOCKER')
        expect(form.eventStartlistConfig).toBe(startlistPreset)
        // Ein alter Server ohne die Felder darf nicht als „erbt etwas" gelesen werden.
        expect(mapDtoToTimingForm({}).eventTimingSystem).toBe('NONE')
    })
})

describe('mapTimingFormToRequest', () => {
    it('schickt NONE als null', () => {
        const request = mapTimingFormToRequest({...emptyTimingForm, timingSystem: 'NONE'})

        expect(request.timingSystem).toBeNull()
    })

    it('verwirft die Rennen-Anwahl, wenn nicht RaceClocker gewählt ist', () => {
        // Sonst bliebe eine Anwahl stehen, die im Tab gar nicht mehr sichtbar ist.
        const request = mapTimingFormToRequest({
            ...emptyTimingForm,
            timingSystem: 'WEBSCORER',
            race: {id: shortCourseRace, label: 'Kurzstrecke'},
        })

        expect(request.race).toBeNull()
    })

    it('übernimmt bei RaceClocker Rennen, Preset und Import-Preset', () => {
        const request = mapTimingFormToRequest({
            ...emptyTimingForm,
            timingSystem: 'RACECLOCKER',
            race: {id: shortCourseRace, label: 'Kurzstrecke'},
            startlistConfig: {id: startlistPreset, label: 'Läufe'},
            resultImportConfig: {id: importPreset, label: 'Webscorer xlsx'},
        })

        expect(request.race).toBe(shortCourseRace)
        expect(request.startlistConfig).toBe(startlistPreset)
        expect(request.resultImportConfig).toBe(importPreset)
    })

    it('behält Rennen-Anwahl und Presets, wenn RaceClocker von der Veranstaltung geerbt wird', () => {
        // Bei geerbtem System steht das lokale Feld auf NONE — trotzdem sind Anwahl und Presets im
        // Tab sichtbar, und was sichtbar angewählt wurde, darf das Speichern nicht wegwerfen.
        const request = mapTimingFormToRequest({
            ...emptyTimingForm,
            timingSystem: 'NONE',
            eventTimingSystem: 'RACECLOCKER',
            race: {id: shortCourseRace, label: 'Kurzstrecke'},
            startlistConfig: {id: startlistPreset, label: 'Läufe'},
            resultImportConfig: {id: importPreset, label: 'Webscorer xlsx'},
        })

        expect(request.timingSystem).toBeNull()
        expect(request.race).toBe(shortCourseRace)
        expect(request.startlistConfig).toBe(startlistPreset)
        expect(request.resultImportConfig).toBe(importPreset)
    })

    it('schickt eine fehlende Anwahl als null', () => {
        const request = mapTimingFormToRequest({
            ...emptyTimingForm,
            timingSystem: 'RACECLOCKER',
            race: null,
        })

        expect(request.race).toBeNull()
    })

    it('verwirft alle Presets, wenn kein System gesetzt ist', () => {
        // Bei NONE zeigt der Tab kein Preset-Feld. Ein gespeichertes Preset waere damit
        // unsichtbar und wuerde vom Export trotzdem benutzt, weil die serverseitige
        // Auflösung timing_system nicht liest.
        const request = mapTimingFormToRequest({
            ...emptyTimingForm,
            timingSystem: 'NONE',
            startlistConfig: {id: startlistPreset, label: 'Läufe'},
            resultImportConfig: {id: importPreset, label: 'Webscorer xlsx'},
        })

        expect(request.startlistConfig).toBeNull()
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

    it('zählt eine Rennen-Anwahl NICHT als Abweichung', () => {
        // Rennen werden seit dem 11.08.2026 immer pro Wettkampf zugewiesen - es gibt keine
        // Veranstaltungs-Voreinstellung mehr, von der sie abweichen könnten. Zählte die Anwahl hier
        // als Abweichung, stünde der Schalter bei JEDEM zugeordneten Wettkampf an; wer ihn dann
        // ausschaltet, löscht die Zuordnung, die am Rennen vergeben wurde (genau der Fehler vom
        // 11.08.2026 abends: acht Wettkämpfe verloren so ihr Zeitfahren-Rennen).
        expect(
            overridesTiming({
                ...emptyTimingForm,
                eventTimingSystem: 'RACECLOCKER',
                race: {id: shortCourseRace, label: 'Kurzstrecke'},
            }),
        ).toBe(false)
    })

    it('zählt ein eigenes Dateiformat als Abweichung', () => {
        // Auch ein eigener Startlisten Export ist ein Override — sonst stünde der Schalter aus,
        // und das nächste Speichern würde das Format wegwerfen.
        expect(
            overridesTiming({
                ...emptyTimingForm,
                eventTimingSystem: 'RACECLOCKER',
                startlistConfig: {id: startlistPreset, label: 'Läufe'},
            }),
        ).toBe(true)
    })
})

describe('timingConfigWarnings', () => {
    it('schweigt, solange kein System gewählt ist', () => {
        expect(timingConfigWarnings(emptyTimingForm)).toEqual([])
    })

    it('mahnt bei RaceClocker das Rennen an', () => {
        const warnings = timingConfigWarnings({
            ...emptyTimingForm,
            timingSystem: 'RACECLOCKER',
            startlistConfig: {id: startlistPreset, label: 'Läufe'},
        })

        expect(warnings).toEqual(['race'])
    })

    it('mahnt bei geerbtem RaceClocker ohne Anwahl trotzdem an', () => {
        // Sonst bliebe der einzige Hinweis auf eine halbfertige Veranstaltungs-Vorgabe aus, weil
        // der Wettkampf selbst „nichts gesetzt" hat.
        const warnings = timingConfigWarnings({
            ...emptyTimingForm,
            eventTimingSystem: 'RACECLOCKER',
            startlistConfig: {id: startlistPreset, label: 'Läufe'},
        })

        expect(warnings).toEqual(['race'])
    })

    it('mahnt einen Startlisten Export nicht an, der von der Veranstaltung kommt', () => {
        const warnings = timingConfigWarnings({
            ...emptyTimingForm,
            eventTimingSystem: 'RACECLOCKER',
            // Rennen ist Pflicht pro Wettkampf; der Startlisten-Export erbt von der Veranstaltung.
            race: {id: shortCourseRace, label: 'Kurzstrecke'},
            eventStartlistConfig: startlistPreset,
        })

        expect(warnings).toEqual([])
    })

    it('mahnt das Startlisten-Preset an, auch bei Webscorer', () => {
        const warnings = timingConfigWarnings({...emptyTimingForm, timingSystem: 'WEBSCORER'})

        expect(warnings).toEqual(['startlist'])
    })

    it('mahnt das Rennen bei Webscorer nicht an', () => {
        // Webscorer hat keinen Feed, den man abrufen könnte - ein RaceClocker-Rennen wird dort
        // nie gebraucht.
        const warnings = timingConfigWarnings({
            ...emptyTimingForm,
            timingSystem: 'WEBSCORER',
            startlistConfig: {id: startlistPreset, label: 'Läufe'},
        })

        expect(warnings).toEqual([])
    })

    it('schweigt, wenn alles eingerichtet ist', () => {
        const warnings = timingConfigWarnings({
            ...emptyTimingForm,
            timingSystem: 'RACECLOCKER',
            race: {id: shortCourseRace, label: 'Kurzstrecke'},
            startlistConfig: {id: startlistPreset, label: 'Läufe'},
            resultImportConfig: {id: importPreset, label: 'Webscorer xlsx'},
        })

        expect(warnings).toEqual([])
    })
})

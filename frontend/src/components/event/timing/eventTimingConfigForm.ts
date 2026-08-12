import {EventTimingConfigDto, EventTimingConfigRequest, TimingSystem} from '@api/types.gen.ts'
import {AutocompleteOption} from '@utils/types.ts'

/** Wie im Wettkampf-Formular: „nicht gesetzt" ist im Radio ein Wert, im Request null. */
export type EventTimingFormSystem = TimingSystem | 'NONE'

export type EventTimingForm = {
    timingSystem: EventTimingFormSystem
    /**
     * Startlisten-Export und Rennergebnisse-Import wie im Wettkampf. Sie stehen hier, weil alle
     * Wettkämpfe einer Regatta dieselben Spalten brauchen; abweichende Bootsklassen scheren im
     * Wettkampf aus. Seit dem 11.08.2026 gibt es nur noch EIN Startlisten-Preset — RaceClocker
     * kennt keine Startarten mehr, also braucht die Qualifikation kein eigenes. Die
     * RaceClocker-Rennen werden pro Wettkampf zugewiesen (RaceClockerRaceAssignments) — die
     * Veranstaltung hat dafür keine Voreinstellung.
     */
    startlistConfig: AutocompleteOption
    resultImportConfig: AutocompleteOption
    /**
     * Der automatische Abruf. Nur bei RaceClocker sichtbar und speicherbar — Webscorer hat keinen
     * Ergebnis-Feed, den ein Job abholen könnte.
     */
    autoPull: boolean
    intervalActiveSeconds: number
    intervalUpcomingSeconds: number
    watchBeforeMinutes: number
    watchAfterMinutes: number
}

export const emptyEventTimingForm: EventTimingForm = {
    timingSystem: 'NONE',
    startlistConfig: null,
    resultImportConfig: null,
    autoPull: false,
    intervalActiveSeconds: 5,
    intervalUpcomingSeconds: 60,
    watchBeforeMinutes: 15,
    watchAfterMinutes: 120,
}

export const mapDtoToEventTimingForm = (dto: EventTimingConfigDto): EventTimingForm => ({
    timingSystem: dto.timingSystem ?? 'NONE',
    // Wie im Wettkampf-Formular: nur die ID, das Label füllt die Komponente aus den geladenen Listen.
    startlistConfig: dto.startlistConfig ? {id: dto.startlistConfig, label: ''} : null,
    resultImportConfig: dto.resultImportConfig ? {id: dto.resultImportConfig, label: ''} : null,
    autoPull: dto.autoPull,
    intervalActiveSeconds: dto.intervalActiveSeconds,
    intervalUpcomingSeconds: dto.intervalUpcomingSeconds,
    watchBeforeMinutes: dto.watchBeforeMinutes,
    watchAfterMinutes: dto.watchAfterMinutes,
})

/**
 * Verwirft, was für das gewählte System nicht sichtbar ist — dieselbe Regel wie im Wettkampf: eine
 * unsichtbare Voreinstellung, die stillschweigend an alle Wettkämpfe vererbt wird, wäre die
 * unangenehmste Variante einer vergessenen Einstellung.
 */
export const mapEventTimingFormToRequest = (form: EventTimingForm): EventTimingConfigRequest => {
    const raceClocker = form.timingSystem === 'RACECLOCKER'
    const configured = form.timingSystem !== 'NONE'

    return {
        timingSystem: form.timingSystem === 'NONE' ? null : form.timingSystem,
        startlistConfig: configured ? (form.startlistConfig?.id ?? null) : null,
        resultImportConfig: configured ? (form.resultImportConfig?.id ?? null) : null,
        // Die Takte werden immer mitgeschickt: Sie haben in der Datenbank eine Vorgabe, und ein
        // Abschalten des Systems soll die eingestellten Werte nicht verlieren.
        autoPull: raceClocker && form.autoPull,
        intervalActiveSeconds: form.intervalActiveSeconds,
        intervalUpcomingSeconds: form.intervalUpcomingSeconds,
        watchBeforeMinutes: form.watchBeforeMinutes,
        watchAfterMinutes: form.watchAfterMinutes,
    }
}

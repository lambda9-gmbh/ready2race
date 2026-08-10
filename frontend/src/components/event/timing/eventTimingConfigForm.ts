import {EventTimingConfigDto, EventTimingConfigRequest, TimingSystem} from '@api/types.gen.ts'
import {AutocompleteOption} from '@utils/types.ts'

/** Wie im Wettkampf-Formular: „nicht gesetzt" ist im Radio ein Wert, im Request null. */
export type EventTimingFormSystem = TimingSystem | 'NONE'

export type EventTimingForm = {
    timingSystem: EventTimingFormSystem
    /**
     * Das voreingestellte RaceClocker-Rennen je Rundenart. Angewählt statt eingetippt: die Rennen
     * gehören der Veranstaltung und tragen ihre Adresse genau einmal.
     */
    raceQualification: AutocompleteOption
    raceRounds: AutocompleteOption
    /**
     * Startlisten-Export und Rennergebnisse-Import wie im Wettkampf (Migration V202608071300). Sie
     * stehen hier, weil alle Wettkämpfe einer Regatta in dieselben Rennen im Fremdsystem exportieren
     * und deshalb dieselben Spalten brauchen; abweichende Bootsklassen scheren im Wettkampf aus.
     */
    startlistConfigQualification: AutocompleteOption
    startlistConfigRounds: AutocompleteOption
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
    raceQualification: null,
    raceRounds: null,
    startlistConfigQualification: null,
    startlistConfigRounds: null,
    resultImportConfig: null,
    autoPull: false,
    intervalActiveSeconds: 5,
    intervalUpcomingSeconds: 60,
    watchBeforeMinutes: 15,
    watchAfterMinutes: 120,
}

export const mapDtoToEventTimingForm = (dto: EventTimingConfigDto): EventTimingForm => ({
    timingSystem: dto.timingSystem ?? 'NONE',
    raceQualification: dto.raceQualification ? {id: dto.raceQualification, label: ''} : null,
    raceRounds: dto.raceRounds ? {id: dto.raceRounds, label: ''} : null,
    // Wie im Wettkampf-Formular: nur die ID, das Label füllt die Komponente aus den geladenen Listen.
    startlistConfigQualification: dto.startlistConfigQualification
        ? {id: dto.startlistConfigQualification, label: ''}
        : null,
    startlistConfigRounds: dto.startlistConfigRounds
        ? {id: dto.startlistConfigRounds, label: ''}
        : null,
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
        raceQualification: raceClocker ? (form.raceQualification?.id ?? null) : null,
        raceRounds: raceClocker ? (form.raceRounds?.id ?? null) : null,
        // Nur RaceClocker kennt die Zweiteilung Zeitfahren/Läufe; Webscorer füllt allein den Runden-Slot.
        startlistConfigQualification: raceClocker
            ? (form.startlistConfigQualification?.id ?? null)
            : null,
        startlistConfigRounds: configured ? (form.startlistConfigRounds?.id ?? null) : null,
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

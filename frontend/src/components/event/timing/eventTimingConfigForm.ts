import {EventTimingConfigDto, EventTimingConfigRequest, TimingSystem} from '@api/types.gen.ts'
import {AutocompleteOption} from '@utils/types.ts'

/** Wie im Wettkampf-Formular: „nicht gesetzt" ist im Radio ein Wert, im Request null. */
export type EventTimingFormSystem = TimingSystem | 'NONE'

export type EventTimingForm = {
    timingSystem: EventTimingFormSystem
    timeTrialResultsUrl: string
    heatsResultsUrl: string
    /**
     * Startlisten-Export und Rennergebnisse-Import wie im Wettkampf (Migration V202608071300). Sie
     * stehen hier, weil alle Wettkämpfe einer Regatta in dieselben Rennen im Fremdsystem exportieren
     * und deshalb dieselben Spalten brauchen; abweichende Bootsklassen scheren im Wettkampf aus.
     */
    startlistConfigQualification: AutocompleteOption
    startlistConfigRounds: AutocompleteOption
    resultImportConfig: AutocompleteOption
}

export const emptyEventTimingForm: EventTimingForm = {
    timingSystem: 'NONE',
    timeTrialResultsUrl: '',
    heatsResultsUrl: '',
    startlistConfigQualification: null,
    startlistConfigRounds: null,
    resultImportConfig: null,
}

export const mapDtoToEventTimingForm = (dto: EventTimingConfigDto): EventTimingForm => ({
    timingSystem: dto.timingSystem ?? 'NONE',
    timeTrialResultsUrl: dto.timeTrialResultsUrl ?? '',
    heatsResultsUrl: dto.heatsResultsUrl ?? '',
    // Wie im Wettkampf-Formular: nur die ID, das Label füllt die Komponente aus den geladenen Listen.
    startlistConfigQualification: dto.startlistConfigQualification
        ? {id: dto.startlistConfigQualification, label: ''}
        : null,
    startlistConfigRounds: dto.startlistConfigRounds
        ? {id: dto.startlistConfigRounds, label: ''}
        : null,
    resultImportConfig: dto.resultImportConfig ? {id: dto.resultImportConfig, label: ''} : null,
})

const trimmedOrNull = (value: string): string | null => value.trim() || null

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
        timeTrialResultsUrl: raceClocker ? trimmedOrNull(form.timeTrialResultsUrl) : null,
        heatsResultsUrl: raceClocker ? trimmedOrNull(form.heatsResultsUrl) : null,
        // Nur RaceClocker kennt die Zweiteilung Zeitfahren/Läufe; Webscorer füllt allein den Runden-Slot.
        startlistConfigQualification: raceClocker
            ? (form.startlistConfigQualification?.id ?? null)
            : null,
        startlistConfigRounds: configured ? (form.startlistConfigRounds?.id ?? null) : null,
        resultImportConfig: configured ? (form.resultImportConfig?.id ?? null) : null,
    }
}

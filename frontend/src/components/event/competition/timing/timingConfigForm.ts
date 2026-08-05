import {TimingConfigDto, TimingConfigRequest} from '@api/types.gen.ts'
import {AutocompleteOption} from '@utils/types.ts'

/**
 * 'NONE' ist der Formular-Stellvertreter für „kein Zeitnahme-System gesetzt“ (Spalte null). Ein
 * Radio-Button braucht einen Wert; null lässt sich nicht auswählen.
 */
export type TimingFormSystem = 'NONE' | 'RACECLOCKER' | 'WEBSCORER'

export type TimingForm = {
    timingSystem: TimingFormSystem
    timeTrialResultsUrl: string
    heatsResultsUrl: string
    startlistConfigQualification: AutocompleteOption
    startlistConfigRounds: AutocompleteOption
    resultImportConfig: AutocompleteOption
}

export const emptyTimingForm: TimingForm = {
    timingSystem: 'NONE',
    timeTrialResultsUrl: '',
    heatsResultsUrl: '',
    startlistConfigQualification: null,
    startlistConfigRounds: null,
    resultImportConfig: null,
}

/**
 * Die Preset-Felder kommen als reine UUID aus dem Backend. Das Label füllt die Komponente nach, sobald
 * die Preset-Listen geladen sind — hier steht nur die ID, damit diese Funktion ohne Netz testbar bleibt.
 */
export const mapDtoToTimingForm = (dto: TimingConfigDto): TimingForm => ({
    timingSystem: dto.timingSystem ?? 'NONE',
    timeTrialResultsUrl: dto.timeTrialResultsUrl ?? '',
    heatsResultsUrl: dto.heatsResultsUrl ?? '',
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
 * Felder, die für das gewählte System nicht sichtbar sind, werden bewusst geleert statt durchgereicht:
 * eine unsichtbare URL oder ein unsichtbares Preset wäre eine Einstellung, die niemand mehr findet.
 */
export const mapTimingFormToRequest = (form: TimingForm): TimingConfigRequest => {
    const raceClocker = form.timingSystem === 'RACECLOCKER'

    return {
        timingSystem: form.timingSystem === 'NONE' ? null : form.timingSystem,
        timeTrialResultsUrl: raceClocker ? trimmedOrNull(form.timeTrialResultsUrl) : null,
        heatsResultsUrl: raceClocker ? trimmedOrNull(form.heatsResultsUrl) : null,
        startlistConfigQualification: raceClocker
            ? (form.startlistConfigQualification?.id ?? null)
            : null,
        startlistConfigRounds: form.startlistConfigRounds?.id ?? null,
        resultImportConfig: form.resultImportConfig?.id ?? null,
    }
}

export type TimingWarning = 'heatsUrl' | 'startlistRounds'

/**
 * Was fehlt, um die Zeitnahme benutzen zu können.
 *
 * Bewusst NICHT dabei: die Zeitfahren-URL und das Qualifikations-Preset. Ein Wettkampf ohne
 * Qualifikationsrunde braucht beides nie, und eine Warnung, die dort dauerhaft steht, wird ignoriert.
 */
export const timingConfigWarnings = (form: TimingForm): TimingWarning[] => {
    if (form.timingSystem === 'NONE') return []

    const warnings: TimingWarning[] = []
    if (form.timingSystem === 'RACECLOCKER' && !form.heatsResultsUrl.trim()) {
        warnings.push('heatsUrl')
    }
    if (!form.startlistConfigRounds) {
        warnings.push('startlistRounds')
    }
    return warnings
}

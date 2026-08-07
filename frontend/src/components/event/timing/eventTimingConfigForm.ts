import {EventTimingConfigDto, EventTimingConfigRequest, TimingSystem} from '@api/types.gen.ts'

/** Wie im Wettkampf-Formular: „nicht gesetzt" ist im Radio ein Wert, im Request null. */
export type EventTimingFormSystem = TimingSystem | 'NONE'

export type EventTimingForm = {
    timingSystem: EventTimingFormSystem
    timeTrialResultsUrl: string
    heatsResultsUrl: string
}

export const emptyEventTimingForm: EventTimingForm = {
    timingSystem: 'NONE',
    timeTrialResultsUrl: '',
    heatsResultsUrl: '',
}

export const mapDtoToEventTimingForm = (dto: EventTimingConfigDto): EventTimingForm => ({
    timingSystem: dto.timingSystem ?? 'NONE',
    timeTrialResultsUrl: dto.timeTrialResultsUrl ?? '',
    heatsResultsUrl: dto.heatsResultsUrl ?? '',
})

const trimmedOrNull = (value: string): string | null => value.trim() || null

/**
 * Verwirft die Adressen, sobald nicht RaceClocker gewählt ist — dieselbe Regel wie im Wettkampf:
 * eine unsichtbare Voreinstellung, die stillschweigend an alle Wettkämpfe vererbt wird, wäre die
 * unangenehmste Variante einer vergessenen Einstellung.
 */
export const mapEventTimingFormToRequest = (form: EventTimingForm): EventTimingConfigRequest => {
    const raceClocker = form.timingSystem === 'RACECLOCKER'

    return {
        timingSystem: form.timingSystem === 'NONE' ? null : form.timingSystem,
        timeTrialResultsUrl: raceClocker ? trimmedOrNull(form.timeTrialResultsUrl) : null,
        heatsResultsUrl: raceClocker ? trimmedOrNull(form.heatsResultsUrl) : null,
    }
}

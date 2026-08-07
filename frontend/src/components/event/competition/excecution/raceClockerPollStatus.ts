import {ExecutionErrorKey, raceClockerKeys} from './executionError.ts'

export type RaceClockerPollStatus = {
    kind: 'none' | 'ok' | 'error' | 'paused'
    errorKey?: ExecutionErrorKey
}

type PollFields = {
    raceClockerPolledAt?: string | null
    raceClockerPollError?: string | null
    raceClockerAutoPausedAt?: string | null
}

/**
 * Die Fehlercodes des automatischen Abrufs auf dieselben Texte wie beim Knopf — der Job scheitert
 * an denselben Dingen, es gibt keinen Grund, das zweimal zu formulieren.
 */
const errorKeyFor = (code: string): ExecutionErrorKey | undefined => {
    switch (code) {
        case 'RACECLOCKER_URL_MISSING':
            return raceClockerKeys.urlMissing
        case 'RACECLOCKER_URL_INVALID':
            return raceClockerKeys.urlInvalid
        case 'RACECLOCKER_UNREACHABLE':
        case 'RACECLOCKER_MALFORMED_FEED':
            return raceClockerKeys.unreachable
        case 'RACECLOCKER_MATCH_NOT_IN_FEED':
            return raceClockerKeys.matchNotInFeed
        case 'RACECLOCKER_DUPLICATE_TEAMS':
            return raceClockerKeys.duplicateTeams
        case 'RACECLOCKER_NO_RESULTS':
            return raceClockerKeys.noResults
        case 'RACECLOCKER_MATCH_IS_BYE':
            return raceClockerKeys.matchIsBye
    }

    return undefined
}

/**
 * Was die Oberfläche über den automatischen Abruf dieses Laufs sagt.
 *
 * Die Reihenfolge ist die Aussage: „pausiert" schlägt alles, weil ein pausierter Lauf gar nicht
 * mehr abgerufen wird — ein Fehler von davor wäre dann eine Behauptung über etwas, das nicht mehr
 * stattfindet. „Nie abgerufen" ist kein Fehler: Vor dem ersten Takt und bei ausgeschalteter
 * Automatik ist genau das der richtige Zustand.
 */
export const raceClockerPollStatus = (match: PollFields): RaceClockerPollStatus => {
    if (match.raceClockerAutoPausedAt) {
        return {kind: 'paused'}
    }

    if (match.raceClockerPollError) {
        return {kind: 'error', errorKey: errorKeyFor(match.raceClockerPollError)}
    }

    if (match.raceClockerPolledAt) {
        return {kind: 'ok'}
    }

    return {kind: 'none'}
}

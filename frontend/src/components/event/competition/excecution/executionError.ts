import {ErrorCode} from '@api/types.gen.ts'

/**
 * Die Fehlermeldungen der Durchführung: Ummeldungen, Challenge-Ergebnisse und die vier Gründe der
 * Ergebniserfassung, die ein Nutzer regelmäßig auslöst. Sie landeten bisher durchweg in
 * Sammelmeldungen ("Beim Speichern ist ein Fehler aufgetreten"), obwohl sie Verschiedenes
 * verlangen - bei den Ummeldungen kam wegen vertauschter i18n-Schlüssel in de/da stattdessen sogar
 * der rohe Schlüssel in der Oberfläche an.
 *
 * Jede Funktion liefert `undefined`, wenn der Grund unbekannt ist. Die aufrufende Stelle bleibt
 * dann bei ihrer eigenen Sammelmeldung, die immerhin schon sagt, welche Aktion misslang.
 */

const substitutionKeys = {
    notFound: 'event.competition.execution.substitution.error.notFound',
    participantOutNotFound: 'event.competition.execution.substitution.error.participantOutNotFound',
    participantInNotFound: 'event.competition.execution.substitution.error.participantInNotFound',
    participantOutNotAvailable:
        'event.competition.execution.substitution.error.participantOutNotAvailable',
    participantInNotAvailable:
        'event.competition.execution.substitution.error.participantInNotAvailable',
    dependentFound: 'event.competition.execution.substitution.error.dependentFound',
    createdInPreviousRound: 'event.competition.execution.substitution.error.createdInPreviousRound',
} as const

const challengeKeys = {
    notAChallengeEvent: 'event.competition.execution.results.challenge.error.notAChallengeEvent',
    alreadyStarted: 'event.competition.execution.results.challenge.error.alreadyStarted',
    notStartedYet: 'event.competition.execution.results.challenge.error.notStartedYet',
    corruptedSetup: 'event.competition.execution.results.challenge.error.corruptedSetup',
    resultAlreadySubmitted:
        'event.competition.execution.results.challenge.error.resultAlreadySubmitted',
    noResultSubmitted: 'event.competition.execution.results.challenge.error.noResultSubmitted',
    selfSubmissionNotAllowed:
        'event.competition.execution.results.challenge.error.selfSubmissionNotAllowed',
} as const

const matchKeys = {
    resultsLocked: 'event.competition.execution.error.matchResultsLocked',
    isBye: 'event.competition.execution.error.matchIsBye',
    placesNotContinuous: 'event.competition.execution.error.placesNotContinuous',
    startTimeManagedBySchedule: 'event.competition.execution.error.startTimeManagedBySchedule',
    teamsNotMatching: 'event.competition.execution.error.teamsNotMatching',
} as const

export const raceClockerKeys = {
    urlMissing: 'event.competition.execution.results.raceclocker.error.urlMissing',
    urlInvalid: 'event.competition.execution.results.raceclocker.error.urlInvalid',
    unreachable: 'event.competition.execution.results.raceclocker.error.unreachable',
    matchNotInFeed: 'event.competition.execution.results.raceclocker.error.matchNotInFeed',
    duplicateTeams: 'event.competition.execution.results.raceclocker.error.duplicateTeams',
    noResults: 'event.competition.execution.results.raceclocker.error.noResults',
    matchIsBye: 'event.competition.execution.results.raceclocker.error.matchIsBye',
} as const

// Separate key for poll duplicate teams — the button's message has {{teams}} with crew names from the API,
// but the background job stores only the error code without details, so no teams list is available.
export const raceClockerPollKeys = {
    duplicateTeams: 'event.competition.execution.results.raceclocker.poll.duplicateTeams',
} as const

export type ExecutionErrorKey =
    | (typeof substitutionKeys)[keyof typeof substitutionKeys]
    | (typeof challengeKeys)[keyof typeof challengeKeys]
    | (typeof matchKeys)[keyof typeof matchKeys]
    | (typeof raceClockerKeys)[keyof typeof raceClockerKeys]
    | (typeof raceClockerPollKeys)[keyof typeof raceClockerPollKeys]

/** Ein übersetzbarer Meldungstext: i18n-Key plus die Werte, die er einsetzt. */
export type ExecutionErrorText = {
    key: ExecutionErrorKey
    values?: Record<string, string | number>
}

/** Was von einer Fehlerantwort hier gebraucht wird — unabhängig vom konkreten SDK-Fehlertyp. */
export type ExecutionApiError = {
    message: string
    errorCode?: ErrorCode
    details?: unknown
}

const asNumber = (value: unknown): number | undefined =>
    typeof value === 'number' ? value : undefined

const asStringArray = (value: unknown): string[] =>
    Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : []

/** Der i18n-Key zur abgelehnten Ummeldung, oder `undefined` für Unbekanntes. */
export const substitutionErrorKey = (error: ExecutionApiError): ExecutionErrorKey | undefined => {
    switch (error.errorCode) {
        case 'SUBSTITUTION_NOT_FOUND':
            return substitutionKeys.notFound
        case 'SUBSTITUTION_PARTICIPANT_OUT_NOT_FOUND':
            return substitutionKeys.participantOutNotFound
        case 'SUBSTITUTION_PARTICIPANT_IN_NOT_FOUND':
            return substitutionKeys.participantInNotFound
        case 'SUBSTITUTION_PARTICIPANT_OUT_NOT_AVAILABLE':
            return substitutionKeys.participantOutNotAvailable
        case 'SUBSTITUTION_PARTICIPANT_IN_NOT_AVAILABLE':
            return substitutionKeys.participantInNotAvailable
        case 'SUBSTITUTION_DEPENDENT_FOUND':
            return substitutionKeys.dependentFound
        case 'SUBSTITUTION_CREATED_IN_PREVIOUS_ROUND':
            return substitutionKeys.createdInPreviousRound
    }

    return undefined
}

/** Der i18n-Key zur abgelehnten Challenge-Aktion, oder `undefined` für Unbekanntes. */
export const challengeErrorKey = (error: ExecutionApiError): ExecutionErrorKey | undefined => {
    switch (error.errorCode) {
        case 'CHALLENGE_NOT_A_CHALLENGE_EVENT':
            return challengeKeys.notAChallengeEvent
        case 'CHALLENGE_ALREADY_STARTED':
            return challengeKeys.alreadyStarted
        case 'CHALLENGE_NOT_STARTED_YET':
            return challengeKeys.notStartedYet
        case 'CHALLENGE_CORRUPTED_SETUP':
            return challengeKeys.corruptedSetup
        case 'CHALLENGE_RESULT_ALREADY_SUBMITTED':
            return challengeKeys.resultAlreadySubmitted
        case 'CHALLENGE_NO_RESULT_SUBMITTED':
            return challengeKeys.noResultSubmitted
        case 'CHALLENGE_SELF_SUBMISSION_NOT_ALLOWED':
            return challengeKeys.selfSubmissionNotAllowed
    }

    return undefined
}

/**
 * Der Meldungstext zu einer abgelehnten Ergebnis- oder Laufdaten-Eingabe, oder `undefined` für
 * Unbekanntes. Bei einer Lücke in den Plätzen reisen der erwartete und der eingetragene Platz mit
 * — sonst muss der Nutzer die Liste selbst durchzählen, um die Lücke zu finden.
 */
export const matchErrorText = (error: ExecutionApiError): ExecutionErrorText | undefined => {
    switch (error.errorCode) {
        case 'EXECUTION_MATCH_RESULTS_LOCKED':
            return {key: matchKeys.resultsLocked}

        case 'EXECUTION_MATCH_IS_BYE':
            return {key: matchKeys.isBye}

        case 'EXECUTION_START_TIME_MANAGED_BY_SCHEDULE':
            return {key: matchKeys.startTimeManagedBySchedule}

        case 'EXECUTION_TEAMS_NOT_MATCHING':
            return {key: matchKeys.teamsNotMatching}

        case 'EXECUTION_PLACES_NOT_CONTINUOUS': {
            const details = (error.details as Record<string, unknown> | undefined) ?? {}
            return {
                key: matchKeys.placesNotContinuous,
                values: {
                    expected: asNumber(details.expected) ?? 0,
                    actual: asNumber(details.actual) ?? 0,
                },
            }
        }
    }

    return undefined
}

/**
 * Der Meldungstext zu einem abgelehnten RaceClocker-Ergebnis-Pull, oder `undefined` für Unbekanntes.
 * Die Codes stammen aus RaceClockerError.respond() im Backend; Unreachable und MalformedFeed teilen
 * sich hier bewusst einen Text ("Feed konnte nicht gelesen werden") - für das Schiedsgericht am Steg
 * verlangen beide dieselbe Handlung: URL und Verbindung prüfen, notfalls in RaceClocker nachsehen.
 */
export const raceClockerErrorText = (error: ExecutionApiError): ExecutionErrorText | undefined => {
    switch (error.errorCode) {
        case 'RACECLOCKER_URL_MISSING':
            return {key: raceClockerKeys.urlMissing}

        case 'RACECLOCKER_URL_INVALID':
            return {key: raceClockerKeys.urlInvalid}

        case 'RACECLOCKER_UNREACHABLE':
        case 'RACECLOCKER_MALFORMED_FEED':
            return {key: raceClockerKeys.unreachable}

        case 'RACECLOCKER_MATCH_NOT_IN_FEED': {
            // Der Rennenname statt der nackten Adresse: „im Rennen Kurzstrecke nicht gefunden" ist
            // am Renntag eine Handlungsanweisung, eine URL ist es nicht.
            const details = (error.details as Record<string, unknown> | undefined) ?? {}
            return {
                key: raceClockerKeys.matchNotInFeed,
                values: {races: asStringArray(details.races).join(', ')},
            }
        }

        case 'RACECLOCKER_DUPLICATE_TEAMS': {
            const details = (error.details as Record<string, unknown> | undefined) ?? {}
            return {
                key: raceClockerKeys.duplicateTeams,
                values: {teams: asStringArray(details.teams).join(', ')},
            }
        }

        case 'RACECLOCKER_NO_RESULTS':
            return {key: raceClockerKeys.noResults}

        case 'RACECLOCKER_MATCH_IS_BYE':
            return {key: raceClockerKeys.matchIsBye}
    }

    return undefined
}

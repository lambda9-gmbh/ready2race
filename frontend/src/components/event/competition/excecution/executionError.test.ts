import {describe, expect, it} from 'vitest'
import {
    ExecutionApiError,
    challengeErrorKey,
    matchErrorText,
    substitutionErrorKey,
} from './executionError.ts'
import deTranslations from '@i18n/de/translations.json'
import enTranslations from '@i18n/en/translations.json'
import daTranslations from '@i18n/da/translations.json'

const error = (partial: Partial<ExecutionApiError>): ExecutionApiError => ({
    message: 'irgendein englischer Backend-Text',
    ...partial,
})

const lookup = (translations: object, key: string): unknown =>
    key.split('.').reduce<unknown>((node, part) => (node as Record<string, unknown>)?.[part], translations)

describe('substitutionErrorKey', () => {
    it.each([
        ['SUBSTITUTION_NOT_FOUND', 'notFound'],
        ['SUBSTITUTION_PARTICIPANT_OUT_NOT_FOUND', 'participantOutNotFound'],
        ['SUBSTITUTION_PARTICIPANT_IN_NOT_FOUND', 'participantInNotFound'],
        ['SUBSTITUTION_PARTICIPANT_OUT_NOT_AVAILABLE', 'participantOutNotAvailable'],
        ['SUBSTITUTION_PARTICIPANT_IN_NOT_AVAILABLE', 'participantInNotAvailable'],
        ['SUBSTITUTION_DEPENDENT_FOUND', 'dependentFound'],
        ['SUBSTITUTION_CREATED_IN_PREVIOUS_ROUND', 'createdInPreviousRound'],
    ] as const)('bildet %s auf einen eigenen Text ab', (errorCode, leaf) => {
        expect(substitutionErrorKey(error({errorCode}))).toBe(
            `event.competition.execution.substitution.error.${leaf}`,
        )
    })

    it('trennt den auszutauschenden Teilnehmer vom Ersatz', () => {
        expect(substitutionErrorKey(error({errorCode: 'SUBSTITUTION_PARTICIPANT_OUT_NOT_AVAILABLE'})))
            .not.toBe(
                substitutionErrorKey(error({errorCode: 'SUBSTITUTION_PARTICIPANT_IN_NOT_AVAILABLE'})),
            )
    })

    it('überlässt Unbekanntes der Sammelmeldung der Aktion', () => {
        expect(substitutionErrorKey(error({}))).toBeUndefined()
    })
})

describe('challengeErrorKey', () => {
    it.each([
        ['CHALLENGE_NOT_A_CHALLENGE_EVENT', 'notAChallengeEvent'],
        ['CHALLENGE_ALREADY_STARTED', 'alreadyStarted'],
        ['CHALLENGE_NOT_STARTED_YET', 'notStartedYet'],
        ['CHALLENGE_CORRUPTED_SETUP', 'corruptedSetup'],
        ['CHALLENGE_RESULT_ALREADY_SUBMITTED', 'resultAlreadySubmitted'],
        ['CHALLENGE_NO_RESULT_SUBMITTED', 'noResultSubmitted'],
        ['CHALLENGE_SELF_SUBMISSION_NOT_ALLOWED', 'selfSubmissionNotAllowed'],
    ] as const)('bildet %s auf einen eigenen Text ab', (errorCode, leaf) => {
        expect(challengeErrorKey(error({errorCode}))).toBe(
            `event.competition.execution.results.challenge.error.${leaf}`,
        )
    })

    it('unterscheidet "noch nicht begonnen" von "schon begonnen"', () => {
        // Beide teilten sich die eine Sammelmeldung, obwohl der eine Fall zum Warten auffordert
        // und der andere sagt, dass der Zug abgefahren ist.
        expect(challengeErrorKey(error({errorCode: 'CHALLENGE_NOT_STARTED_YET'}))).not.toBe(
            challengeErrorKey(error({errorCode: 'CHALLENGE_ALREADY_STARTED'})),
        )
    })

    it('überlässt Unbekanntes der Sammelmeldung der Aktion', () => {
        expect(challengeErrorKey(error({}))).toBeUndefined()
    })
})

describe('matchErrorText', () => {
    it('nennt den erwarteten und den eingetragenen Platz', () => {
        expect(
            matchErrorText(
                error({
                    errorCode: 'EXECUTION_PLACES_NOT_CONTINUOUS',
                    details: {expected: 3, actual: 4},
                }),
            ),
        ).toEqual({
            key: 'event.competition.execution.error.placesNotContinuous',
            values: {expected: 3, actual: 4},
        })
    })

    it('trennt das Freilos von der gesperrten früheren Runde', () => {
        // Beides kam bis zuletzt als MatchResultsLocked an und las sich damit als "nur die
        // aktuelle Runde ist bearbeitbar" - bei einem Freilos die falsche Fährte.
        expect(matchErrorText(error({errorCode: 'EXECUTION_MATCH_IS_BYE'}))).toEqual({
            key: 'event.competition.execution.error.matchIsBye',
        })
        expect(matchErrorText(error({errorCode: 'EXECUTION_MATCH_RESULTS_LOCKED'}))).toEqual({
            key: 'event.competition.execution.error.matchResultsLocked',
        })
    })

    it.each([
        ['EXECUTION_START_TIME_MANAGED_BY_SCHEDULE', 'startTimeManagedBySchedule'],
        ['EXECUTION_TEAMS_NOT_MATCHING', 'teamsNotMatching'],
    ] as const)('bildet %s auf einen eigenen Text ab', (errorCode, leaf) => {
        expect(matchErrorText(error({errorCode}))).toEqual({
            key: `event.competition.execution.error.${leaf}`,
        })
    })

    it('überlässt Unbekanntes der Sammelmeldung der Maske', () => {
        expect(matchErrorText(error({}))).toBeUndefined()
    })
})

describe('Übersetzungen', () => {
    const keys = [
        'event.competition.execution.substitution.error.notFound',
        'event.competition.execution.substitution.error.participantOutNotFound',
        'event.competition.execution.substitution.error.participantInNotFound',
        'event.competition.execution.substitution.error.participantOutNotAvailable',
        'event.competition.execution.substitution.error.participantInNotAvailable',
        'event.competition.execution.substitution.error.dependentFound',
        'event.competition.execution.substitution.error.createdInPreviousRound',
        'event.competition.execution.results.challenge.error.notAChallengeEvent',
        'event.competition.execution.results.challenge.error.alreadyStarted',
        'event.competition.execution.results.challenge.error.notStartedYet',
        'event.competition.execution.results.challenge.error.corruptedSetup',
        'event.competition.execution.results.challenge.error.resultAlreadySubmitted',
        'event.competition.execution.results.challenge.error.noResultSubmitted',
        'event.competition.execution.results.challenge.error.selfSubmissionNotAllowed',
        'event.competition.execution.results.challenge.error.unexpected',
        'event.competition.execution.error.matchResultsLocked',
        'event.competition.execution.error.matchIsBye',
        'event.competition.execution.error.placesNotContinuous',
        'event.competition.execution.error.startTimeManagedBySchedule',
        'event.competition.execution.error.teamsNotMatching',
        // Die Sammelmeldungen, auf die der jeweilige Aufrufer zurückfällt. In de/da waren
        // add.error und delete.error vertauscht (Objekt statt String und umgekehrt), sodass der
        // rohe Schlüssel in der Oberfläche stand - genau das fängt diese Prüfung ab.
        'event.competition.execution.substitution.add.error',
        'event.competition.execution.substitution.delete.error',
        'event.competition.execution.results.submit.error',
        'event.competition.execution.matchData.submit.error',
    ]

    it.each(keys)('hat einen deutschen Text für %s', key => {
        expect(typeof lookup(deTranslations, key)).toBe('string')
    })

    it.each(keys)('hat auch einen englischen und dänischen Text für %s', key => {
        expect(typeof lookup(enTranslations, key)).toBe('string')
        expect(typeof lookup(daTranslations, key)).toBe('string')
    })
})

import {ErrorCode} from '@api/types.gen.ts'

/**
 * Die Fehlermeldungen der Ergebnisliste. Der Server nutzt dieselben ErrorCodes wie der
 * Siegerehrungsbogen — die Texte sind trotzdem eigene, denn „es gibt keine Siegerehrungsbögen"
 * wäre als Antwort auf einen Ergebnislisten-Download eine falsche Auskunft.
 */
const keys = {
    noResults: 'resultList.download.error.noResults',
    competitionNotInEvent: 'resultList.download.error.competitionNotInEvent',
    isChallengeEvent: 'resultList.download.error.isChallengeEvent',
    unexpected: 'resultList.download.error.unexpected',
} as const

export type ResultListErrorKey = (typeof keys)[keyof typeof keys]

/** Was von einer Fehlerantwort hier gebraucht wird — unabhängig vom konkreten SDK-Fehlertyp. */
export type ResultListApiError = {
    message: string
    errorCode?: ErrorCode
}

/**
 * Der i18n-Key zur abgelehnten Ergebnisliste. Bewusst nur der Key statt der fertigen Meldung,
 * damit die Zuordnung ohne i18n-Kontext testbar bleibt (dasselbe Muster wie awardCeremonyError.ts).
 */
export const resultListErrorKey = (error: ResultListApiError): ResultListErrorKey => {
    switch (error.errorCode) {
        case 'AWARD_CEREMONY_NO_RESULTS':
            return keys.noResults
        case 'AWARD_CEREMONY_COMPETITION_NOT_IN_EVENT':
            return keys.competitionNotInEvent
        case 'AWARD_CEREMONY_IS_CHALLENGE_EVENT':
            return keys.isChallengeEvent
    }

    return keys.unexpected
}

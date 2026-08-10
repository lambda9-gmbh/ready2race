import {ErrorCode} from '@api/types.gen.ts'

/**
 * Die Fehlermeldungen des Siegerehrungsbogens. Wie bei den Urkunden entscheidet der ErrorCode und
 * nicht der HTTP-Status: 400 trägt hier vier verschiedene Gründe, die für die Sprecherin am Steg
 * jeweils etwas anderes bedeuten - "es gibt noch keine Platzierungen" ist Warten, "die Wertung gibt
 * es nicht mehr" ist Neuladen.
 */
const keys = {
    noResults: 'awardCeremony.download.error.noResults',
    competitionNotInEvent: 'awardCeremony.download.error.competitionNotInEvent',
    unknownRatingCategory: 'awardCeremony.download.error.unknownRatingCategory',
    isChallengeEvent: 'awardCeremony.download.error.isChallengeEvent',
    unexpected: 'awardCeremony.download.error.unexpected',
} as const

export type AwardCeremonyErrorKey = (typeof keys)[keyof typeof keys]

/** Was von einer Fehlerantwort hier gebraucht wird — unabhängig vom konkreten SDK-Fehlertyp. */
export type AwardCeremonyApiError = {
    message: string
    errorCode?: ErrorCode
}

export const awardCeremonyUnexpectedKey = keys.unexpected

/**
 * Der i18n-Key zum abgelehnten Siegerehrungsbogen. Bewusst nur der Key statt der fertigen Meldung,
 * damit die Zuordnung ohne i18n-Kontext testbar bleibt (dasselbe Muster wie certificateError.ts).
 */
export const awardCeremonyErrorKey = (error: AwardCeremonyApiError): AwardCeremonyErrorKey => {
    switch (error.errorCode) {
        case 'AWARD_CEREMONY_NO_RESULTS':
            return keys.noResults
        case 'AWARD_CEREMONY_COMPETITION_NOT_IN_EVENT':
            return keys.competitionNotInEvent
        case 'AWARD_CEREMONY_UNKNOWN_RATING_CATEGORY':
            return keys.unknownRatingCategory
        case 'AWARD_CEREMONY_IS_CHALLENGE_EVENT':
            return keys.isChallengeEvent
    }

    return keys.unexpected
}

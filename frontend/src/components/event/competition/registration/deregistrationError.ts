import {DeregisterCompetitionRegistrationError} from '@api/types.gen.ts'

// Gründe, aus denen der Server eine Abmeldung ablehnt (CompetitionDeregistrationError), mit je
// eigener Meldung. Alles andere - auch ein Fehler ganz ohne Code, etwa ein Netzwerkabbruch - läuft
// in die allgemeine Meldung.
const messageKeys = {
    DEREGISTRATION_ALREADY_EXISTS:
        'event.competition.registration.deregister.error.DEREGISTRATION_ALREADY_EXISTS',
    DEREGISTRATION_RESULTS_ALREADY_EXIST:
        'event.competition.registration.deregister.error.DEREGISTRATION_RESULTS_ALREADY_EXIST',
    DEREGISTRATION_NOT_IN_CURRENT_ROUND:
        'event.competition.registration.deregister.error.DEREGISTRATION_NOT_IN_CURRENT_ROUND',
    DEREGISTRATION_REGISTRATION_STILL_OPEN:
        'event.competition.registration.deregister.error.DEREGISTRATION_REGISTRATION_STILL_OPEN',
} as const

const unexpectedKey = 'event.competition.registration.deregister.error.unexpected' as const

export type DeregistrationErrorKey = (typeof messageKeys)[keyof typeof messageKeys] | typeof unexpectedKey

/**
 * Der i18n-Key zur Fehlerantwort des Abmelde-Endpunkts. Bewusst nur der Key statt der fertigen
 * Meldung, damit die Zuordnung ohne i18n-Kontext testbar bleibt.
 */
export const deregistrationErrorKey = (
    error: DeregisterCompetitionRegistrationError,
): DeregistrationErrorKey => {
    const code = error.errorCode

    return code !== undefined && code in messageKeys
        ? messageKeys[code as keyof typeof messageKeys]
        : unexpectedKey
}

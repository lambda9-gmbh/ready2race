import {ErrorCode} from '@api/types.gen.ts'

/**
 * Die Fehlermeldungen der Anzeigen, die am Renntag draußen bedient werden: das
 * Schiedsrichter-Dashboard am Steg, die Bändchen-Ausgabe und das Check-in. Alle drei hatten bis
 * zuletzt keinen einzigen ErrorCode, und alle drei fassen Helfer an, die keinen Kontext haben und
 * niemanden fragen können.
 *
 * Die Funktionen liefern `undefined`, wenn der Grund unbekannt ist; die aufrufende Stelle bleibt
 * dann bei ihrer eigenen Sammelmeldung.
 */

const dashboardKeys = {
    finishReservedForOffice: 'event.liveDashboard.control.errorReason.finishReservedForOffice',
} as const

const qrAssignKeys = {
    alreadyInUse: 'qrAssign.errorReason.alreadyInUse',
} as const

const trackingKeys = {
    alreadyCheckedIn: 'club.participant.tracking.errorReason.alreadyCheckedIn',
    notCheckedIn: 'club.participant.tracking.errorReason.notCheckedIn',
    qrCodeNotAssociated: 'club.participant.tracking.errorReason.qrCodeNotAssociated',
    qrCodeNotFound: 'club.participant.tracking.errorReason.qrCodeNotFound',
    entryNotFound: 'club.participant.tracking.errorReason.entryNotFound',
    sequenceConflict: 'club.participant.tracking.errorReason.sequenceConflict',
    timestampCollision: 'club.participant.tracking.errorReason.timestampCollision',
} as const

export type LiveDashboardErrorKey =
    | (typeof dashboardKeys)[keyof typeof dashboardKeys]
    | (typeof qrAssignKeys)[keyof typeof qrAssignKeys]
    | (typeof trackingKeys)[keyof typeof trackingKeys]

/** Was von einer Fehlerantwort hier gebraucht wird — unabhängig vom konkreten SDK-Fehlertyp. */
export type LiveDashboardApiError = {
    message: string
    errorCode?: ErrorCode
}

/**
 * Der i18n-Key zur abgelehnten Dashboard-Aktion. Bislang gab es dafür genau einen Satz ("Der Lauf
 * konnte nicht geändert werden") - der häufigste Grund ist aber gar keine Störung: die
 * Veranstaltung läuft im Modus REGATTABUERO, dort beendet das Büro über den Zeitplan.
 */
export const liveDashboardErrorKey = (
    error: LiveDashboardApiError,
): LiveDashboardErrorKey | undefined =>
    error.errorCode === 'LIVE_DASHBOARD_FINISH_RESERVED_FOR_OFFICE'
        ? dashboardKeys.finishReservedForOffice
        : undefined

/**
 * Der i18n-Key zur abgelehnten QR-Code-Zuweisung. "Dieser QR-Code konnte nicht zugewiesen werden"
 * klingt nach Scanfehler und schickt die Helfer dazu, es noch einmal zu scannen - dabei ist das
 * Bändchen schon vergeben und sie brauchen ein anderes.
 */
export const qrAssignErrorKey = (error: LiveDashboardApiError): LiveDashboardErrorKey | undefined =>
    error.errorCode === 'QR_CODE_ALREADY_IN_USE' ? qrAssignKeys.alreadyInUse : undefined

/**
 * Der i18n-Key zum abgelehnten Check-in/Check-out. "Schon eingecheckt" ist dabei keine Störung,
 * sondern die Auskunft, dass nichts mehr zu tun ist.
 */
export const participantTrackingErrorKey = (
    error: LiveDashboardApiError,
): LiveDashboardErrorKey | undefined => {
    switch (error.errorCode) {
        case 'TRACKING_TEAM_ALREADY_CHECKED_IN':
            return trackingKeys.alreadyCheckedIn
        case 'TRACKING_TEAM_NOT_CHECKED_IN':
            return trackingKeys.notCheckedIn
        case 'TRACKING_QR_CODE_NOT_ASSOCIATED_WITH_PARTICIPANT':
            return trackingKeys.qrCodeNotAssociated
        case 'TRACKING_QR_CODE_NOT_FOUND':
            return trackingKeys.qrCodeNotFound
        // Die drei Gründe des manuellen Nachtrags. "Widersprüchliche Reihenfolge" ist der
        // häufigste - und ohne eigenen Satz die irreführendste Meldung von allen, weil sie sich
        // wie ein Speicherfehler liest, obwohl der Eintrag nur eine andere Uhrzeit braucht.
        case 'TRACKING_ENTRY_NOT_FOUND':
            return trackingKeys.entryNotFound
        case 'TRACKING_SEQUENCE_CONFLICT':
            return trackingKeys.sequenceConflict
        case 'TRACKING_TIMESTAMP_COLLISION':
            return trackingKeys.timestampCollision
    }

    return undefined
}

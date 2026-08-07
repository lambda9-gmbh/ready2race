import {ErrorCode} from '@api/types.gen.ts'

/**
 * Die Fehlermeldungen rund um Urkunden und ihre Vorlagen. Bis zuletzt gab es hier gar keine
 * ErrorCodes: der Teilnahmeurkunden-Download reichte `error.message` roh durch (englischer
 * Backend-Text mitten in der deutschen Oberfläche), die Siegerurkunden unterschieden nur nach
 * HTTP-Status und warfen "keine Vorlage" mit "Vorlage unlesbar" in einen Topf.
 */

// Teilnahmeurkunde (CertificateError) - Download aus der Teilnehmerliste.
const participationKeys = {
    noResults: 'club.participant.certificate.error.noResults',
    missingTemplate: 'club.participant.certificate.error.missingTemplate',
    unreadableTemplate: 'club.participant.certificate.error.unreadableTemplate',
    notAChallengeEvent: 'club.participant.certificate.error.notAChallengeEvent',
    challengeStillInProgress: 'club.participant.certificate.error.challengeStillInProgress',
    unexpected: 'club.participant.certificate.error.unexpected',
} as const

// Siegerurkunde (AwardCertificateError) - der Download-Dialog. missingTemplate und noResults
// existieren bereits und werden weiterverwendet, damit der Dialog seine Alerts behält.
const awardKeys = {
    noResults: 'awardCertificate.download.error.noResults',
    missingTemplate: 'awardCertificate.download.error.missingTemplate',
    unreadableTemplate: 'awardCertificate.download.error.unreadableTemplate',
    competitionNotInEvent: 'awardCertificate.download.error.competitionNotInEvent',
    isChallengeEvent: 'awardCertificate.download.error.isChallengeEvent',
    unexpected: 'awardCertificate.download.error.unexpected',
} as const

// Dokumentvorlage (GapDocumentTemplateError) - Anlegen und Bearbeiten einer PDF-Vorlage.
const templateKeys = {
    invalidFont: 'gap.document.template.error.invalidFont',
    invalidPdf: 'gap.document.template.error.invalidPdf',
    invalidPackage: 'gap.document.template.error.invalidPackage',
    unsupportedPackageVersion: 'gap.document.template.error.unsupportedPackageVersion',
    typeMismatch: 'gap.document.template.error.typeMismatch',
    placeholderPageNotSupported: 'gap.document.template.error.placeholderPageNotSupported',
    placeholderTypeNotSupported: 'gap.document.template.error.placeholderTypeNotSupported',
} as const

export type CertificateErrorKey =
    | (typeof participationKeys)[keyof typeof participationKeys]
    | (typeof awardKeys)[keyof typeof awardKeys]
    | (typeof templateKeys)[keyof typeof templateKeys]

/** Was von einer Fehlerantwort hier gebraucht wird — unabhängig vom konkreten SDK-Fehlertyp. */
export type CertificateApiError = {
    message: string
    errorCode?: ErrorCode
}

export const participationUnexpectedKey = participationKeys.unexpected
export const awardUnexpectedKey = awardKeys.unexpected

/**
 * Der i18n-Key zur abgelehnten Teilnahmeurkunde. Bewusst nur der Key statt der fertigen Meldung,
 * damit die Zuordnung ohne i18n-Kontext testbar bleibt (dasselbe Muster wie scheduleError.ts).
 */
export const participationCertificateErrorKey = (
    error: CertificateApiError,
): CertificateErrorKey => {
    switch (error.errorCode) {
        case 'CERTIFICATE_NO_RESULTS':
            return participationKeys.noResults
        case 'CERTIFICATE_MISSING_TEMPLATE':
            return participationKeys.missingTemplate
        case 'CERTIFICATE_UNREADABLE_TEMPLATE':
            return participationKeys.unreadableTemplate
        case 'CERTIFICATE_NOT_A_CHALLENGE_EVENT':
            return participationKeys.notAChallengeEvent
        case 'CERTIFICATE_CHALLENGE_STILL_IN_PROGRESS':
            return participationKeys.challengeStillInProgress
    }

    return participationKeys.unexpected
}

/**
 * Der i18n-Key zur abgelehnten Siegerurkunde. Die Unterscheidung nach HTTP-Status kam an ihre
 * Grenze, sobald zwei Gründe denselben Status teilen (409 für fehlende UND unlesbare Vorlage,
 * 400 für "keine Platzierungen", "Wettkampf gehört nicht zur Veranstaltung" und Challenge-Event).
 */
export const awardCertificateErrorKey = (error: CertificateApiError): CertificateErrorKey => {
    switch (error.errorCode) {
        case 'AWARD_CERTIFICATE_NO_RESULTS':
            return awardKeys.noResults
        case 'AWARD_CERTIFICATE_MISSING_TEMPLATE':
            return awardKeys.missingTemplate
        case 'AWARD_CERTIFICATE_UNREADABLE_TEMPLATE':
            return awardKeys.unreadableTemplate
        case 'AWARD_CERTIFICATE_COMPETITION_NOT_IN_EVENT':
            return awardKeys.competitionNotInEvent
        case 'AWARD_CERTIFICATE_IS_CHALLENGE_EVENT':
            return awardKeys.isChallengeEvent
    }

    return awardKeys.unexpected
}

/**
 * Nur bei fehlender Vorlage hilft der Verweis in die Konfiguration - bei einer unlesbaren Datei
 * ebenso, denn beides wird an derselben Stelle behoben. Bei allen übrigen Gründen wäre der Link
 * eine Sackgasse.
 */
export const awardCertificateErrorLinksToConfig = (key: CertificateErrorKey): boolean =>
    key === awardKeys.missingTemplate || key === awardKeys.unreadableTemplate

/**
 * Der i18n-Key zur abgelehnten Dokumentvorlage, oder `undefined`, wenn der Grund unbekannt ist -
 * dann bleibt es bei der Sammelmeldung des EntityDialog, die schon sagt, welche Entität betroffen
 * ist ("Vorlage konnte nicht angelegt werden").
 */
export const documentTemplateErrorKey = (
    error: CertificateApiError,
): CertificateErrorKey | undefined => {
    switch (error.errorCode) {
        case 'DOCUMENT_TEMPLATE_INVALID_FONT':
            return templateKeys.invalidFont
        case 'DOCUMENT_TEMPLATE_INVALID_PDF':
            return templateKeys.invalidPdf
        case 'DOCUMENT_TEMPLATE_INVALID_PACKAGE':
            return templateKeys.invalidPackage
        case 'DOCUMENT_TEMPLATE_UNSUPPORTED_PACKAGE_VERSION':
            return templateKeys.unsupportedPackageVersion
        case 'DOCUMENT_TEMPLATE_TYPE_MISMATCH':
            return templateKeys.typeMismatch
        case 'DOCUMENT_TEMPLATE_PLACEHOLDER_PAGE_NOT_SUPPORTED':
            return templateKeys.placeholderPageNotSupported
        case 'DOCUMENT_TEMPLATE_PLACEHOLDER_TYPE_NOT_SUPPORTED':
            return templateKeys.placeholderTypeNotSupported
    }

    return undefined
}

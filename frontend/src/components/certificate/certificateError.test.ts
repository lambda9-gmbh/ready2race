import {describe, expect, it} from 'vitest'
import {
    CertificateApiError,
    awardCertificateErrorKey,
    awardCertificateErrorLinksToConfig,
    awardUnexpectedKey,
    documentTemplateErrorKey,
    participationCertificateErrorKey,
    participationUnexpectedKey,
} from './certificateError.ts'
import deTranslations from '@i18n/de/translations.json'
import enTranslations from '@i18n/en/translations.json'
import daTranslations from '@i18n/da/translations.json'

const error = (partial: Partial<CertificateApiError>): CertificateApiError => ({
    message: 'No results in this event for this participant',
    ...partial,
})

const lookup = (translations: object, key: string): unknown =>
    key.split('.').reduce<unknown>((node, part) => (node as Record<string, unknown>)?.[part], translations)

describe('participationCertificateErrorKey', () => {
    it.each([
        ['CERTIFICATE_NO_RESULTS', 'club.participant.certificate.error.noResults'],
        ['CERTIFICATE_MISSING_TEMPLATE', 'club.participant.certificate.error.missingTemplate'],
        ['CERTIFICATE_UNREADABLE_TEMPLATE', 'club.participant.certificate.error.unreadableTemplate'],
        ['CERTIFICATE_NOT_A_CHALLENGE_EVENT', 'club.participant.certificate.error.notAChallengeEvent'],
        [
            'CERTIFICATE_CHALLENGE_STILL_IN_PROGRESS',
            'club.participant.certificate.error.challengeStillInProgress',
        ],
    ] as const)('bildet %s auf einen eigenen Text ab', (errorCode, key) => {
        expect(participationCertificateErrorKey(error({errorCode}))).toBe(key)
    })

    it('reicht den englischen Backend-Text nicht mehr durch', () => {
        // Genau das passierte bis zuletzt: feedback.error(error.message) zeigte "No results in
        // this event for this participant" mitten in der deutschen Oberfläche.
        expect(participationCertificateErrorKey(error({}))).toBe(participationUnexpectedKey)
    })
})

describe('awardCertificateErrorKey', () => {
    it.each([
        ['AWARD_CERTIFICATE_NO_RESULTS', 'awardCertificate.download.error.noResults'],
        ['AWARD_CERTIFICATE_MISSING_TEMPLATE', 'awardCertificate.download.error.missingTemplate'],
        [
            'AWARD_CERTIFICATE_UNREADABLE_TEMPLATE',
            'awardCertificate.download.error.unreadableTemplate',
        ],
        [
            'AWARD_CERTIFICATE_COMPETITION_NOT_IN_EVENT',
            'awardCertificate.download.error.competitionNotInEvent',
        ],
        ['AWARD_CERTIFICATE_IS_CHALLENGE_EVENT', 'awardCertificate.download.error.isChallengeEvent'],
    ] as const)('bildet %s auf einen eigenen Text ab', (errorCode, key) => {
        expect(awardCertificateErrorKey(error({errorCode}))).toBe(key)
    })

    it('trennt die fehlende von der unlesbaren Vorlage', () => {
        // Beide kamen als 409 und teilten sich denselben Alert-Text.
        expect(awardCertificateErrorKey(error({errorCode: 'AWARD_CERTIFICATE_MISSING_TEMPLATE'}))).not.toBe(
            awardCertificateErrorKey(error({errorCode: 'AWARD_CERTIFICATE_UNREADABLE_TEMPLATE'})),
        )
    })

    it('sagt beim Challenge-Event den wahren Grund statt "keine Platzierungen"', () => {
        // G18: der Download antwortete mit NoResults - richtiges Ergebnis, falsche Begruendung.
        expect(awardCertificateErrorKey(error({errorCode: 'AWARD_CERTIFICATE_IS_CHALLENGE_EVENT'}))).not.toBe(
            awardCertificateErrorKey(error({errorCode: 'AWARD_CERTIFICATE_NO_RESULTS'})),
        )
    })

    it('reicht Unbekanntes nicht als englischen Backend-Text durch', () => {
        expect(awardCertificateErrorKey(error({}))).toBe(awardUnexpectedKey)
    })
})

describe('awardCertificateErrorLinksToConfig', () => {
    it('verweist nur bei Vorlagen-Problemen in die Konfiguration', () => {
        expect(
            awardCertificateErrorLinksToConfig('awardCertificate.download.error.missingTemplate'),
        ).toBe(true)
        expect(
            awardCertificateErrorLinksToConfig('awardCertificate.download.error.unreadableTemplate'),
        ).toBe(true)
    })

    it('bietet bei allen übrigen Gründen keine Sackgasse an', () => {
        expect(awardCertificateErrorLinksToConfig('awardCertificate.download.error.noResults')).toBe(
            false,
        )
        expect(
            awardCertificateErrorLinksToConfig('awardCertificate.download.error.isChallengeEvent'),
        ).toBe(false)
        expect(awardCertificateErrorLinksToConfig(awardUnexpectedKey)).toBe(false)
    })
})

describe('documentTemplateErrorKey', () => {
    it.each([
        ['DOCUMENT_TEMPLATE_INVALID_FONT', 'gap.document.template.error.invalidFont'],
        ['DOCUMENT_TEMPLATE_TYPE_MISMATCH', 'gap.document.template.error.typeMismatch'],
        [
            'DOCUMENT_TEMPLATE_PLACEHOLDER_PAGE_NOT_SUPPORTED',
            'gap.document.template.error.placeholderPageNotSupported',
        ],
        [
            'DOCUMENT_TEMPLATE_PLACEHOLDER_TYPE_NOT_SUPPORTED',
            'gap.document.template.error.placeholderTypeNotSupported',
        ],
    ] as const)('bildet %s auf einen eigenen Text ab', (errorCode, key) => {
        expect(documentTemplateErrorKey(error({errorCode}))).toBe(key)
    })

    it('überlässt Unbekanntes der Sammelmeldung des Dialogs', () => {
        expect(documentTemplateErrorKey(error({}))).toBeUndefined()
    })

    it('benennt ein unlesbares Paket', () => {
        expect(documentTemplateErrorKey({message: '', errorCode: 'DOCUMENT_TEMPLATE_INVALID_PACKAGE'}))
            .toBe('gap.document.template.error.invalidPackage')
    })

    it('benennt eine unbekannte Paketversion', () => {
        expect(
            documentTemplateErrorKey({
                message: '',
                errorCode: 'DOCUMENT_TEMPLATE_UNSUPPORTED_PACKAGE_VERSION',
            }),
        ).toBe('gap.document.template.error.unsupportedPackageVersion')
    })

    it('benennt eine Datei, die kein PDF ist', () => {
        expect(documentTemplateErrorKey({message: '', errorCode: 'DOCUMENT_TEMPLATE_INVALID_PDF'}))
            .toBe('gap.document.template.error.invalidPdf')
    })
})

describe('Übersetzungen', () => {
    const keys = [
        'club.participant.certificate.error.noResults',
        'club.participant.certificate.error.missingTemplate',
        'club.participant.certificate.error.unreadableTemplate',
        'club.participant.certificate.error.notAChallengeEvent',
        'club.participant.certificate.error.challengeStillInProgress',
        participationUnexpectedKey,
        'awardCertificate.download.error.noResults',
        'awardCertificate.download.error.missingTemplate',
        'awardCertificate.download.error.missingTemplateLink',
        'awardCertificate.download.error.unreadableTemplate',
        'awardCertificate.download.error.competitionNotInEvent',
        'awardCertificate.download.error.isChallengeEvent',
        awardUnexpectedKey,
        'gap.document.template.error.invalidFont',
        'gap.document.template.error.typeMismatch',
        'gap.document.template.error.placeholderPageNotSupported',
        'gap.document.template.error.placeholderTypeNotSupported',
        'gap.document.template.error.invalidPdf',
        'gap.document.template.error.invalidPackage',
        'gap.document.template.error.unsupportedPackageVersion',
    ]

    // Ein falsch geschriebener Key faellt sonst erst auf, wenn der rohe Key im Dialog steht.
    it.each(keys)('hat einen deutschen Text für %s', key => {
        expect(typeof lookup(deTranslations, key)).toBe('string')
    })

    it.each(keys)('hat auch einen englischen und dänischen Text für %s', key => {
        expect(typeof lookup(enTranslations, key)).toBe('string')
        expect(typeof lookup(daTranslations, key)).toBe('string')
    })
})

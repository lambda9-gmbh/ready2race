import {describe, expect, it} from 'vitest'
import {
    LiveDashboardApiError,
    liveDashboardErrorKey,
    participantTrackingErrorKey,
    qrAssignErrorKey,
} from './liveDashboardError.ts'
import deTranslations from '@i18n/de/translations.json'
import enTranslations from '@i18n/en/translations.json'
import daTranslations from '@i18n/da/translations.json'

const error = (partial: Partial<LiveDashboardApiError>): LiveDashboardApiError => ({
    message: 'Finishing is handled by the regatta office for this event',
    ...partial,
})

const lookup = (translations: object, key: string): unknown =>
    key.split('.').reduce<unknown>((node, part) => (node as Record<string, unknown>)?.[part], translations)

describe('liveDashboardErrorKey', () => {
    it('sagt beim Beenden, dass das Regattabüro zuständig ist', () => {
        // Der häufigste Fehlerfall am Steg - und gar keine Störung, sondern eine
        // Zuständigkeitsfrage. Bislang las das Steg-Personal "Der Lauf konnte nicht geändert
        // werden" und probierte es erneut.
        expect(
            liveDashboardErrorKey(error({errorCode: 'LIVE_DASHBOARD_FINISH_RESERVED_FOR_OFFICE'})),
        ).toBe('event.liveDashboard.control.errorReason.finishReservedForOffice')
    })

    it('überlässt Unbekanntes der Sammelmeldung der Karte', () => {
        expect(liveDashboardErrorKey(error({}))).toBeUndefined()
    })
})

describe('qrAssignErrorKey', () => {
    it('sagt bei der Doppelvergabe, dass ein anderes Bändchen nötig ist', () => {
        expect(qrAssignErrorKey(error({errorCode: 'QR_CODE_ALREADY_IN_USE'}))).toBe(
            'qrAssign.errorReason.alreadyInUse',
        )
    })

    it('überlässt Unbekanntes der Sammelmeldung der Seite', () => {
        expect(qrAssignErrorKey(error({}))).toBeUndefined()
    })
})

describe('participantTrackingErrorKey', () => {
    it.each([
        ['TRACKING_TEAM_ALREADY_CHECKED_IN', 'alreadyCheckedIn'],
        ['TRACKING_TEAM_NOT_CHECKED_IN', 'notCheckedIn'],
        ['TRACKING_QR_CODE_NOT_ASSOCIATED_WITH_PARTICIPANT', 'qrCodeNotAssociated'],
        ['TRACKING_QR_CODE_NOT_FOUND', 'qrCodeNotFound'],
    ] as const)('bildet %s auf einen eigenen Text ab', (errorCode, leaf) => {
        expect(participantTrackingErrorKey(error({errorCode}))).toBe(
            `club.participant.tracking.errorReason.${leaf}`,
        )
    })

    it('trennt "schon drin" von "gar nicht drin"', () => {
        // Beide teilten sich je nach Richtung "Fehler beim Einchecken" oder "... Auschecken",
        // obwohl der eine Fall bedeutet, dass gar nichts mehr zu tun ist.
        expect(participantTrackingErrorKey(error({errorCode: 'TRACKING_TEAM_ALREADY_CHECKED_IN'}))).not.toBe(
            participantTrackingErrorKey(error({errorCode: 'TRACKING_TEAM_NOT_CHECKED_IN'})),
        )
    })

    it('überlässt Unbekanntes der Sammelmeldung der Aktion', () => {
        expect(participantTrackingErrorKey(error({}))).toBeUndefined()
    })
})

describe('Übersetzungen', () => {
    const keys = [
        'event.liveDashboard.control.errorReason.finishReservedForOffice',
        'qrAssign.errorReason.alreadyInUse',
        'club.participant.tracking.errorReason.alreadyCheckedIn',
        'club.participant.tracking.errorReason.notCheckedIn',
        'club.participant.tracking.errorReason.qrCodeNotAssociated',
        'club.participant.tracking.errorReason.qrCodeNotFound',
        // Die Sammelmeldungen, auf die die jeweilige Stelle zurückfällt.
        'event.liveDashboard.control.error',
        'qrAssign.notAssigned',
        'club.participant.tracking.checkIn.error',
        'club.participant.tracking.checkOut.error',
    ]

    it.each(keys)('hat einen deutschen Text für %s', key => {
        expect(typeof lookup(deTranslations, key)).toBe('string')
    })

    it.each(keys)('hat auch einen englischen und dänischen Text für %s', key => {
        expect(typeof lookup(enTranslations, key)).toBe('string')
        expect(typeof lookup(daTranslations, key)).toBe('string')
    })
})

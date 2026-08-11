import {describe, expect, it} from 'vitest'
import {
    AwardCeremonyApiError,
    awardCeremonyErrorKey,
    awardCeremonyUnexpectedKey,
} from './awardCeremonyError.ts'
import deTranslations from '@i18n/de/translations.json'
import enTranslations from '@i18n/en/translations.json'
import daTranslations from '@i18n/da/translations.json'

const error = (partial: Partial<AwardCeremonyApiError>): AwardCeremonyApiError => ({
    message: 'No results in this event',
    ...partial,
})

const lookup = (translations: object, key: string): unknown =>
    key
        .split('.')
        .reduce<unknown>((node, part) => (node as Record<string, unknown>)?.[part], translations)

describe('awardCeremonyErrorKey', () => {
    it.each([
        ['AWARD_CEREMONY_NO_RESULTS', 'awardCeremony.download.error.noResults'],
        [
            'AWARD_CEREMONY_COMPETITION_NOT_IN_EVENT',
            'awardCeremony.download.error.competitionNotInEvent',
        ],
        [
            'AWARD_CEREMONY_UNKNOWN_RATING_CATEGORY',
            'awardCeremony.download.error.unknownRatingCategory',
        ],
        ['AWARD_CEREMONY_IS_CHALLENGE_EVENT', 'awardCeremony.download.error.isChallengeEvent'],
    ] as const)('bildet %s auf einen eigenen Text ab', (errorCode, key) => {
        expect(awardCeremonyErrorKey(error({errorCode}))).toBe(key)
    })

    it('trennt die fehlende Wertung von den fehlenden Platzierungen', () => {
        // Beide kommen als 400 - "es gibt noch keine Plätze" heißt warten, "die Wertung gibt es
        // nicht mehr" heißt neu laden.
        expect(
            awardCeremonyErrorKey(error({errorCode: 'AWARD_CEREMONY_UNKNOWN_RATING_CATEGORY'})),
        ).not.toBe(awardCeremonyErrorKey(error({errorCode: 'AWARD_CEREMONY_NO_RESULTS'})))
    })

    it('reicht den englischen Backend-Text nicht durch', () => {
        expect(awardCeremonyErrorKey(error({}))).toBe(awardCeremonyUnexpectedKey)
    })

    it('fällt auch bei einem fremden ErrorCode zurück', () => {
        expect(awardCeremonyErrorKey(error({errorCode: 'AWARD_CERTIFICATE_NO_RESULTS'}))).toBe(
            awardCeremonyUnexpectedKey,
        )
    })
})

describe('Übersetzungen', () => {
    const keys = [
        'awardCeremony.download.button',
        'awardCeremony.download.title',
        'awardCeremony.download.action',
        'awardCeremony.download.hint',
        'awardCeremony.download.selectAll',
        'awardCeremony.download.deselectAll',
        'awardCeremony.download.withoutCategory',
        'awardCeremony.download.boats_one',
        'awardCeremony.download.boats_other',
        'awardCeremony.download.empty',
        'awardCeremony.download.error.noResults',
        'awardCeremony.download.error.competitionNotInEvent',
        'awardCeremony.download.error.unknownRatingCategory',
        'awardCeremony.download.error.isChallengeEvent',
        awardCeremonyUnexpectedKey,
    ]

    // Ein falsch geschriebener Key fällt sonst erst auf, wenn der rohe Key im Dialog steht.
    it.each(keys)('hat einen deutschen Text für %s', key => {
        expect(typeof lookup(deTranslations, key)).toBe('string')
    })

    it.each(keys)('hat auch einen englischen und dänischen Text für %s', key => {
        expect(typeof lookup(enTranslations, key)).toBe('string')
        expect(typeof lookup(daTranslations, key)).toBe('string')
    })
})

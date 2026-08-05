import {describe, expect, it} from 'vitest'
import {DeregisterCompetitionRegistrationError, ErrorCode} from '@api/types.gen.ts'
import {deregistrationErrorKey} from './deregistrationError.ts'
import de from '@i18n/de/translations.json'

const error = (
    status: number,
    errorCode?: ErrorCode,
): DeregisterCompetitionRegistrationError => ({
    status: {value: status, description: 'x'},
    message: 'x',
    errorCode,
})

const lookup = (key: string): unknown =>
    key.split('.').reduce<unknown>((node, part) => (node as Record<string, unknown>)?.[part], de)

describe('deregistrationErrorKey', () => {
    it('bildet den gewerteten Lauf auf seine eigene Meldung ab', () => {
        expect(deregistrationErrorKey(error(409, 'DEREGISTRATION_RESULTS_ALREADY_EXIST'))).toBe(
            'event.competition.registration.deregister.error.DEREGISTRATION_RESULTS_ALREADY_EXIST',
        )
    })

    it('unterscheidet die beiden 409er', () => {
        expect(deregistrationErrorKey(error(409, 'DEREGISTRATION_ALREADY_EXISTS'))).not.toBe(
            deregistrationErrorKey(error(409, 'DEREGISTRATION_RESULTS_ALREADY_EXIST')),
        )
    })

    it('fällt ohne Code auf die allgemeine Meldung zurück', () => {
        expect(deregistrationErrorKey(error(500))).toBe(
            'event.competition.registration.deregister.error.unexpected',
        )
    })

    it('fällt bei einem fremden Code auf die allgemeine Meldung zurück', () => {
        expect(deregistrationErrorKey(error(409, 'CLUB_NAME_ALREADY_EXISTS'))).toBe(
            'event.competition.registration.deregister.error.unexpected',
        )
    })

    it('hat für jeden gelieferten Key eine deutsche Übersetzung', () => {
        const keys = [
            deregistrationErrorKey(error(409, 'DEREGISTRATION_ALREADY_EXISTS')),
            deregistrationErrorKey(error(409, 'DEREGISTRATION_RESULTS_ALREADY_EXIST')),
            deregistrationErrorKey(error(400, 'DEREGISTRATION_NOT_IN_CURRENT_ROUND')),
            deregistrationErrorKey(error(400, 'DEREGISTRATION_REGISTRATION_STILL_OPEN')),
            deregistrationErrorKey(error(500)),
        ]

        keys.forEach(key => expect(typeof lookup(key)).toBe('string'))
    })
})

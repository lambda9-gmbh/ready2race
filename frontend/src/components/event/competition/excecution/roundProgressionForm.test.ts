import {describe, expect, test} from 'vitest'
import {choiceFromDto, effectiveFromChoice, requestFromChoice} from './roundProgressionForm.ts'

describe('roundProgressionForm', () => {
    test('null am Wettkampf heißt: der Veranstaltung folgen', () => {
        expect(
            choiceFromDto({
                autoCreateFollowingRounds: null,
                eventAutoCreateFollowingRounds: true,
                effective: true,
            }),
        ).toBe('INHERIT')
    })

    test('die ausdrückliche Wahl bleibt erhalten', () => {
        expect(
            choiceFromDto({
                autoCreateFollowingRounds: false,
                eventAutoCreateFollowingRounds: true,
                effective: false,
            }),
        ).toBe('DISABLED')
    })

    test('erben schickt null, nicht false', () => {
        expect(requestFromChoice('INHERIT').autoCreateFollowingRounds).toBeNull()
        expect(requestFromChoice('DISABLED').autoCreateFollowingRounds).toBe(false)
        expect(requestFromChoice('ENABLED').autoCreateFollowingRounds).toBe(true)
    })

    test('die Vorschau folgt der Veranstaltung nur beim Erben', () => {
        expect(effectiveFromChoice('INHERIT', true)).toBe(true)
        expect(effectiveFromChoice('DISABLED', true)).toBe(false)
        expect(effectiveFromChoice('ENABLED', false)).toBe(true)
    })
})

import {describe, expect, it} from 'vitest'
import {EventDto} from '@api/types.gen.ts'
import {eventDtoToUpdateRequest} from './eventUpdateRequest.ts'

const baseDto: EventDto = {
    id: '11111111-1111-1111-1111-111111111111',
    name: 'Coastal Rowing Festival',
    description: 'Regatta an der Förde',
    location: 'Kiel',
    registrationAvailableFrom: '2026-05-01T00:00:00Z',
    registrationAvailableTo: '2026-07-01T00:00:00Z',
    lateRegistrationAvailableTo: '2026-07-15T00:00:00Z',
    invoicePrefix: 'CRF',
    published: true,
    paymentDueBy: '2026-07-20',
    latePaymentDueBy: '2026-07-30',
    registrationsFinalized: true,
    mixedTeamTerm: 'Mixed',
    challengeEvent: false,
    allowSelfSubmission: false,
    submissionNeedsVerification: true,
    allowParticipantSelfRegistration: false,
    chainProgressionMode: 'SCHIEDSRICHTER',
    autoCreateFollowingRounds: true,
    showBreaksOnPublicBoards: true,
    allowCrossClubRegistration: true,
    publicResultsVisibility: 'FINISHED_ONLY',
    executionAutoRefresh: true,
    executionAutoRefreshSeconds: 10,
}

describe('eventDtoToUpdateRequest', () => {
    it('trägt alle Einstellungen unverändert in den Update-Request', () => {
        const request = eventDtoToUpdateRequest(baseDto)

        expect(request).toEqual({
            name: 'Coastal Rowing Festival',
            description: 'Regatta an der Förde',
            location: 'Kiel',
            registrationAvailableFrom: '2026-05-01T00:00:00Z',
            registrationAvailableTo: '2026-07-01T00:00:00Z',
            lateRegistrationAvailableTo: '2026-07-15T00:00:00Z',
            invoicePrefix: 'CRF',
            published: true,
            paymentDueBy: '2026-07-20',
            latePaymentDueBy: '2026-07-30',
            mixedTeamTerm: 'Mixed',
            challengeResultType: undefined,
            allowSelfSubmission: false,
            submissionNeedsVerification: true,
            allowParticipantSelfRegistration: false,
            chainProgressionMode: 'SCHIEDSRICHTER',
            autoCreateFollowingRounds: true,
            showBreaksOnPublicBoards: true,
            allowCrossClubRegistration: true,
            publicResultsVisibility: 'FINISHED_ONLY',
            executionAutoRefresh: true,
            executionAutoRefreshSeconds: 10,
        })
    })

    it('lässt gezielt überschriebene Felder zu, ohne die übrigen anzufassen', () => {
        const request = {
            ...eventDtoToUpdateRequest(baseDto),
            chainProgressionMode: 'REGATTABUERO' as const,
            executionAutoRefreshSeconds: 30,
        }

        expect(request.chainProgressionMode).toBe('REGATTABUERO')
        expect(request.executionAutoRefreshSeconds).toBe(30)
        // Stichprobe: das Übrige bleibt, wie es vom Server kam.
        expect(request.name).toBe('Coastal Rowing Festival')
        expect(request.publicResultsVisibility).toBe('FINISHED_ONLY')
    })

    it('schickt den Challenge-Ergebnistyp nur bei Challenge-Veranstaltungen mit', () => {
        expect(
            eventDtoToUpdateRequest({...baseDto, challengeResultType: 'DISTANCE'})
                .challengeResultType,
        ).toBeUndefined()
        expect(
            eventDtoToUpdateRequest({
                ...baseDto,
                challengeEvent: true,
                challengeResultType: 'DISTANCE',
            }).challengeResultType,
        ).toBe('DISTANCE')
    })
})

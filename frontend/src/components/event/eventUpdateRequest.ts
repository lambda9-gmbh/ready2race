import {EventDto, UpdateEventRequest} from '@api/types.gen.ts'

/**
 * Baut aus dem geladenen Event den Update-Request, der alles unverändert lässt.
 *
 * Der Update-Endpunkt kennt kein Teil-Update: Wer ein einzelnes Feld ändern will (etwa die
 * Durchführungs-Einstellungen im Zeitplan-Popover), muss alle übrigen Felder mitschicken —
 * sonst würden sie geleert. Diese Abbildung ist das Gegenstück zu `mapDtoToForm` +
 * `mapFormToUpdateRequest` im EventDialog, nur ohne den Umweg über ein Formular; wer einzelne
 * Felder ändern will, überschreibt sie per Spread: `{...eventDtoToUpdateRequest(dto), feld: neu}`.
 */
export const eventDtoToUpdateRequest = (dto: EventDto): UpdateEventRequest => ({
    name: dto.name,
    description: dto.description,
    location: dto.location,
    registrationAvailableFrom: dto.registrationAvailableFrom,
    registrationAvailableTo: dto.registrationAvailableTo,
    lateRegistrationAvailableTo: dto.lateRegistrationAvailableTo,
    invoicePrefix: dto.invoicePrefix,
    published: dto.published,
    paymentDueBy: dto.paymentDueBy,
    latePaymentDueBy: dto.latePaymentDueBy,
    mixedTeamTerm: dto.mixedTeamTerm,
    // Dieselbe Regel wie im EventDialog: der Ergebnistyp gehört nur zu Challenge-Veranstaltungen.
    challengeResultType: dto.challengeEvent ? dto.challengeResultType : undefined,
    allowSelfSubmission: dto.allowSelfSubmission,
    submissionNeedsVerification: dto.submissionNeedsVerification,
    allowParticipantSelfRegistration: dto.allowParticipantSelfRegistration,
    chainProgressionMode: dto.chainProgressionMode,
    autoCreateFollowingRounds: dto.autoCreateFollowingRounds,
    showBreaksOnPublicBoards: dto.showBreaksOnPublicBoards,
    allowCrossClubRegistration: dto.allowCrossClubRegistration,
    publicResultsVisibility: dto.publicResultsVisibility,
    executionAutoRefresh: dto.executionAutoRefresh,
    executionAutoRefreshSeconds: dto.executionAutoRefreshSeconds,
})

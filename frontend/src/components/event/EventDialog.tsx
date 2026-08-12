import {BaseEntityDialogProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityDialog from '@components/EntityDialog.tsx'
import {Alert, Box, Stack, Typography} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputDateTime from '@components/form/input/FormInputDateTime.tsx'
import {useForm} from 'react-hook-form-mui'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {useCallback} from 'react'
import {
    CreateEventRequest,
    EventDto,
    MatchResultType,
    PublicResultsVisibility,
    UpdateEventRequest,
} from '@api/types.gen.ts'
import {addEvent, updateEvent} from '@api/sdk.gen.ts'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'
import FormInputDate from '@components/form/input/FormInputDate.tsx'
import {FormInputSelect} from '@components/form/input/FormInputSelect.tsx'
import {AUTO_REFRESH_DEFAULT_SECONDS} from '@components/event/competition/excecution/autoRefresh.ts'

type EventForm = {
    name: string
    description: string
    location: string
    registrationAvailableFrom: string
    registrationAvailableTo: string
    lateRegistrationAvailableTo: string
    invoicePrefix: string
    published: boolean
    paymentDueBy: string
    latePaymentDueBy: string
    mixedTeamTerm: string
    challengeEvent: boolean
    challengeResultType: MatchResultType
    allowSelfSubmission: boolean
    submissionNeedsVerification: boolean
    allowParticipantSelfRegistration: boolean
    // Die Durchführungs-Einstellungen (chainProgressionMode, autoCreateFollowingRounds,
    // executionAutoRefresh) fehlen hier bewusst: Sie werden seit dem 11.08.2026 im
    // Einstellungs-Popover des Zeitplan-Tabs gepflegt (siehe ScheduleSettingsPopover).
    showBreaksOnPublicBoards: boolean
    publicResultsVisibility: PublicResultsVisibility
}

const addAction = (formData: EventForm) => {
    return addEvent({
        body: mapFormToCreateRequest(formData),
    })
}

const editAction = (formData: EventForm, entity: EventDto) => {
    return updateEvent({
        path: {eventId: entity.id},
        body: mapFormToUpdateRequest(formData, entity),
    })
}

const EventDialog = (props: BaseEntityDialogProps<EventDto>) => {
    const {t} = useTranslation()

    const defaultValues: EventForm = {
        name: '',
        description: '',
        location: '',
        registrationAvailableFrom: '',
        registrationAvailableTo: '',
        lateRegistrationAvailableTo: '',
        invoicePrefix: '',
        published: false,
        paymentDueBy: '',
        latePaymentDueBy: '',
        mixedTeamTerm: '',
        challengeEvent: false,
        challengeResultType: 'DISTANCE',
        allowSelfSubmission: false,
        submissionNeedsVerification: false,
        allowParticipantSelfRegistration: false,
        showBreaksOnPublicBoards: false,
        publicResultsVisibility: 'FINISHED_ONLY',
    }

    const formContext = useForm<EventForm>()

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])

    const challengeEventWatch = formContext.watch('challengeEvent')
    const challengeResultTypes = [{id: 'DISTANCE', label: 'Distance (m)'}]
    const publicResultsVisibilities: {id: PublicResultsVisibility; label: string}[] = [
        {id: 'FINISHED_ONLY', label: t('event.publicResultsVisibility.FINISHED_ONLY')},
        {id: 'RESULTS_COMPLETE', label: t('event.publicResultsVisibility.RESULTS_COMPLETE')},
    ]

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}>
            <Stack spacing={4}>
                <FormInputText name={'name'} label={t('event.name')} required />
                <FormInputText name={'description'} label={t('event.description')} />
                <FormInputText name={'location'} label={t('event.location')} />
                <FormInputText name={'mixedTeamTerm'} label={t('event.mixedTeamTerm')} />
                <FormInputCheckbox name={'published'} label={t('event.published.published')} />
                <Box>
                    <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 4}}>
                        <FormInputCheckbox
                            disabled={props.entity ? props.entity.challengeEvent : undefined}
                            name={`challengeEvent`}
                            label={t('event.challengeEvent.challengeEvent')}
                        />
                        {challengeEventWatch && (
                            <Box sx={{flex: 1}}>
                                <FormInputSelect
                                    label={t('event.challengeResultType')}
                                    required={true}
                                    name="challengeResultType"
                                    options={challengeResultTypes}
                                    fullWidth
                                />
                            </Box>
                        )}
                    </Box>
                </Box>
                {challengeEventWatch && (
                    <Alert severity={'info'}>
                        <Typography variant={'body2'}>
                            {t('event.challengeEvent.explanation')}
                        </Typography>
                        {!props.entity && (
                            <Typography variant={'body2'} sx={{mt: 1}}>
                                {t('event.challengeEvent.unchangeable')}
                            </Typography>
                        )}
                    </Alert>
                )}
                <FormInputDateTime
                    name={'registrationAvailableFrom'}
                    label={t('event.registrationAvailable.timespanFrom')}
                />
                <FormInputDateTime
                    name={'registrationAvailableTo'}
                    label={t('event.registrationAvailable.timespanTo')}
                />
                <FormInputDateTime
                    name={'lateRegistrationAvailableTo'}
                    label={t('event.registrationAvailable.lateTo')}
                />
                <FormInputCheckbox
                    name={`allowSelfSubmission`}
                    label={t('event.allowSelfSubmission')}
                />
                <FormInputCheckbox
                    name={`submissionNeedsVerification`}
                    label={t('event.submissionNeedsVerification')}
                />
                <FormInputCheckbox
                    name={`allowParticipantSelfRegistration`}
                    label={t('event.allowParticipantSelfRegistration')}
                />
                <FormInputCheckbox
                    name={`showBreaksOnPublicBoards`}
                    label={t('event.showBreaksOnPublicBoards')}
                />
                <Typography variant={'body2'} color={'text.secondary'} sx={{mt: -1}}>
                    {t('event.showBreaksOnPublicBoardsHint')}
                </Typography>
                <FormInputSelect
                    label={t('event.publicResultsVisibility.label')}
                    required={true}
                    name="publicResultsVisibility"
                    options={publicResultsVisibilities}
                    fullWidth
                />
                <Typography variant={'body2'} color={'text.secondary'} sx={{mt: -1}}>
                    {t('event.publicResultsVisibility.hint')}
                </Typography>
                <FormInputText name={'invoicePrefix'} label={t('event.invoice.prefix')} />
                <FormInputDate name={'paymentDueBy'} label={t('event.invoice.paymentDueBy')} />
                <FormInputDate
                    name={'latePaymentDueBy'}
                    label={t('event.invoice.latePaymentDueBy')}
                />
            </Stack>
        </EntityDialog>
    )
}

function mapFormToCreateRequest(formData: EventForm): CreateEventRequest {
    return {
        name: formData.name,
        description: takeIfNotEmpty(formData.description),
        location: takeIfNotEmpty(formData.location),
        registrationAvailableFrom: takeIfNotEmpty(formData.registrationAvailableFrom),
        registrationAvailableTo: takeIfNotEmpty(formData.registrationAvailableTo),
        lateRegistrationAvailableTo: takeIfNotEmpty(formData.lateRegistrationAvailableTo),
        invoicePrefix: takeIfNotEmpty(formData.invoicePrefix),
        published: formData.published,
        paymentDueBy: takeIfNotEmpty(formData.paymentDueBy),
        latePaymentDueBy: takeIfNotEmpty(formData.latePaymentDueBy),
        mixedTeamTerm: takeIfNotEmpty(formData.mixedTeamTerm),
        challengeEvent: formData.challengeEvent,
        challengeResultType: formData.challengeEvent ? formData.challengeResultType : undefined,
        allowSelfSubmission: formData.allowSelfSubmission,
        submissionNeedsVerification: formData.submissionNeedsVerification,
        allowParticipantSelfRegistration: formData.allowParticipantSelfRegistration,
        showBreaksOnPublicBoards: formData.showBreaksOnPublicBoards,
        publicResultsVisibility: formData.publicResultsVisibility,
        // Die Durchführungs-Einstellungen gehören zum Zeitplan-Popover; eine neue Veranstaltung
        // startet mit den Server-Voreinstellungen (der Modus-Default liegt beim Backend).
        executionAutoRefresh: true,
        executionAutoRefreshSeconds: AUTO_REFRESH_DEFAULT_SECONDS,
    }
}

function mapFormToUpdateRequest(formData: EventForm, entity: EventDto): UpdateEventRequest {
    return {
        name: formData.name,
        description: takeIfNotEmpty(formData.description),
        location: takeIfNotEmpty(formData.location),
        registrationAvailableFrom: takeIfNotEmpty(formData.registrationAvailableFrom),
        registrationAvailableTo: takeIfNotEmpty(formData.registrationAvailableTo),
        lateRegistrationAvailableTo: takeIfNotEmpty(formData.lateRegistrationAvailableTo),
        invoicePrefix: takeIfNotEmpty(formData.invoicePrefix),
        published: formData.published,
        paymentDueBy: takeIfNotEmpty(formData.paymentDueBy),
        latePaymentDueBy: takeIfNotEmpty(formData.latePaymentDueBy),
        mixedTeamTerm: takeIfNotEmpty(formData.mixedTeamTerm),
        challengeResultType: entity.challengeEvent ? formData.challengeResultType : undefined,
        allowSelfSubmission: formData.allowSelfSubmission,
        submissionNeedsVerification: formData.submissionNeedsVerification,
        allowParticipantSelfRegistration: formData.allowParticipantSelfRegistration,
        showBreaksOnPublicBoards: formData.showBreaksOnPublicBoards,
        publicResultsVisibility: formData.publicResultsVisibility,
        // Nicht Teil dieses Dialogs (siehe ScheduleSettingsPopover) — der Endpunkt kennt aber
        // kein Teil-Update, also gehen die gespeicherten Werte unverändert mit.
        chainProgressionMode: entity.chainProgressionMode,
        autoCreateFollowingRounds: entity.autoCreateFollowingRounds,
        executionAutoRefresh: entity.executionAutoRefresh,
        executionAutoRefreshSeconds: entity.executionAutoRefreshSeconds,
    }
}

function mapDtoToForm(dto: EventDto): EventForm {
    return {
        name: dto.name,
        description: dto.description ?? '',
        location: dto.location ?? '',
        registrationAvailableFrom: dto.registrationAvailableFrom ?? '',
        registrationAvailableTo: dto.registrationAvailableTo ?? '',
        lateRegistrationAvailableTo: dto.lateRegistrationAvailableTo ?? '',
        invoicePrefix: dto.invoicePrefix ?? '',
        published: dto.published,
        paymentDueBy: dto.paymentDueBy ?? '',
        latePaymentDueBy: dto.latePaymentDueBy ?? '',
        mixedTeamTerm: dto.mixedTeamTerm ?? '',
        challengeEvent: dto.challengeEvent,
        challengeResultType: dto.challengeResultType ?? 'DISTANCE',
        allowSelfSubmission: dto.allowSelfSubmission,
        submissionNeedsVerification: dto.submissionNeedsVerification,
        allowParticipantSelfRegistration: dto.allowParticipantSelfRegistration,
        showBreaksOnPublicBoards: dto.showBreaksOnPublicBoards ?? false,
        publicResultsVisibility: dto.publicResultsVisibility ?? 'FINISHED_ONLY',
    }
}

export default EventDialog

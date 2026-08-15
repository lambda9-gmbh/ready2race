import EntityDialog from '@components/EntityDialog.tsx'
import {BaseEntityDialogProps} from '@utils/types.ts'
import {ParticipantForEventDto, ParticipantScanCompetitionDto} from '@api/types.gen.ts'
import {Alert, MenuItem, Stack, TextField} from '@mui/material'
import {
    approveParticipantRequirementsForEvent,
    getEventScanScope,
    getParticipantsForEventInApp,
} from '@api/sdk.gen.ts'
import {useForm} from 'react-hook-form-mui'
import {useCallback, useMemo, useState} from 'react'
import {competitionLabel, covers} from '@components/qrApp/requirementScope.ts'
import {eventRoute} from '@routes'
import FormInputTransferList from '@components/form/input/FormInputTransferList.tsx'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import Throbber from '@components/Throbber.tsx'

export type ParticipantRequirementApproveManuallyForEventForm = {
    requirementId: string
    requirementName: string
    isGlobal: boolean
    namedParticipantId?: string
    namedParticipantName?: string
    /**
     * Die Geltung der Bedingung (V202608141900). Der Abgleich ersetzt den vollständigen Zustand
     * und muss deshalb wissen, WOFÜR - ohne Wettkampf löschte er bei einer wettkampfbezogenen
     * Bedingung die Bestätigungen aller anderen Wettkämpfe mit.
     */
    perEventDay?: boolean
    perCompetition?: boolean
    approvedParticipants: Array<ParticipantForEventDto & {note?: string}>
}

const ParticipantRequirementApproveManuallyForEventDialog = (
    props: BaseEntityDialogProps<ParticipantRequirementApproveManuallyForEventForm>,
) => {
    const {eventId} = eventRoute.useParams()
    const feedback = useFeedback()
    const {t} = useTranslation()
    const [competitionId, setCompetitionId] = useState<string | null>(null)
    const [competitions, setCompetitions] = useState<ParticipantScanCompetitionDto[]>([])
    const [todayEventDayId, setTodayEventDayId] = useState<string | null>(null)

    const perCompetition = props.entity?.perCompetition === true
    const perEventDay = props.entity?.perEventDay === true

    // Der Rahmen kommt vom Server: den heutigen Wettkampftag bestimmt dieselbe Regel, die ihn
    // beim Speichern einträgt - eine zweite Quelle für "heute" wäre eine zu viel.
    useFetch(signal => getEventScanScope({signal, path: {eventId}}), {
        onResponse: ({data, error}) => {
            if (error || !data) {
                feedback.error(
                    t('common.load.error.multiple.short', {
                        entity: t('event.competition.competitions'),
                    }),
                )
                return
            }
            setCompetitions(data.competitions)
            setTodayEventDayId(data.todayEventDayId ?? null)
        },
        preCondition: () => props.entity?.requirementId != null,
        deps: [eventId, props.entity],
    })

    const editAction = (formData: ParticipantRequirementApproveManuallyForEventForm) => {
        return approveParticipantRequirementsForEvent({
            path: {eventId},
            body: {
                requirementId: formData.requirementId,
                approvedParticipants: formData.approvedParticipants.map(p => ({
                    id: p.id,
                    note: p.note,
                })),
                namedParticipantId: formData.namedParticipantId,
                competitionId: perCompetition ? (competitionId ?? undefined) : undefined,
            },
        })
    }

    const {data: participantsData, pending: participantsPending} = useFetch(
        signal =>
            getParticipantsForEventInApp({
                signal,
                path: {eventId},
                query: {
                    sort: JSON.stringify([
                        {field: 'FIRSTNAME', direction: 'ASC'},
                        {field: 'LASTNAME', direction: 'ASC'},
                    ]),
                },
            }),
        {
            preCondition: () => props.entity?.requirementId != null || false,
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('club.participant.title'),
                        }),
                    )
                }
            },
            deps: [props.entity],
        },
    )

    // Filter participants based on requirement type
    const filteredParticipants = useMemo(() => {
        if (!participantsData?.data) return []

        return participantsData.data.filter(p => {
            // If it's a named participant requirement, only show participants with matching namedParticipantId
            if (!props.entity?.isGlobal && props.entity?.namedParticipantId) {
                return p.namedParticipantIds?.includes(props.entity.namedParticipantId) ?? false
            }
            // For global requirements, show all participants
            return true
        })
    }, [participantsData?.data, props.entity?.isGlobal, props.entity?.namedParticipantId])

    const formContext = useForm<ParticipantRequirementApproveManuallyForEventForm>()

    const options = filteredParticipants.map(p => ({
        ...p,
        note: p.participantRequirementsChecked?.find(r => r.id === props.entity?.requirementId)
            ?.note,
    }))

    // Angehakt ist, wessen Bestätigung DIESEN Rahmen abdeckt - dieselbe Regel wie an der Waage
    // und vor dem Start. Ohne sie stünde jemand, der für einen anderen Wettkampf gewogen wurde,
    // hier als erledigt, und das Speichern schriebe seine Bestätigung nie für den gewählten.
    const scanContext = {todayEventDayId, competitionId}
    const isApproved = useCallback(
        (p: ParticipantForEventDto) =>
            (p.participantRequirementsChecked ?? []).some(
                r =>
                    r.id === props.entity?.requirementId &&
                    covers({perEventDay, perCompetition}, r, scanContext),
            ),
        [props.entity, perEventDay, perCompetition, todayEventDayId, competitionId],
    )

    const onOpen = useCallback(() => {
        formContext.reset(
            props.entity
                ? {
                      ...props.entity,
                      approvedParticipants: options.filter(isApproved),
                  }
                : {},
        )
    }, [props.entity, filteredParticipants, isApproved])

    // Ein Wettkampfwechsel ist ein anderer Zustand: die Liste muss neu vorbelegt werden, sonst
    // trüge der Abgleich die Häkchen des vorigen Wettkampfs in den neuen.
    const onCompetitionChange = (id: string) => {
        setCompetitionId(id)
        formContext.setValue(
            'approvedParticipants',
            options.filter(p =>
                (p.participantRequirementsChecked ?? []).some(
                    r =>
                        r.id === props.entity?.requirementId &&
                        covers({perEventDay, perCompetition}, r, {
                            todayEventDayId,
                            competitionId: id,
                        }),
                ),
            ),
        )
    }

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            editAction={editAction}
            maxWidth={'xl'}
            fullWidth={true}
            title={`${props.entity?.requirementName}${
                props.entity && !props.entity.isGlobal && props.entity.namedParticipantName
                    ? ` (${props.entity.namedParticipantName})`
                    : ''
            }`}>
            <Stack spacing={2}>
                {perCompetition && (
                    <TextField
                        select
                        fullWidth
                        label={t('qrParticipant.competitionLabel')}
                        helperText={t('event.participantRequirement.scope.competitionHelp')}
                        value={competitionId ?? ''}
                        onChange={e => onCompetitionChange(e.target.value)}>
                        {competitions.map(competition => (
                            <MenuItem key={competition.id} value={competition.id}>
                                {competitionLabel(competition)}
                            </MenuItem>
                        ))}
                    </TextField>
                )}

                {perCompetition && competitionId === null && (
                    <Alert severity="info">
                        {t('event.participantRequirement.scope.chooseCompetition')}
                    </Alert>
                )}

                {perEventDay && (
                    <Alert severity={todayEventDayId === null ? 'warning' : 'info'}>
                        {todayEventDayId === null
                            ? t('qrParticipant.noEventDayToday')
                            : t('event.participantRequirement.scope.todayOnly')}
                    </Alert>
                )}

                {participantsPending ? (
                    <Throbber />
                ) : perCompetition && competitionId === null ? null : (
                    <FormInputTransferList
                        name={'approvedParticipants'}
                        options={options}
                        labelLeft={t('event.participantRequirement.participantsOpen')}
                        labelRight={t('event.participantRequirement.participantsApproved')}
                        renderValue={v => ({
                            primary: `${v.firstname} ${v.lastname}`,
                            secondary: `${v.gender} - ${v.year} - ${v.external ? `${v.externalClubName} (${v.clubName})` : v.clubName}`,
                        })}
                    />
                )}
            </Stack>
        </EntityDialog>
    )
}
export default ParticipantRequirementApproveManuallyForEventDialog

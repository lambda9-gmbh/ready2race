import EntityDialog from '@components/EntityDialog.tsx'
import {BaseEntityDialogProps} from '@utils/types.ts'
import {ParticipantForEventDto} from '@api/types.gen.ts'
import {Stack} from '@mui/material'
import {
    approveParticipantRequirementsForEvent,
    getParticipantsForEventInApp
} from '@api/sdk.gen.ts'
import {useForm} from 'react-hook-form-mui'
import {useCallback, useMemo} from 'react'
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
    approvedParticipants: Array<ParticipantForEventDto>
}

const ParticipantRequirementApproveManuallyForEventDialog = (
    props: BaseEntityDialogProps<ParticipantRequirementApproveManuallyForEventForm>,
) => {
    const {eventId} = eventRoute.useParams()
    const feedback = useFeedback()
    const {t} = useTranslation()

    const editAction = (formData: ParticipantRequirementApproveManuallyForEventForm) => {
        return approveParticipantRequirementsForEvent({
            path: {eventId},
            body: {
                requirementId: formData.requirementId,
                approvedParticipants: formData.approvedParticipants.map(p => p.id),
                namedParticipantId: formData.namedParticipantId,
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

    const onOpen = useCallback(() => {
        formContext.reset(
            props.entity
                ? {
                      ...props.entity,
                      approvedParticipants:
                          filteredParticipants.filter(p =>
                              p.participantRequirementsChecked?.some(
                                  r => r.id === props.entity?.requirementId,
                              ),
                          ) ?? [],
                  }
                : {},
        )
    }, [props.entity, filteredParticipants])

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
            <Stack>
                {participantsPending ? (
                    <Throbber />
                ) : (
                    <FormInputTransferList
                        name={'approvedParticipants'}
                        options={filteredParticipants}
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

import EntityDialog from '@components/EntityDialog.tsx'
import {BaseEntityDialogProps} from '@utils/types.ts'
import {ParticipantForEventDto} from '@api/types.gen.ts'
import {Stack} from '@mui/material'
import {approveParticipantRequirementsForEvent, getParticipantsForEvent} from '@api/sdk.gen.ts'
import {useForm} from 'react-hook-form-mui'
import {useCallback} from 'react'
import {eventRoute} from '@routes'
import FormInputTransferList from '@components/form/input/FormInputTransferList.tsx'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import Throbber from '@components/Throbber.tsx'

export type ParticipantRequirementApproveManuallyForEventForm = {
    requirementId: string
    requirementName: string
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
            },
        })
    }

    const {data: participantsData, pending: participantsPending} = useFetch(
        signal =>
            getParticipantsForEvent({
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

    const formContext = useForm<ParticipantRequirementApproveManuallyForEventForm>()

    const onOpen = useCallback(() => {
        formContext.reset(
            props.entity
                ? {
                      ...props.entity,
                      approvedParticipants:
                          participantsData?.data.filter(p =>
                              p.participantRequirementsChecked?.some(
                                  r => r.id === props.entity?.requirementId,
                              ),
                          ) ?? [],
                  }
                : {},
        )
    }, [props.entity, participantsData?.data])

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            editAction={editAction}
            maxWidth={'xl'}
            fullWidth={true}
            title={props.entity?.requirementName}>
            <Stack>
                {participantsPending ? (
                    <Throbber />
                ) : (
                    <FormInputTransferList
                        name={'approvedParticipants'}
                        options={participantsData?.data ?? []}
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

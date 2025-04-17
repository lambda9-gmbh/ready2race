import {BaseEntityDialogProps} from '@utils/types.ts'
import {
    ClubSearchDto,
    CompetitionDto,
    CompetitionRegistrationNamedParticipantUpsertDto,
    CompetitionRegistrationTeamDto,
    CompetitionRegistrationTeamUpsertDto,
} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {CheckboxButtonGroup, useForm, useWatch} from 'react-hook-form-mui'
import {useCallback, useMemo} from 'react'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {
    addCompetitionRegistration,
    getClubNames,
    getClubParticipants,
    updateCompetitionRegistration,
} from '@api/sdk.gen.ts'
import {TeamNamedParticipantLabel} from '@components/eventRegistration/TeamNamedParticipantLabel.tsx'
import {TeamParticipantAutocomplete} from '@components/eventRegistration/TeamParticipantAutocomplete.tsx'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'

type CompetitionRegistrationForm = {
    id?: string
    clubId?: string
    optionalFees?: Array<string>
    namedParticipants?: Array<CompetitionRegistrationNamedParticipantUpsertDto>
}

const CompetitionRegistrationDialog = (
    props: BaseEntityDialogProps<CompetitionRegistrationTeamDto> & {
        competition: CompetitionDto
        eventId: string
    },
) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const formContext = useForm<CompetitionRegistrationForm>()

    const clubId = useWatch({
        control: formContext.control,
        name: 'clubId',
        defaultValue: props.entity?.clubId,
    })

    const {data: clubs} = useFetch(
        signal =>
            getClubNames({
                signal,
                query: {
                    sort: JSON.stringify([{field: 'NAME', direction: 'ASC'}]),
                    eventId: props.eventId,
                },
            }),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('club.club'),
                        }),
                    )
                }
            },
            deps: [props.eventId],
        },
    )

    const {data: participantsData, pending: participantsPending} = useFetch(
        signal =>
            getClubParticipants({
                signal,
                path: {clubId: clubId!!},
                query: {
                    sort: JSON.stringify([
                        {field: 'EXTERNAL', direction: 'ASC'},
                        {field: 'EXTERNAL_CLUB_NAME', direction: 'ASC'},
                        {field: 'FIRSTNAME', direction: 'ASC'},
                        {field: 'LASTNAME', direction: 'ASC'},
                    ]),
                },
            }),
        {
            preCondition: () => clubId != null,
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('club.participant.title'),
                        }),
                    )
                }
            },
            deps: [clubId],
        },
    )

    const addAction = (formData: CompetitionRegistrationForm) => {
        return addCompetitionRegistration({
            path: {eventId: props.eventId, competitionId: props.competition.id},
            body: mapFormToRequest(formData),
        })
    }

    const editAction = (
        formData: CompetitionRegistrationForm,
        entity: CompetitionRegistrationTeamDto,
    ) => {
        return updateCompetitionRegistration({
            path: {
                eventId: props.eventId,
                competitionId: props.competition.id,
                competitionRegistrationId: entity.id,
            },
            body: mapFormToRequest(formData),
        })
    }

    const defaultValues: CompetitionRegistrationForm = {
        namedParticipants: [],
        optionalFees: [],
    }

    const optionalFees = useMemo(
        () => props.competition.properties.fees?.filter(f => !f.required) ?? [],
        [props.competition.id],
    )

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])

    return (
        <EntityDialog
            {...props}
            open={true}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}>
            <Stack spacing={2}>
                {props.entity?.id == null && (
                    <FormInputAutocomplete
                        label={t('club.club')}
                        autocompleteProps={{getOptionLabel: (o: ClubSearchDto) => o.name}}
                        matchId
                        name={'clubId'}
                        required={true}
                        options={clubs?.data ?? []}
                    />
                )}
                {props.competition.properties.namedParticipants.map(
                    (namedParticipant, namedParticipantIndex) => (
                        <Stack key={`${namedParticipant.id}`} spacing={1}>
                            <TeamNamedParticipantLabel namedParticipant={namedParticipant} />
                            <TeamParticipantAutocomplete
                                name={`namedParticipants.${namedParticipantIndex}`}
                                label={t('club.participant.title')}
                                loading={clubId != null && participantsPending}
                                disabled={clubId == null}
                                options={participantsData?.data ?? []}
                                required={true}
                                transform={{
                                    input: value =>
                                        participantsData?.data.filter(p =>
                                            value?.participantIds?.some(
                                                (id: string) => id === p.id,
                                            ),
                                        ) ?? [],
                                    output: (_, value) => {
                                        return {
                                            namedParticipantId: namedParticipant.id,
                                            participantIds: value.map(p => p.id),
                                        }
                                    },
                                }}
                                count={
                                    namedParticipant.countMales +
                                    namedParticipant.countFemales +
                                    namedParticipant.countMixed +
                                    namedParticipant.countNonBinary
                                }
                            />
                        </Stack>
                    ),
                )}
                {optionalFees.length > 0 && (
                    <CheckboxButtonGroup
                        label={t('event.registration.optionalFee')}
                        name={`optionalFees`}
                        labelKey={'name'}
                        options={optionalFees}
                        row
                    />
                )}
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(
    formData: CompetitionRegistrationForm,
): CompetitionRegistrationTeamUpsertDto {
    return {id: '', ...formData}
}

function mapDtoToForm(dto: CompetitionRegistrationTeamDto): CompetitionRegistrationForm {
    return {
        id: dto.id,
        clubId: dto.clubId,
        optionalFees: dto.optionalFees.map(f => f.feeId),
        namedParticipants: dto.namedParticipants.map(np => ({
            namedParticipantId: np.namedParticipantId,
            participantIds: np.participants.map(p => p.id),
        })),
    }
}

export default CompetitionRegistrationDialog

import {BaseEntityDialogProps} from '@utils/types.ts'
import {
    ClubSearchDto,
    CompetitionDto,
    CompetitionRegistrationNamedParticipantUpsertDto,
    CompetitionRegistrationTeamDto,
    CompetitionRegistrationTeamUpsertDto,
    EventRegistrationNamedParticipantDto,
} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {CheckboxButtonGroup, useForm, useWatch} from 'react-hook-form-mui'
import {useCallback, useMemo, useState} from 'react'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {
    addCompetitionRegistration,
    getClubNames,
    getClubParticipants,
    getCompetitionRegistrations,
    updateCompetitionRegistration,
} from '@api/sdk.gen.ts'
import {TeamNamedParticipantLabel} from '@components/eventRegistration/TeamNamedParticipantLabel.tsx'
import {TeamParticipantAutocomplete} from '@components/eventRegistration/TeamParticipantAutocomplete.tsx'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'
import {useUser} from '@contexts/user/UserContext.ts'
import {createRegistrationGlobal} from '@authorization/privileges.ts'

type CompetitionRegistrationForm = {
    id?: string
    clubId?: string
    optionalFees?: Array<string>
    namedParticipants?: Array<CompetitionRegistrationNamedParticipantUpsertDto>
}

const CompetitionRegistrationDialog = ({
    competition,
    eventId,
    ...props
}: BaseEntityDialogProps<CompetitionRegistrationTeamDto> & {
    competition: CompetitionDto
    eventId: string
}) => {
    const {t} = useTranslation()
    const user = useUser()
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
                    eventId,
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
            deps: [eventId],
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

    const [reloadCompetitionRegistrations, setReloadCompetitionRegistrations] = useState(false)
    const {data: competitionRegistrations} = useFetch(
        signal =>
            getCompetitionRegistrations({
                signal,
                path: {eventId: eventId, competitionId: competition.id},
            }),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.registration.registrations'),
                        }),
                    )
                }
            },
            deps: [eventId, competition.id, reloadCompetitionRegistrations],
        },
    )

    const participants = useMemo(() => {
        return participantsData?.data ?? []
    }, [participantsData])

    const getFilteredParticipants = useCallback(
        (namedParticipant: EventRegistrationNamedParticipantDto) => {
            return participants.filter(p => {
                switch (p.gender) {
                    case 'M':
                        return namedParticipant.countMales > 0 || namedParticipant.countMixed > 0
                    case 'F':
                        return namedParticipant.countFemales > 0 || namedParticipant.countMixed > 0
                    case 'D':
                        return (
                            namedParticipant.countNonBinary > 0 || namedParticipant.countMixed > 0
                        )
                }
            })
        },
        [participants],
    )

    const addAction = (formData: CompetitionRegistrationForm) => {
        return addCompetitionRegistration({
            path: {eventId, competitionId: competition.id},
            body: mapFormToRequest(formData),
        })
    }

    const editAction = (
        formData: CompetitionRegistrationForm,
        entity: CompetitionRegistrationTeamDto,
    ) => {
        return updateCompetitionRegistration({
            path: {
                eventId,
                competitionId: competition.id,
                competitionRegistrationId: entity.id,
            },
            body: mapFormToRequest(formData),
        })
    }

    const defaultValues: CompetitionRegistrationForm = {
        namedParticipants: [],
        optionalFees: [],
        clubId: user.loggedIn ? user.clubId : undefined,
    }

    const optionalFees = useMemo(
        () => competition.properties.fees?.filter(f => !f.required) ?? [],
        [competition.id],
    )

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
        setReloadCompetitionRegistrations(prev => !prev)
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
                {props.entity?.id == null && user.checkPrivilege(createRegistrationGlobal) && (
                    <FormInputAutocomplete
                        label={t('club.club')}
                        autocompleteProps={{getOptionLabel: (o: ClubSearchDto) => o.name}}
                        matchId
                        name={'clubId'}
                        required={true}
                        options={clubs?.data ?? []}
                    />
                )}
                {competition.properties.namedParticipants.map(
                    (namedParticipant, namedParticipantIndex) => (
                        <Stack key={`${namedParticipant.id}`} spacing={1}>
                            <TeamNamedParticipantLabel namedParticipant={namedParticipant} />
                            <TeamParticipantAutocomplete
                                name={`namedParticipants.${namedParticipantIndex}`}
                                label={t('club.participant.title')}
                                loading={clubId != null && participantsPending}
                                disabled={clubId == null}
                                options={getFilteredParticipants(namedParticipant)}
                                required={true}
                                namedParticipantsPath={'namedParticipants'}
                                transform={{
                                    input: value =>
                                        participants.filter(p =>
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
                                countMales={namedParticipant.countMales}
                                countFemales={namedParticipant.countFemales}
                                countMixed={namedParticipant.countMixed}
                                countNonBinary={namedParticipant.countNonBinary}
                                disabledParticipants={competitionRegistrations?.data
                                    ?.flatMap(cr =>
                                        cr.namedParticipants.flatMap(np => np.participants),
                                    )
                                    .filter(
                                        p =>
                                            props.entity?.namedParticipants
                                                .flatMap(np => np.participants)
                                                .find(
                                                    entityParticipant =>
                                                        entityParticipant.id === p.id,
                                                ) === undefined,
                                    )}
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

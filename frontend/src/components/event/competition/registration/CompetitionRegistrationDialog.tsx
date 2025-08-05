import {BaseEntityDialogProps} from '@utils/types.ts'
import {
    ClubSearchDto,
    CompetitionDto,
    CompetitionRegistrationNamedParticipantUpsertDto,
    CompetitionRegistrationTeamDto,
    CompetitionRegistrationTeamUpsertDto, EventDto,
    EventRegistrationNamedParticipantDto, RegistrationInvoiceType,
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
import {updateRegistrationGlobal} from '@authorization/privileges.ts'
import {FormInputSelect} from "@components/form/input/FormInputSelect.tsx";

const registrationTypes: RegistrationInvoiceType[] = ['REGULAR', 'LATE']
type RegistrationType = (typeof registrationTypes)[number]

type CompetitionRegistrationForm = {
    id?: string
    clubId?: string
    optionalFees?: Array<string>
    namedParticipants?: Array<CompetitionRegistrationNamedParticipantUpsertDto>
    asRegistrationType: RegistrationType
}

const CompetitionRegistrationDialog = ({
    competition,
    eventData,
    ...props
}: BaseEntityDialogProps<CompetitionRegistrationTeamDto> & {
    competition: CompetitionDto
    eventData: EventDto
}) => {
    const {t} = useTranslation()
    const user = useUser()
    const feedback = useFeedback()

    const formContext = useForm<CompetitionRegistrationForm>()

    const globalPrivilege = user.checkPrivilege(updateRegistrationGlobal)

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
                    eventId: eventData.id,
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
            deps: [eventData],
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
                path: {eventId: eventData.id, competitionId: competition.id},
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
            deps: [eventData, competition.id, reloadCompetitionRegistrations],
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
            path: {eventId: eventData.id, competitionId: competition.id},
            body: mapFormToRequest(formData),
            query: {registrationType: globalPrivilege ? formData.asRegistrationType : undefined}
        })
    }

    const editAction = (
        formData: CompetitionRegistrationForm,
        entity: CompetitionRegistrationTeamDto,
    ) => {
        return updateCompetitionRegistration({
            path: {
                eventId: eventData.id,
                competitionId: competition.id,
                competitionRegistrationId: entity.id,
            },
            body: mapFormToRequest(formData),
            query: {registrationType: globalPrivilege ? formData.asRegistrationType : undefined}
        })
    }

    const defaultValues: CompetitionRegistrationForm = {
        namedParticipants: [],
        optionalFees: [],
        clubId: user.loggedIn ? user.clubId : undefined,
        asRegistrationType: 'LATE',
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
            <Stack spacing={4}>
                {globalPrivilege && competition.properties.lateRegistrationAllowed && eventData.registrationAvailableTo && new Date(eventData.registrationAvailableTo) < new Date() &&
                    <FormInputSelect
                        name={'asRegistrationType'}
                        label={t('event.competition.registration.dialog.registrationType.label')}
                        options={registrationTypes.map( type => ({
                            id: type,
                            label: t(`event.competition.registration.dialog.registrationType.${type}`)
                        } satisfies {id: RegistrationType, label: string} ))}
                        required
                    />
                }
                <Stack spacing={2}>
                    {props.entity?.id == null && globalPrivilege && (
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
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(
    formData: CompetitionRegistrationForm,
): CompetitionRegistrationTeamUpsertDto {
    return {
        id: formData.id ?? '',
        clubId: formData.clubId,
        optionalFees: formData.optionalFees,
        namedParticipants: formData.namedParticipants,
    }
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
        asRegistrationType: dto.isLate ? 'LATE' : 'REGULAR',
    }
}

export default CompetitionRegistrationDialog

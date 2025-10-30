import {BaseEntityDialogProps} from '@utils/types.ts'
import {
    ClubSearchDto,
    CompetitionDto,
    CompetitionRegistrationNamedParticipantUpsertDto,
    CompetitionRegistrationDto,
    CompetitionRegistrationTeamUpsertDto,
    EventDto,
    EventRegistrationNamedParticipantDto,
    RegistrationInvoiceType,
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
    getClubParticipantsForEvent,
    getCompetitionRegistrations,
    getRatingCategoriesForEvent,
    updateCompetitionRegistration,
} from '@api/sdk.gen.ts'
import {TeamNamedParticipantLabel} from '@components/eventRegistration/TeamNamedParticipantLabel.tsx'
import {TeamParticipantAutocomplete} from '@components/eventRegistration/TeamParticipantAutocomplete.tsx'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateRegistrationGlobal, updateResultGlobal} from '@authorization/privileges.ts'
import {FormInputSelect} from '@components/form/input/FormInputSelect.tsx'
import {currentlyInTimespan} from '@utils/helpers.ts'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'
import {ResultInputTeamInfo} from '@components/event/competition/registration/ChallengeResultDialog.tsx'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'

// TODO: validate/sanitize basepath (also in routes.tsx)
const basepath = document.getElementById('ready2race-root')!.dataset.basepath

const registrationTypes: RegistrationInvoiceType[] = ['REGULAR', 'LATE']
type RegistrationType = (typeof registrationTypes)[number]

type CompetitionRegistrationForm = {
    id?: string
    clubId?: string
    optionalFees?: Array<string>
    namedParticipants?: Array<CompetitionRegistrationNamedParticipantUpsertDto>
    asRegistrationType: RegistrationType
    ratingCategory: string
    includeResult: boolean
}

const CompetitionRegistrationDialog = ({
    competition,
    eventData,
    openResultDialog,
    ...props
}: BaseEntityDialogProps<CompetitionRegistrationDto> & {
    competition: CompetitionDto
    eventData: EventDto
    openResultDialog: (reg: ResultInputTeamInfo) => void
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

    const ratingCategoryId = useWatch({
        control: formContext.control,
        name: 'ratingCategory',
        defaultValue: props.entity?.ratingCategory?.id ?? '',
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
            getClubParticipantsForEvent({
                signal,
                path: {clubId: clubId!!},
                query: {
                    eventId: eventData.id,
                    ratingCategoryId: takeIfNotEmpty(ratingCategoryId),
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
            deps: [clubId, ratingCategoryId],
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
        return participantsData ?? []
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

    const {data: ratingCategories} = useFetch(
        signal =>
            getRatingCategoriesForEvent({
                signal,
                path: {eventId: eventData.id},
            }),
        {
            onResponse: ({error}) =>
                error &&
                feedback.error(
                    t('common.load.error.multiple.short', {
                        entity: t('configuration.ratingCategory.ratingCategories'),
                    }),
                ),
        },
    )

    const ratingCategoryOptions =
        (ratingCategories?.length ?? 0) > 0
            ? [
                  ...(competition.properties.ratingCategoryRequired
                      ? []
                      : [
                            {
                                id: 'none',
                                label: t('common.form.select.none'),
                            },
                        ]),
                  ...(ratingCategories?.map(dto => ({
                      id: dto.ratingCategory.id,
                      label: dto.ratingCategory.name,
                  })) ?? []),
              ]
            : []

    const addAction = async (formData: CompetitionRegistrationForm) => {
        const registerRes = await addCompetitionRegistration({
            path: {eventId: eventData.id, competitionId: competition.id},
            body: mapFormToRequest(formData),
            query: {
                registrationType: globalPrivilege ? formData.asRegistrationType : undefined,
            },
        })
        if (
            !registerRes.error &&
            eventData.challengeEvent &&
            formContext.getValues('includeResult')
        ) {
            openResultDialog(registerRes.data)
        }
        return registerRes
    }

    const editAction = (
        formData: CompetitionRegistrationForm,
        entity: CompetitionRegistrationDto,
    ) => {
        return updateCompetitionRegistration({
            path: {
                eventId: eventData.id,
                competitionId: competition.id,
                competitionRegistrationId: entity.id,
            },
            body: mapFormToRequest(formData),
            query: {
                registrationType: globalPrivilege ? formData.asRegistrationType : undefined,
            },
        })
    }

    const adminLatePossible =
        globalPrivilege &&
        competition.properties.lateRegistrationAllowed &&
        eventData.registrationAvailableTo &&
        new Date(eventData.registrationAvailableTo) < new Date()

    const directResultPossible =
        eventData.challengeEvent &&
        ((eventData.allowSelfSubmission &&
            currentlyInTimespan(
                competition.properties.challengeConfig?.startAt,
                competition.properties.challengeConfig?.endAt,
            )) ||
            user.checkPrivilege(updateResultGlobal))

    const defaultValues: CompetitionRegistrationForm = {
        namedParticipants: [],
        optionalFees: [],
        clubId: user.loggedIn ? user.clubId : undefined,
        asRegistrationType: adminLatePossible ? 'LATE' : 'REGULAR',
        ratingCategory: competition.properties.ratingCategoryRequired ? '' : 'none',
        includeResult: directResultPossible,
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
                <Stack spacing={2}>
                    {adminLatePossible && (
                        <FormInputSelect
                            name={'asRegistrationType'}
                            label={t(
                                'event.competition.registration.dialog.registrationType.label',
                            )}
                            options={registrationTypes.map(
                                type =>
                                    ({
                                        id: type,
                                        label: t(
                                            `event.competition.registration.dialog.registrationType.${type}`,
                                        ),
                                    }) satisfies {id: RegistrationType; label: string},
                            )}
                            required
                        />
                    )}
                    {(ratingCategories?.length ?? 0) > 0 && (
                        <FormInputSelect
                            name={'ratingCategory'}
                            label={t('event.competition.registration.ratingCategory')}
                            options={ratingCategoryOptions ?? []}
                            required
                        />
                    )}
                </Stack>

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
                    {directResultPossible && (
                        <FormInputCheckbox
                            name={'includeResult'}
                            label={t('event.competition.registration.challenge.includeResult')}
                            horizontal
                            reverse
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
        ratingCategory: formData.ratingCategory !== 'none' ? formData.ratingCategory : undefined,
        callbackUrl: location.origin + (basepath ? `/${basepath}` : '') + '/challenge/',
    }
}

function mapDtoToForm(dto: CompetitionRegistrationDto): CompetitionRegistrationForm {
    return {
        id: dto.id,
        clubId: dto.clubId,
        optionalFees: dto.optionalFees.map(f => f.feeId),
        namedParticipants: dto.namedParticipants.map(np => ({
            namedParticipantId: np.namedParticipantId,
            participantIds: np.participants.map(p => p.id),
        })),
        asRegistrationType: dto.isLate ? 'LATE' : 'REGULAR',
        ratingCategory: dto.ratingCategory ? dto.ratingCategory.id : 'none',
        includeResult: false,
    }
}

export default CompetitionRegistrationDialog

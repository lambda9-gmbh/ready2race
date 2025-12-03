import {CheckboxButtonGroup, useFieldArray, useFormContext, useWatch} from 'react-hook-form-mui'
import {Box, Button, Divider, IconButton, Paper, Stack} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import {GroupAdd} from '@mui/icons-material'
import {v4 as uuid} from 'uuid'
import {
    EventRegistrationCompetitionDto,
    EventRegistrationNamedParticipantDto,
    RatingCategoryToEventDto,
} from '../../api'
import {EventRegistrationCompetitionFeeCard} from './EventRegistrationCompetitionFeeCard.tsx'
import {useTranslation} from 'react-i18next'
import {useCallback, useMemo} from 'react'
import {TeamParticipantAutocomplete} from '@components/eventRegistration/TeamParticipantAutocomplete.tsx'
import {TeamNamedParticipantLabel} from '@components/eventRegistration/TeamNamedParticipantLabel.tsx'
import {
    EventRegistrationFormData,
    EventRegistrationParticipantFormData,
} from '../../pages/eventRegistration/EventRegistrationCreatePage.tsx'
import {FormInputSelect} from '@components/form/input/FormInputSelect.tsx'
import {useEventRegistration} from '@contexts/eventRegistration/EventRegistrationContext.ts'

const TeamInput = (props: {
    competition: EventRegistrationCompetitionDto
    competitionIndex: number
    teamIndex: number
    participants: EventRegistrationParticipantFormData[]
    onRemove: (id: number) => void
    locked: boolean
}) => {
    const {t} = useTranslation()
    const {ratingCategories} = useEventRegistration()
    const formContext = useFormContext<EventRegistrationFormData>()

    const getFilteredParticipants = useCallback(
        (namedParticipant: EventRegistrationNamedParticipantDto) => {
            return props.participants.filter(p => {
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
        [props.participants],
    )

    // Watch the current team's named participants to get all participant IDs
    const teamNamedParticipants = useWatch({
        control: formContext.control,
        name: `competitionRegistrations.${props.competitionIndex}.teams.${props.teamIndex}.namedParticipants`,
    })

    // Collect all participant IDs in this team
    const teamParticipantIds = useMemo(() => {
        if (!teamNamedParticipants) return []
        return teamNamedParticipants.flatMap(np => np?.participantIds || [])
    }, [teamNamedParticipants])

    // Mark invalid rating categories as disabled based on age restrictions
    const ratingCategoryOptions = useMemo(() => {
        const baseOptions = props.competition.ratingCategoryRequired
            ? []
            : [
                  {
                      id: 'none',
                      label: t('common.form.select.none'),
                      disabled: false,
                  },
              ]

        return [
            ...baseOptions,
            ...ratingCategories.map(rc => ({
                id: rc.ratingCategory.id,
                label: rc.ratingCategory.name,
                disabled: !isRatingCategoryValidForTeam(rc, teamParticipantIds, props.participants),
            })),
        ]
    }, [
        props.competition.ratingCategoryRequired,
        ratingCategories,
        teamParticipantIds,
        props.participants,
        t,
    ])

    return (
        <Paper sx={{p: {xs: 1, sm: 2}}} elevation={2}>
            <Stack rowGap={2}>
                <Stack spacing={2}>
                    {props.competition.namedParticipant?.map(
                        (namedParticipant, namedParticipantIndex) => (
                            <Stack rowGap={1} flexWrap={'wrap'} key={namedParticipant.id}>
                                <TeamNamedParticipantLabel namedParticipant={namedParticipant} />
                                <TeamParticipantAutocomplete
                                    disabled={props.locked}
                                    name={`competitionRegistrations.${props.competitionIndex}.teams.${props.teamIndex}.namedParticipants.${namedParticipantIndex}`}
                                    key={`${namedParticipant.id}`}
                                    label={t('club.participant.title')}
                                    required={true}
                                    competitionPath={`competitionRegistrations.${props.competitionIndex}`}
                                    options={getFilteredParticipants(namedParticipant)}
                                    transform={{
                                        input: value =>
                                            props.participants.filter(p =>
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
                                />
                            </Stack>
                        ),
                    )}
                </Stack>
                {(props.competition.fees?.filter(f => !f.required)?.length ?? 0) > 0 && (
                    <CheckboxButtonGroup
                        disabled={props.locked}
                        label={t('event.registration.optionalFee')}
                        name={`competitionRegistrations.${props.competitionIndex}.teams.${props.teamIndex}.optionalFees`}
                        options={props.competition.fees?.filter(f => !f.required) ?? []}
                        row
                    />
                )}
                {ratingCategories.length > 0 && (
                    <Box sx={{my: 1, maxWidth: {xs: '100%', sm: 300}}}>
                        <FormInputSelect
                            name={`competitionRegistrations.${props.competitionIndex}.teams.${props.teamIndex}.ratingCategory`}
                            options={ratingCategoryOptions}
                            required
                            label={t('event.competition.registration.ratingCategory')}
                            disabled={props.locked}
                            rules={{
                                validate: (value: string) => {
                                    if (!value || value === 'none') return true
                                    const selectedOption = ratingCategoryOptions.find(
                                        opt => opt.id === value,
                                    )
                                    return (
                                        !selectedOption?.disabled ||
                                        t('event.competition.registration.ratingCategoryInvalid')
                                    )
                                },
                            }}
                        />
                    </Box>
                )}

                <IconButton
                    sx={{alignSelf: 'end', cursor: 'pointer', minWidth: '44px', minHeight: '44px'}}
                    disabled={props.locked}
                    onClick={() => props.onRemove(props.teamIndex)}>
                    <DeleteIcon />
                </IconButton>
            </Stack>
        </Paper>
    )
}

const EventRegistrationTeamsForm = (props: {
    index: number
    competition: EventRegistrationCompetitionDto
    isLate: boolean
}) => {
    const formContext = useFormContext<EventRegistrationFormData>()

    const {
        fields: teams,
        append,
        remove,
    } = useFieldArray({
        control: formContext.control,
        name: `competitionRegistrations.${props.index}.teams`,
        keyName: 'fieldId',
    })

    const participants: EventRegistrationParticipantFormData[] = useWatch({
        control: formContext.control,
        name: `participants`,
    })

    const participantOptions = useMemo(
        () =>
            [...participants].sort((a, b) =>
                b.externalClubName === a.externalClubName
                    ? -b.firstname.localeCompare(a.firstname)
                    : b.externalClubName == null
                      ? 1
                      : a.externalClubName == null
                        ? -1
                        : -b.externalClubName.localeCompare(a.externalClubName),
            ),
        participants,
    )

    return (
        <Stack padding={{xs: 0.5, sm: 1}}>
            <EventRegistrationCompetitionFeeCard competition={props.competition} />
            <Stack spacing={1} padding={{xs: 0.5, sm: 1}}>
                {teams.map((team, teamIndex) => (
                    <TeamInput
                        key={team.fieldId}
                        competition={props.competition}
                        competitionIndex={props.index}
                        teamIndex={teamIndex}
                        participants={participantOptions}
                        onRemove={remove}
                        locked={team.locked}
                    />
                ))}
                <Button
                    disabled={props.isLate && !props.competition.lateRegistrationAllowed}
                    sx={{
                        minHeight: '44px',
                        cursor: 'pointer',
                        width: {xs: '100%', sm: 'auto'},
                    }}
                    onClick={() => {
                        append({
                            id: uuid(),
                            namedParticipants: [],
                            optionalFees: [],
                            locked: false,
                            isLate: props.isLate,
                            ratingCategory: props.competition.ratingCategoryRequired ? '' : 'none',
                        })
                    }}>
                    <GroupAdd />
                </Button>
            </Stack>
            <Divider />
        </Stack>
    )
}

/**
 * Check if a rating category's age restriction is valid for all participants in a team
 */
const isRatingCategoryValidForTeam = (
    ratingCategory: RatingCategoryToEventDto,
    teamParticipantIds: string[],
    allParticipants: EventRegistrationParticipantFormData[],
): boolean => {
    // If no age restriction, category is valid for everyone
    if (!ratingCategory.yearFrom && !ratingCategory.yearTo) {
        return true
    }

    // Get all participants in this team
    const teamParticipants = allParticipants.filter(p => teamParticipantIds.includes(p.id))

    // Check if all participants meet the age restriction
    return teamParticipants.every(participant => {
        // If participant has no year, we can't validate - assume invalid
        if (!participant.year) {
            return false
        }

        const meetsMinAge = !ratingCategory.yearFrom || participant.year >= ratingCategory.yearFrom
        const meetsMaxAge = !ratingCategory.yearTo || participant.year <= ratingCategory.yearTo

        return meetsMinAge && meetsMaxAge
    })
}

export default EventRegistrationTeamsForm

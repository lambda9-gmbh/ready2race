import {
    CheckboxButtonGroup,
    useFieldArray,
    useFormContext,
    useWatch,
} from 'react-hook-form-mui'
import {Button, Chip, Divider, IconButton, Paper, Stack, Typography} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import {GroupAdd} from '@mui/icons-material'
import {v4 as uuid} from 'uuid'
import {
    EventRegistrationCompetitionDto,
    EventRegistrationNamedParticipantDto,
} from '../../api'
import {EventRegistrationPriceTooltip} from './EventRegistrationPriceTooltip.tsx'
import {useTranslation} from 'react-i18next'
import {useCallback, useMemo} from 'react'
import {TeamParticipantAutocomplete} from '@components/eventRegistration/TeamParticipantAutocomplete.tsx'
import {TeamNamedParticipantLabel} from '@components/eventRegistration/TeamNamedParticipantLabel.tsx'
import {
    EventRegistrationFormData,
    EventRegistrationParticipantFormData
} from "../../pages/eventRegistration/EventRegistrationCreatePage.tsx";

const TeamInput = (props: {
    competition: EventRegistrationCompetitionDto
    competitionIndex: number
    teamIndex: number
    participants: EventRegistrationParticipantFormData[]
    onRemove: (id: number) => void
    locked: boolean
}) => {
    const {t} = useTranslation()

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

    return (
        <Paper sx={{p: 2}} elevation={2}>
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
                <IconButton sx={{alignSelf: 'end'}} disabled={props.locked} onClick={() => props.onRemove(props.teamIndex)}>
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
        <Stack padding={1}>
            <Stack direction={'row'} spacing={1} alignItems={'center'}>
                <Typography>{`${props.competition.name} ${props.competition.identifier}`}</Typography>
                {props.competition.competitionCategory && (
                    <Chip variant={'outlined'} label={props.competition.competitionCategory} />
                )}
                <EventRegistrationPriceTooltip competition={props.competition} />
            </Stack>
            <Stack spacing={1} padding={1}>
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
                    onClick={() => {
                        append({id: uuid(), namedParticipants: [], optionalFees: [], locked: false})
                    }}>
                    <GroupAdd />
                </Button>
            </Stack>
            <Divider />
        </Stack>
    )
}

export default EventRegistrationTeamsForm

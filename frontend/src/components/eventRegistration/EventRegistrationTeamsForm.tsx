import {
    AutocompleteElement,
    CheckboxButtonGroup,
    FieldArrayWithId,
    useFieldArray,
    useFormContext,
    useWatch,
} from 'react-hook-form-mui'
import {
    AutocompleteChangeDetails,
    AutocompleteChangeReason,
    Button,
    Divider,
    IconButton,
    Paper,
    Stack,
    Typography,
} from '@mui/material'
import DeleteIcon from '@mui/icons-material/Delete'
import {GroupAdd} from '@mui/icons-material'
import {v4 as uuid} from 'uuid'
import {
    EventRegistrationCompetitionDto,
    EventRegistrationParticipantUpsertDto,
    EventRegistrationUpsertDto,
} from '../../api'
import {EventRegistrationPriceTooltip} from './EventRegistrationPriceTooltip.tsx'
import {useTranslation} from 'react-i18next'
import {SyntheticEvent, useCallback, useEffect, useMemo, useState} from 'react'

const TeamParticipantAutocomplete = (props: {
    name: string,
    label: string,
    count: number,
    required: boolean,
    options: EventRegistrationParticipantUpsertDto[],
    transform?: {
        input?: ((value: any) => EventRegistrationParticipantUpsertDto[]) | undefined;
        output?: ((event: SyntheticEvent, value: EventRegistrationParticipantUpsertDto[], reason: AutocompleteChangeReason, details?: AutocompleteChangeDetails<EventRegistrationParticipantUpsertDto> | undefined) => any) | undefined
    } | undefined
}) => {
    const [limitReached, setLimitReached] = useState(false)

    const value = useWatch({name: props.name})

    useEffect(() => {
        setLimitReached((value ? ((props.transform?.input?.(value) ?? value ?? []).length >= props.count) : false))
    }, [value])

    const getOptionDisabled = useCallback((option: EventRegistrationParticipantUpsertDto) => {
        return limitReached && !(props.transform?.input?.(value) ?? value ?? []).includes(option)
    }, [limitReached, value, props.transform])

    return (
        <AutocompleteElement
            label={props.label}
            name={props.name}
            transform={props.transform}
            matchId
            required={props.required}
            multiple
            autocompleteProps={{
                size: 'small',
                getOptionDisabled: getOptionDisabled,
                slotProps: {
                    popper: {
                        sx: {
                            '& .MuiAutocomplete-groupLabel': {
                                overflow: 'hidden',
                                whiteSpace: 'nowrap',
                                textOverflow: 'ellipsis',
                            },
                        },
                    },
                },
                sx: {
                    width: 250,
                    minWidth: 250,
                },
                groupBy: option => option.externalClubName || '',
                getOptionLabel: option => `${option.firstname} ${option.lastname}`,
            }}
            options={props.options}
        />
    )
}

const TeamInput = (props: {
    competition: EventRegistrationCompetitionDto,
    competitionIndex: number,
    teamIndex: number,
    participants: EventRegistrationParticipantUpsertDto[],
    onRemove: (id: number) => void,
    team: FieldArrayWithId<EventRegistrationUpsertDto, `competitionRegistrations.${number}.teams`, 'fieldId'>
}) => {

    const {t} = useTranslation()

    return (
        <Paper sx={{p: 2}} elevation={2} key={props.team.fieldId}>
            <Stack
                rowGap={3}
            >
                {
                    props.competition.namedParticipant?.map((namedParticipant, namedParticipantIndex) =>
                        <Stack
                            rowGap={2}
                            columnGap={1}
                            flexWrap={'wrap'}
                            key={namedParticipant.id}>
                            {namedParticipant.name}
                            <TeamParticipantAutocomplete
                                name={`competitionRegistrations.${props.competitionIndex}.teams.${props.teamIndex}.namedParticipants.${namedParticipantIndex}`}
                                key={`${namedParticipant.id}`}
                                label={t('club.participant.title')}
                                required={namedParticipant.required ?? false}
                                options={props.participants}
                                transform={
                                    {
                                        input: (value) => props.participants.filter(p => value?.participantIds?.some(
                                            (id: string) => id === p.id,
                                        )) ?? [],
                                        output: (_, value) => {
                                            return ({
                                                namedParticipantId: namedParticipant.id,
                                                participantIds: value.map(p => p.id),
                                            })
                                        },
                                    }
                                }
                                count={namedParticipant.countMales +
                                    namedParticipant.countFemales +
                                    namedParticipant.countMixed +
                                    namedParticipant.countNonBinary}
                            />
                        </Stack>,
                    )
                }
                <CheckboxButtonGroup
                    label={t('event.registration.optionalFee')}
                    name={`competitionRegistrations.${props.competitionIndex}.teams.${props.teamIndex}.optionalFees`}
                    options={props.competition.fees?.filter(f => !f.required) ?? []}
                    row
                />
                <IconButton sx={{alignSelf: 'end'}} onClick={() => props.onRemove(props.teamIndex)}>
                    <DeleteIcon />
                </IconButton>
            </Stack>
        </Paper>
    )
}

const EventRegistrationTeamsForm = (props: {
    index: number
    competition: EventRegistrationCompetitionDto
}) => {

    const formContext = useFormContext<EventRegistrationUpsertDto>()

    const {fields: teams, append, remove} = useFieldArray({
        control: formContext.control,
        name: `competitionRegistrations.${props.index}.teams`,
        keyName: 'fieldId',
    })

    const participants: EventRegistrationParticipantUpsertDto[] = useWatch({
        control: formContext.control,
        name: `participants`,
    })

    const participantOptions = useMemo(() =>
            [...participants].sort((a, b) =>
                b.externalClubName === a.externalClubName
                    ? -b.firstname.localeCompare(a.firstname)
                    : b.externalClubName == null
                        ? 1
                        : a.externalClubName == null
                            ? -1
                        : -b.externalClubName.localeCompare(a.externalClubName)
            )
        , participants)

    return (
        <Stack padding={1}>
            <Stack direction={'row'} spacing={1} alignItems={'center'}>
                <Typography>{`${props.competition.identifier} ${props.competition.name}`}</Typography>
                <EventRegistrationPriceTooltip competition={props.competition} />
            </Stack>
            <Stack spacing={1} padding={1}>
                {teams.map((team, teamIndex) =>
                    <TeamInput key={team.fieldId} team={team} competition={props.competition}
                               competitionIndex={props.index}
                               teamIndex={teamIndex}
                               participants={participantOptions} onRemove={remove} />,
                )}
                <Button
                    onClick={() => {
                        append({id: uuid(), participants: []})
                    }}>
                    <GroupAdd />
                </Button>
            </Stack>
            <Divider />
        </Stack>
    )
}

export default EventRegistrationTeamsForm

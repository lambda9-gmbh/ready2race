import {CheckboxElement, Controller, useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import {
    Alert,
    Box,
    Button,
    Checkbox,
    Divider,
    FormControlLabel,
    MenuItem,
    Select,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
    useTheme,
} from '@mui/material'
import CompetitionSetupMatch from '@components/event/competition/setup/CompetitionSetupMatch.tsx'
import {useEffect, useState} from 'react'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {
    CompetitionSetupForm,
    CompetitionSetupMatchOrGroupProps,
    fillSeedingList,
    getHighestTeamsCount,
    getNewPlaces,
    getParticipantsFromMatchOrGroup,
    getTeamsCountInMatches,
    setParticipantValuesForMatchOrGroup,
    updateParticipants,
    updatePreviousRoundParticipants,
} from '@components/event/competition/setup/common.ts'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {useTranslation} from 'react-i18next'

type Props = {
    round: {index: number; id: string}
    formContext: UseFormReturn<CompetitionSetupForm>
    removeRound: (index: number) => void
    teamCounts: {
        prevRound: number
        thisRound: number
        nextRound: number
    }
    getRoundTeamCountWithoutThis: (ignoredIndex: number, isGroupRound: boolean) => number
    allowRoundUpdates: {
        value: boolean
        set: (value: boolean) => void
    }
}
const CompetitionSetupRound = ({round, formContext, removeRound, teamCounts, ...props}: Props) => {
    const defaultMatchTeamSize = 2
    const defaultGroupTeamSize = 4

    const theme = useTheme()
    const {t} = useTranslation()

    const watchUseDefaultSeeding = formContext.watch(`rounds.${round.index}.useDefaultSeeding`)

    const watchIsGroupRound = formContext.watch(`rounds.${round.index}.isGroupRound`)

    const [matchesError, setMatchesError] = useState<string | null>(null)

    const {
        fields: matchFields,
        append: appendMatch,
        remove: removeMatch,
    } = useFieldArray({
        control: formContext.control,
        name: `rounds.${round.index}.matches`,
        rules: {
            validate: value => {
                const participants = value.map(match => match.participants.map(p => p.seed)).flat()
                const countedParticipantsMap = participants.reduce<Map<number, number>>(
                    (acc, val) => {
                        const seed = val
                        const old = acc.get(seed)
                        acc.set(seed, old ? old + 1 : 1)
                        return acc
                    },
                    new Map(),
                )
                const duplicateParticipants = Array.from(countedParticipantsMap.entries())
                    .filter(([, count]) => count > 1)
                    .map(([seed]) => seed)

                if (duplicateParticipants.length > 0) {
                    setMatchesError(
                        (duplicateParticipants.length === 1
                            ? t('event.competition.setup.validation.duplicateParticipants.one', {
                                  participants: duplicateParticipants[0],
                              })
                            : t(
                                  'event.competition.setup.validation.duplicateParticipants.multiple',
                                  {participants: duplicateParticipants.join(', ')},
                              )) +
                            ' ' +
                            t('event.competition.setup.validation.duplicateParticipants.message'),
                    )
                    return 'duplicateParticipants'
                }

                setMatchesError(null)
                return undefined
            },
        },
    })

    const watchMatches = formContext.watch(`rounds.${round.index}.matches`)

    /*    const {
            fields: groupFields,
            append: appendGroup,
            remove: removeGroup,
            move: moveGroup,
        } = useFieldArray({
            control: formContext.control,
            name: `rounds.${round.index}.groups`,
        })*/

    const watchGroups = formContext.watch(`rounds.${round.index}.groups`)

    /*const getLowestGroupMatchPosition = (takenPositions?: number[]) => {
        return getLowest(
            [
                ...watchGroups.map(g => g.matches.map(m => m.position)).flat(),
                ...(takenPositions ?? []),
            ],
            1,
        )
    }*/

    const isFirstRound = round.index === 0

    // When appending or removing a match/group, the participants are updated or places are updated
    useEffect(() => {
        if (watchUseDefaultSeeding) {
            updateParticipants(formContext, round.index, true, teamCounts.nextRound, updatePlaces)
        }

        if (props.allowRoundUpdates.value) {
            if (!watchUseDefaultSeeding) {
                // Changes made for the places are overwritten - Because of this "allowRoundUpdates" is necessary to prevent updating directly after resetting the form
                updatePlaces(round.index, true, getTeamsCountInMatches(watchMatches))
            }
        } else {
            props.allowRoundUpdates.set(true)
        }
    }, [
        watchMatches.length,
        watchGroups.length,
        watchIsGroupRound,
        watchUseDefaultSeeding,
        isFirstRound,
    ])

    function findLowestMissingParticipant(
        isGroups: boolean,
        yetUnregisteredParticipants: number[],
        ignoreIndex?: number,
    ): number {
        const list = isGroups ? watchGroups : watchMatches

        const takenParticipants = list
            .filter((_, index) => index !== ignoreIndex)
            .map(v => v.participants)
            .flat()
            .map(v => v.seed)

        const set = new Set([...takenParticipants, ...yetUnregisteredParticipants])
        let i = 1
        while (set.has(i)) {
            i++
        }
        return i
    }

    // Default participants that are inserted to a match or group when appended - When Default seeding is enabled they are overwritten by the useEffect above
    const appendPreparedParticipants: {seed: number}[] = []
    if (round.index !== 0) {
        for (
            let i = 0;
            i < (watchIsGroupRound ? defaultGroupTeamSize : defaultMatchTeamSize);
            i++
        ) {
            appendPreparedParticipants.push({
                seed: findLowestMissingParticipant(
                    watchIsGroupRound,
                    appendPreparedParticipants.map(v => v.seed),
                ),
            })
        }
    }

    const groupsOrMatchesLength = watchIsGroupRound ? watchGroups.length : watchMatches.length
    const highestTeamCount = (watchIsGroupRound ? watchGroups : watchMatches)
        ? getHighestTeamsCount(
              (watchIsGroupRound ? watchGroups : watchMatches).map(v => v.teams),
              teamCounts.nextRound,
          )
        : 0

    // Display the seeds that Teams are given, based on their place in their match
    // Purely visual to show where the participants in the next round are coming from
    const roundOutcomes = fillSeedingList(
        groupsOrMatchesLength,
        highestTeamCount,
        watchIsGroupRound
            ? watchGroups.map(g => Number(g.teams))
            : watchMatches.map(m => Number(m.teams)),
        teamCounts.nextRound,
    )

    // PLACES
    // The places which teams that won't partake in the next round get
    const watchPlaces = formContext.watch(`rounds.${round.index}.places`)

    const {fields: placeFields} = useFieldArray({
        control: formContext.control,
        name: `rounds.${round.index}.places`,
    })

    const controlledPlacesFields = placeFields.map((field, index) => ({
        ...field,
        ...watchPlaces?.[index],
    }))

    const updatePlaces = (roundIndex: number, updateThisRound: boolean, newTeamsCount?: number) => {
        const thisRoundTeams = newTeamsCount ?? teamCounts.thisRound

        // If the participants of this round changed, the places in this round are updated
        if (updateThisRound) {
            const nextRoundParticipants = watchIsGroupRound
                ? getParticipantsFromMatchOrGroup(
                      undefined,
                      formContext.getValues(`rounds.${roundIndex + 1}.groups`),
                  )
                : getParticipantsFromMatchOrGroup(
                      formContext.getValues(`rounds.${roundIndex + 1}.matches`),
                  )

            const newPlaces = getNewPlaces(
                nextRoundParticipants,
                thisRoundTeams,
                teamCounts.nextRound,
            )

            formContext.setValue(`rounds.${roundIndex}.places`, newPlaces)
        }

        // Update the places of the previous round
        if (roundIndex > 0) {
            const participants = getParticipantsFromMatchOrGroup(watchMatches)

            const newPlaces = getNewPlaces(participants, teamCounts.prevRound, thisRoundTeams)

            formContext.setValue(`rounds.${roundIndex - 1}.places`, newPlaces)
        }
    }

    const roundHasUndefinedTeams = watchMatches.filter(match => match.teams === '').length > 0

    console.log(`R${round.index}`, roundHasUndefinedTeams, teamCounts, (roundHasUndefinedTeams || teamCounts.thisRound > teamCounts.nextRound))

    type MatchOrGroupInfo = {
        index: number
        fieldId: string
        outcomes: number[]
    }

    const getGroupOrMatchProps = (
        isGroups: boolean,
        info: MatchOrGroupInfo,
        useStartTimeOffsets: boolean,
    ): CompetitionSetupMatchOrGroupProps => {
        return {
            formContext: formContext,
            roundIndex: round.index,
            fieldInfo: {
                index: info.index,
                id: info.fieldId,
            },
            outcomes: info.outcomes,
            teamCounts: {
                thisRoundWithoutThis: props.getRoundTeamCountWithoutThis(info.index, isGroups),
                nextRound: teamCounts.nextRound,
            },
            useDefaultSeeding: watchUseDefaultSeeding,
            participantFunctions: {
                findLowestMissingParticipant: yetUnregisteredParticipants =>
                    findLowestMissingParticipant(isGroups, yetUnregisteredParticipants, info.index),
                updateRoundParticipants: (repeatForPreviousRound, nextRoundTeams) =>
                    updateParticipants(
                        formContext,
                        round.index,
                        repeatForPreviousRound,
                        nextRoundTeams,
                        updatePlaces,
                    ),
                setParticipantValuesForThis: participants =>
                    setParticipantValuesForMatchOrGroup(
                        formContext,
                        round.index,
                        isGroups,
                        info.index,
                        participants,
                    ),
                updatePreviousRoundParticipants: thisRoundTeams =>
                    updatePreviousRoundParticipants(
                        formContext,
                        round.index - 1,
                        thisRoundTeams,
                        updatePlaces,
                    ),
                updatePlaces: updatePlaces,
            },
            useStartTimeOffsets: useStartTimeOffsets,
            isLastIndex: info.index === watchMatches.length - 1,
        }
    }

    return (
        <Controller
            name={`rounds.${round.index}.useDefaultSeeding`}
            render={({
                field: {onChange: useDefSeedingOnChange, value: useDefSeedingValue = true},
            }) => (
                <Controller
                    name={`rounds.${round.index}.useStartTimeOffsets`}
                    render={({
                        field: {
                            onChange: useStartTimeOffsetsOnChange,
                            value: useStartTimeOffsetsValue = true,
                        },
                    }) => (
                        <Stack spacing={2} sx={{border: 1, borderColor: 'blue', p: 2}}>
                            <Box>
                                <Button
                                    variant="outlined"
                                    onClick={() => {
                                        removeRound(round.index)
                                    }}>
                                    {t('event.competition.setup.round.remove')}
                                </Button>
                            </Box>
                            {/*<CheckboxElement
                            name={`rounds[${round.index}].isGroupRound`}
                            label={<FormInputLabel label={'Group Phase'} required={true} horizontal />}
                        />*/}
                            <Box sx={{maxWidth: 230}}>
                                <FormInputText
                                    name={`rounds.${round.index}.name`}
                                    label={t('event.competition.setup.round.name')}
                                    required
                                />
                            </Box>

                            <Box sx={{flexShrink: 'inherit', alignSelf: 'start'}}>
                                <FormControlLabel
                                    control={
                                        <Checkbox
                                            checked={useDefSeedingValue}
                                            onChange={useDefSeedingOnChange}
                                        />
                                    }
                                    label={
                                        <FormInputLabel
                                            label={t(
                                                'event.competition.setup.round.useDefaultSeeding',
                                            )}
                                            required={true}
                                            horizontal
                                        />
                                    }
                                />
                            </Box>
                            <Box sx={{flexShrink: 'inherit', alignSelf: 'start'}}>
                                <FormControlLabel
                                    control={
                                        <Checkbox
                                            checked={useStartTimeOffsetsValue}
                                            onChange={e => {
                                                useStartTimeOffsetsOnChange(e)
                                            }}
                                        />
                                    }
                                    label={
                                        <FormInputLabel
                                            label={t(
                                                'event.competition.setup.startTimeOffset.enable',
                                            )}
                                            required={true}
                                            horizontal
                                        />
                                    }
                                />
                            </Box>
                            <CheckboxElement
                                name={`rounds.${round.index}.required`}
                                label={
                                    <FormInputLabel
                                        label={t('event.competition.setup.round.required')}
                                        required={true}
                                        horizontal
                                    />
                                }
                            />
                            <Divider />
                            <Box
                                sx={{
                                    display: 'flex',
                                    flexDirection: 'row',
                                    justifyContent: 'space-between',
                                    [theme.breakpoints.down('lg')]: {flexDirection: 'column'},
                                }}>
                                <Stack spacing={2} sx={{flex: 1, alignSelf: 'start'}}>
                                    <Box sx={{alignSelf: 'start'}}>
                                        {!watchIsGroupRound ? (
                                            <Button
                                                variant="outlined"
                                                onClick={() => {
                                                    appendMatch({
                                                        teams: `${defaultMatchTeamSize}`,
                                                        name: '',
                                                        participants: watchUseDefaultSeeding
                                                            ? []
                                                            : appendPreparedParticipants,
                                                        executionOrder: matchFields.length + 1,
                                                    })
                                                }}
                                                sx={{width: 1}}>
                                                {t('entity.add.action', {
                                                    entity: t(
                                                        'event.competition.setup.match.match',
                                                    ),
                                                })}
                                            </Button>
                                        ) : (
                                            <></>
                                            /*<Button
                                                variant="outlined"
                                                onClick={() => {
                                                    appendGroup({
                                                        weighting: groupFields.length + 1,
                                                        teams: `${defaultGroupTeamSize}`,
                                                        name: '',
                                                        matches: [],
                                                        participants: appendPreparedParticipants,
                                                        matchTeams: 2,
                                                    })
                                                }}
                                                sx={{width: 1}}>
                                                Add Group
                                            </Button>*/
                                        )}
                                    </Box>
                                    {matchesError && (
                                        <Typography color={'error'}>{matchesError}</Typography>
                                    )}
                                    {round.index === 0 &&
                                        watchMatches.filter(v => v.teams === '').length > 1 && (
                                            <Alert severity="info">
                                                {t(
                                                    'event.competition.setup.match.info.roundOneFillMatches',
                                                )}
                                            </Alert>
                                        )}
                                    <Box
                                        sx={{
                                            display: 'flex',
                                            flexWrap: 'wrap',
                                            justifyContent: 'start',
                                            flex: 1,
                                            gap: 2,
                                        }}>
                                        {watchIsGroupRound === false ? (
                                            <>
                                                {matchFields.map((match, matchIndex) => (
                                                    <Stack
                                                        key={match.id}
                                                        direction="column"
                                                        spacing={1}
                                                        sx={{maxWidth: 245}}>
                                                        <CompetitionSetupMatch
                                                            {...getGroupOrMatchProps(
                                                                false,
                                                                {
                                                                    index: matchIndex,
                                                                    fieldId: match.id,
                                                                    outcomes: !watchIsGroupRound
                                                                        ? roundOutcomes[matchIndex]
                                                                        : [],
                                                                },
                                                                useStartTimeOffsetsValue,
                                                            )}
                                                        />
                                                        <Button
                                                            variant="outlined"
                                                            onClick={() => {
                                                                removeMatch(matchIndex)
                                                            }}>
                                                            {t(
                                                                'event.competition.setup.match.remove',
                                                            )}
                                                        </Button>
                                                    </Stack>
                                                ))}
                                            </>
                                        ) : (
                                            <>
                                                {/*
                                                {groupFields.map((group, groupIndex) => (
                                                    <Stack
                                                        key={group.id}
                                                        direction="column"
                                                        spacing={1}
                                                        sx={{maxWidth: 450}}>
                                                        <CompetitionSetupGroup
                                                            {...getGroupOrMatchProps(true, groupInfo, useStartTimeOffsetsValue)}
                                                            getLowestGroupMatchPosition={
                                                                getLowestGroupMatchPosition
                                                            }
                                                        />
                                                        <Button
                                                            variant="outlined"
                                                            onClick={() => {
                                                                removeGroup(groupIndex)
                                                            }}>
                                                            Remove Group
                                                        </Button>
                                                    </Stack>
                                                ))}*/}
                                            </>
                                        )}
                                    </Box>
                                </Stack>

                                <Controller
                                    name={`rounds.${round.index}.placesOption`}
                                    render={({
                                        field: {
                                            onChange: placesOptionOnChange,
                                            value: placesOptionValue = 'EQUAL',
                                        },
                                    }) => (<>
                                        { (roundHasUndefinedTeams || teamCounts.thisRound > teamCounts.nextRound) && (
                                        <Stack
                                            spacing={2}
                                            sx={{justifySelf: 'flex-end', maxWidth: 250, m: 2}}>
                                            <Typography variant={'h3'}>
                                                {`${t('event.competition.setup.place.places')}`}
                                            </Typography>
                                            <Select
                                                value={placesOptionValue}
                                                onChange={e => {
                                                    placesOptionOnChange(e)
                                                }}>
                                                <MenuItem value={'EQUAL'}>
                                                    {t(
                                                        'event.competition.setup.place.placesOption.equal',
                                                        {place: teamCounts.nextRound + 1},
                                                    )}
                                                </MenuItem>
                                                <MenuItem value={'ASCENDING'}>
                                                    {t(
                                                        'event.competition.setup.place.placesOption.ascending',
                                                    )}
                                                </MenuItem>
                                                <MenuItem value={'CUSTOM'}>
                                                    {t(
                                                        'event.competition.setup.place.placesOption.custom',
                                                    )}
                                                </MenuItem>
                                            </Select>
                                            {placesOptionValue === 'CUSTOM' && (
                                                <>
                                                    {controlledPlacesFields.length > 0 ? (
                                                        <TableContainer>
                                                            <Table>
                                                                <TableHead>
                                                                    <TableRow>
                                                                        <TableCell>
                                                                            {t(
                                                                                'event.competition.setup.match.outcome.outcome',
                                                                            )}
                                                                        </TableCell>
                                                                        <TableCell>
                                                                            {t(
                                                                                'event.competition.setup.place.place',
                                                                            )}
                                                                        </TableCell>
                                                                    </TableRow>
                                                                </TableHead>
                                                                <TableBody>
                                                                    {controlledPlacesFields.map(
                                                                        (place, placeIndex) => (
                                                                            <TableRow
                                                                                key={place.id}>
                                                                                <TableCell>
                                                                                    {
                                                                                        controlledPlacesFields[
                                                                                            placeIndex
                                                                                        ]
                                                                                            .roundOutcome
                                                                                    }
                                                                                </TableCell>
                                                                                <TableCell>
                                                                                    <FormInputNumber
                                                                                        name={`rounds.${round.index}.places.${placeIndex}.place`}
                                                                                        placeholder={`${teamCounts.nextRound + 1}`}
                                                                                        required
                                                                                        integer
                                                                                        min={1}
                                                                                    />
                                                                                </TableCell>
                                                                            </TableRow>
                                                                        ),
                                                                    )}
                                                                </TableBody>
                                                            </Table>
                                                        </TableContainer>
                                                    ) : (
                                                        <Typography>
                                                            [todo] No places in this round
                                                        </Typography>
                                                    )}
                                                </>
                                            )}
                                        </Stack>
                                        )}
                                    </>)}
                                />
                            </Box>
                        </Stack>
                    )}
                />
            )}
        />
    )
}

export default CompetitionSetupRound

import {CheckboxElement, Controller, useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import {
    Alert,
    Box,
    Button,
    Checkbox,
    FormControlLabel,
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
import {CompetitionSetupForm} from '@components/event/competition/setup/CompetitionSetup.tsx'
import CompetitionSetupMatch from '@components/event/competition/setup/CompetitionSetupMatch.tsx'
import {useEffect} from 'react'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {
    CompetitionSetupMatchOrGroupProps,
    fillSeedingList,
    getHighestTeamsCount,
    //getLowest,
    setParticipantValuesForMatchOrGroup,
    updateParticipants,
    updatePreviousRoundParticipants,
} from '@components/event/competition/setup/common.ts'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'

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
}
const CompetitionSetupRound = ({round, formContext, removeRound, teamCounts, ...props}: Props) => {
    const defaultMatchTeamSize = 2
    const defaultGroupTeamSize = 4

    const theme = useTheme()

    const watchUseDefaultSeeding = formContext.watch(
        `rounds[${round.index}].useDefaultSeeding` as `rounds.${number}.useDefaultSeeding`,
    )

    const watchIsGroupRound = formContext.watch(
        `rounds[${round.index}].isGroupRound` as `rounds.${number}.isGroupRound`,
    )

    const {
        fields: matchFields,
        append: appendMatch,
        remove: removeMatch,
    } = useFieldArray({
        control: formContext.control,
        name: `rounds.${round.index}.matches`,
    })

    const watchMatches = formContext.watch(`rounds.${round.index}.matches`)

    const {
        fields: groupFields,
        append: appendGroup,
        remove: removeGroup,
    } = useFieldArray({
        control: formContext.control,
        name: `rounds.${round.index}.groups`,
    })

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

    // When appending or removing a match/group, the participants are updated (if default seeding is active)
    useEffect(() => {
        if (watchUseDefaultSeeding) {
            updateParticipants(formContext, round.index, true, teamCounts.nextRound)
        }
    }, [watchMatches.length, watchGroups.length, watchIsGroupRound, watchUseDefaultSeeding])

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

    const roundHasDuplicatableMatch = watchMatches.find(v => v.duplicatable === true) !== undefined
    const roundHasDuplicatableGroup = watchGroups.find(v => v.duplicatable === true) !== undefined

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

    const onParticipantsChanged = (teamCountChanged: boolean) => {
        const participants = watchMatches.map(m => m.participants.map(p => p.seed)).flat()

        if (round.index > 0) {
            const newPlaces = []

            for (let i = 0; i < teamCounts.prevRound; i++) {
                if (![...newPlaces.map(np => np.roundOutcome), ...participants].includes(i + 1)) {
                    const place = {roundOutcome: i + 1, place: teamCounts.thisRound + 1}
                    newPlaces.push(place)
                }
            }

            const foo = new Array(teamCounts.prevRound)
                .fill(null)
                .map((_, i) => ({
                    roundOutcome: i + 1,
                    place: teamCounts.thisRound + 1,
                }))
                .filter(v => !participants.includes(v.roundOutcome))

            formContext.setValue(
                `rounds.${round.index - 1}.places`,
                foo,
            )
        }
    }

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
            roundHasDuplicatable: isGroups ? roundHasDuplicatableGroup : roundHasDuplicatableMatch,
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
                    updatePreviousRoundParticipants(formContext, round.index - 1, thisRoundTeams),
            },
            useStartTimeOffsets: useStartTimeOffsets,
            onParticipantsChanged: onParticipantsChanged,
        }
    }

    return (
        <Controller
            name={'rounds[' + round.index + '].useDefaultSeeding'}
            render={({
                field: {onChange: useDefSeedingOnChange, value: useDefSeedingValue = true},
            }) => (
                <Controller
                    name={`rounds[${round.index}].useStartTimeOffsets`}
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
                                    Remove Round
                                </Button>
                            </Box>
                            {/*<CheckboxElement
                            name={`rounds[${round.index}].isGroupRound`}
                            label={<FormInputLabel label={'Group Phase'} required={true} horizontal />}
                        />*/}
                            <Box sx={{maxWidth: 230}}>
                                <FormInputText
                                    name={`rounds[${round.index}].name`}
                                    label={'Round name'}
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
                                            label={'Use default seeding'}
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
                                            label={'Enable start time offsets'}
                                            required={true}
                                            horizontal
                                        />
                                    }
                                />
                            </Box>

                            <CheckboxElement
                                name={`rounds[${round.index}].required`}
                                label={
                                    <FormInputLabel
                                        label={'Round must be held'}
                                        required={true}
                                        horizontal
                                    />
                                }
                            />

                            <Box
                                sx={{
                                    display: 'flex',
                                    flexDirection: 'row',
                                    justifyContent: 'space-between',
                                    [theme.breakpoints.down('lg')]: {flexDirection: 'column'},
                                }}>
                                <Stack spacing={2} sx={{flex: 1, alignSelf: 'start'}}>
                                    <Box sx={{alignSelf: 'center'}}>
                                        {!watchIsGroupRound ? (
                                            <Button
                                                variant="outlined"
                                                onClick={() => {
                                                    appendMatch({
                                                        duplicatable: false,
                                                        weighting: matchFields.length + 1,
                                                        teams: `${defaultMatchTeamSize}`,
                                                        name: '',
                                                        participants: watchUseDefaultSeeding
                                                            ? []
                                                            : appendPreparedParticipants,
                                                        position: matchFields.length + 1,
                                                    })
                                                }}
                                                sx={{width: 1}}>
                                                Add Match
                                            </Button>
                                        ) : (
                                            <Button
                                                variant="outlined"
                                                onClick={() => {
                                                    appendGroup({
                                                        duplicatable: false,
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
                                            </Button>
                                        )}
                                    </Box>
                                    {round.index === 0 &&
                                        watchMatches.filter(v => v.teams === '').length > 1 && (
                                            <Alert severity="info">
                                                Participants will be filled into the matches equally
                                                starting from the first match
                                            </Alert>
                                        )}
                                    <Box
                                        sx={{
                                            display: 'flex',
                                            flexWrap: 'wrap',
                                            justifyContent: 'center',
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
                                                            Remove Match
                                                        </Button>
                                                    </Stack>
                                                ))}
                                            </>
                                        ) : (
                                            <>
                                                {groupFields.map((group, groupIndex) => (
                                                    <Stack
                                                        key={group.id}
                                                        direction="column"
                                                        spacing={1}
                                                        sx={{maxWidth: 450}}>
                                                        {/*<CompetitionSetupGroup
                                                {...getGroupOrMatchProps(true, groupInfo, useStartTimeOffsetsValue)}
                                                getLowestGroupMatchPosition={
                                                    getLowestGroupMatchPosition
                                                }
                                            />*/}
                                                        <Button
                                                            variant="outlined"
                                                            onClick={() => {
                                                                removeGroup(groupIndex)
                                                            }}>
                                                            Remove Group
                                                        </Button>
                                                    </Stack>
                                                ))}
                                            </>
                                        )}
                                    </Box>
                                </Stack>

                                {controlledPlacesFields.length > 0 && (
                                    <Stack sx={{justifySelf: 'flex-end', maxWidth: 250, m: 2}}>
                                        <Typography variant={'h3'}>
                                            {`Final places (${controlledPlacesFields[0].roundOutcome} - ${controlledPlacesFields[controlledPlacesFields.length - 1].roundOutcome})`}
                                        </Typography>
                                        <TableContainer>
                                            <Table>
                                                <TableHead>
                                                    <TableCell>Outcome</TableCell>
                                                    <TableCell>Place</TableCell>
                                                </TableHead>
                                                <TableBody>
                                                    {controlledPlacesFields.map(
                                                        (place, placeIndex) => (
                                                            <TableRow key={place.id}>
                                                               <TableCell>
                                                                   {
                                                                       controlledPlacesFields[
                                                                           placeIndex
                                                                       ].roundOutcome
                                                                   }
                                                               </TableCell>
                                                               <TableCell>
                                                                   <FormInputNumber
                                                                       name={`rounds.${round.index}.places.${placeIndex}.place`}
                                                                       placeholder={`${teamCounts.nextRound + 1}`}
                                                                       required
                                                                   />
                                                               </TableCell>
                                                           </TableRow>
                                                        ),
                                                    )}
                                                </TableBody>
                                            </Table>
                                        </TableContainer>
                                    </Stack>
                                )}
                            </Box>
                        </Stack>
                    )}
                />
            )}
        />
    )
}

export default CompetitionSetupRound

import {CheckboxElement, Controller, useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import {
    Alert,
    Box,
    Button,
    Checkbox,
    Divider,
    FormControlLabel,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
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
//import CompetitionSetupGroup from '@components/event/competition/setup/CompetitionSetupGroup.tsx'

type Props = {
    round: {index: number; id: string}
    formContext: UseFormReturn<CompetitionSetupForm>
    removeRound: (index: number) => void
    teamCounts: {
        thisRound: number
        nextRound: number
    }
    getRoundTeamCountWithoutThis: (ignoredIndex: number, isGroupRound: boolean) => number
}
const CompetitionSetupRound = ({round, formContext, removeRound, teamCounts, ...props}: Props) => {
    const defaultMatchTeamSize = 2
    const defaultGroupTeamSize = 4

    const watchUseDefaultSeeding = formContext.watch(
        ('rounds[' + round.index + '].useDefaultSeeding') as `rounds.${number}.useDefaultSeeding`,
    )

    const watchIsGroupRound = formContext.watch(
        ('rounds[' + round.index + '].isGroupRound') as `rounds.${number}.isGroupRound`,
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

    type MatchOrGroupInfo = {
        originalIndex: number
        fieldId: string
        outcomes: number[]
    }
    const matchInfos: MatchOrGroupInfo[] = watchMatches.map((_, index) => ({
        originalIndex: index,
        fieldId: matchFields[index]?.id ?? '',
        outcomes: !watchIsGroupRound ? roundOutcomes[index] : [],
    }))

    const groupInfos: MatchOrGroupInfo[] = watchGroups.map((_, index) => ({
        originalIndex: index,
        fieldId: groupFields[index]?.id ?? '',
        outcomes: watchIsGroupRound ? roundOutcomes[index] : [],
    }))

    // The outcomes that won't partake in the next round if there is one
    const eliminatedRoundOutcomes = roundOutcomes
        .flat()
        .sort((a, b) => a - b)
        .filter((outcome) => outcome > teamCounts.nextRound)

    console.log("Round", round.index, "Outcomes", roundOutcomes.flat(), "E", eliminatedRoundOutcomes)

    const getGroupOrMatchProps = (
        isGroups: boolean,
        info: MatchOrGroupInfo,
        useStartTimeOffsets: boolean,
    ): CompetitionSetupMatchOrGroupProps => {
        return {
            formContext: formContext,
            roundIndex: round.index,
            fieldInfo: {
                index: info.originalIndex,
                id: info.fieldId,
            },
            roundHasDuplicatable: isGroups ? roundHasDuplicatableGroup : roundHasDuplicatableMatch,
            outcomes: info.outcomes,
            teamCounts: {
                thisRoundWithoutThis: props.getRoundTeamCountWithoutThis(
                    info.originalIndex,
                    isGroups,
                ),
                nextRound: teamCounts.nextRound,
            },
            useDefaultSeeding: watchUseDefaultSeeding,
            participantFunctions: {
                findLowestMissingParticipant: yetUnregisteredParticipants =>
                    findLowestMissingParticipant(
                        isGroups,
                        yetUnregisteredParticipants,
                        info.originalIndex,
                    ),
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
                        info.originalIndex,
                        participants,
                    ),
                updatePreviousRoundParticipants: thisRoundTeams =>
                    updatePreviousRoundParticipants(formContext, round.index - 1, thisRoundTeams),
            },
            useStartTimeOffsets: useStartTimeOffsets,
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
                            <Stack
                                spacing={2}
                                direction="row"
                                sx={{
                                    justifyContent: 'space-between',
                                    alignItems: 'center',
                                }}>
                                <Typography>
                                    Max Teams:{' '}
                                    {teamCounts.thisRound === 0 ? 'Unknown' : teamCounts.thisRound}
                                </Typography>
                                <Box>
                                    <Button
                                        variant="outlined"
                                        onClick={() => {
                                            removeRound(round.index)
                                        }}>
                                        Remove Round
                                    </Button>
                                </Box>
                            </Stack>
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

                            {/*todo: make this look the same as the isGroupRound Checkbox*/}
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

                            <FormControlLabel
                                control={
                                    <Checkbox
                                        checked={useStartTimeOffsetsValue}
                                        onChange={e => {
                                            useStartTimeOffsetsOnChange(e)
                                            console.log(e, useStartTimeOffsetsValue)
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
                            <Stack
                                direction="row"
                                spacing={2}
                                justifyContent="space-between"
                                sx={{alignItems: 'center'}}>
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
                                            {matchInfos.map((matchInfo, _) => (
                                                <Stack
                                                    key={matchInfo.fieldId}
                                                    direction="column"
                                                    spacing={1}
                                                    sx={{maxWidth: 245}}>
                                                    <CompetitionSetupMatch
                                                        {...getGroupOrMatchProps(
                                                            false,
                                                            matchInfo,
                                                            useStartTimeOffsetsValue,
                                                        )}
                                                    />
                                                    <Button
                                                        variant="outlined"
                                                        onClick={() => {
                                                            removeMatch(matchInfo.originalIndex)
                                                        }}>
                                                        Remove Match
                                                    </Button>
                                                </Stack>
                                            ))}
                                        </>
                                    ) : (
                                        <>
                                            {groupInfos.map((groupInfo, _) => (
                                                <Stack
                                                    key={groupInfo.fieldId}
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
                                                            removeGroup(groupInfo.originalIndex)
                                                        }}>
                                                        Remove Group
                                                    </Button>
                                                </Stack>
                                            ))}
                                        </>
                                    )}
                                </Box>
                            </Stack>
                            {eliminatedRoundOutcomes.length > 0 && (
                                <>
                                    <Divider />
                                    <Stack sx={{alignSelf: 'center', maxWidth: 300}}>
                                        <Typography variant={'h3'}>Final places</Typography>
                                        <TableContainer>
                                            <Table>
                                                <TableHead>
                                                    <TableCell>Outcome</TableCell>
                                                    <TableCell>Place</TableCell>
                                                </TableHead>
                                                <TableBody>
                                                    {eliminatedRoundOutcomes.map(
                                                        (outcome, outcomeIndex) => (
                                                            <TableRow key={outcome}>
                                                                <TableCell>{outcome}</TableCell>
                                                                <TableCell>
                                                                    <FormInputNumber
                                                                        name={`rounds[${round.index}].places[${outcomeIndex}].place`}
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
                                </>
                            )}
                        </Stack>
                    )}
                />
            )}
        />
    )
}

export default CompetitionSetupRound

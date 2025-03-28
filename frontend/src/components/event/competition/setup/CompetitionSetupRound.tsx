import {CheckboxElement, Controller, useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import {Box, Button, Checkbox, FormControlLabel, Stack, Typography} from '@mui/material'
import {CompetitionSetupForm} from '@components/event/competition/setup/CompetitionSetup.tsx'
import CompetitionSetupMatch from '@components/event/competition/setup/CompetitionSetupMatch.tsx'
import {useEffect} from 'react'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {
    CompetitionSetupMatchOrGroupProps,
    getHighestTeamsCount,
    getLowest,
    getMatchupsString,
    getWeightings,
    setOutcomeValuesForMatchOrGroup,
    updateOutcomes,
    updatePreviousRoundOutcomes,
} from '@components/event/competition/setup/common.ts'
import CompetitionSetupGroup from '@components/event/competition/setup/CompetitionSetupGroup.tsx'

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

    const getLowestGroupMatchPosition = (takenPositions?: number[]) => {
        return getLowest(
            [
                ...watchGroups.map(g => g.matches.map(m => m.position)).flat(),
                ...(takenPositions ?? []),
            ],
            1,
        )
    }

    // When appending or removing a match/group, the outcomes are updated (if default seeding is active)
    useEffect(() => {
        if (watchUseDefaultSeeding) {
            updateOutcomes(formContext, round.index, true, teamCounts.nextRound)
        }
    }, [watchMatches.length, watchGroups.length, watchIsGroupRound, watchUseDefaultSeeding])

    function findLowestMissingOutcome(
        isGroups: boolean,
        yetUnregisteredOutcomes: number[],
        ignoreIndex?: number,
    ): number {
        const list = isGroups ? watchMatches : watchGroups
        const takenOutcomes =
            list
                .filter((_, index) => index !== ignoreIndex)
                .map(v => v.outcomes)
                .flat()
                .map(v => v?.outcome ?? 0) ?? []

        const set = new Set([...takenOutcomes, ...yetUnregisteredOutcomes])
        let i = 1
        while (set.has(i)) {
            i++
        }
        return i
    }

    // Default  outcomes that are inserted to a match or group when appended - When Default seeding is enabled they are overwritten by the useEffect above
    const appendPreparedOutcomes: {outcome: number}[] = []
    for (let i = 0; i < (watchIsGroupRound ? defaultGroupTeamSize : defaultMatchTeamSize); i++) {
        appendPreparedOutcomes.push({
            outcome: findLowestMissingOutcome(
                watchIsGroupRound,
                appendPreparedOutcomes.map(v => v.outcome),
            ),
        })
    }

    const roundHasDuplicatableMatch = watchMatches.find(v => v.duplicatable === true) !== undefined
    const roundHasDuplicatableGroup = watchGroups.find(v => v.duplicatable === true) !== undefined

    // Display the Teams that are participating in this match
    // Purely visual to show where the outcome-seeds from last round are playing in this round

    const watchPrevRoundIsGroupRound =
        round.index > 0
            ? formContext.watch(
                  `rounds[${round.index - 1}].isGroupRound` as `rounds.${number}.isGroupRound`,
              )
            : undefined

    const watchPrevRoundMatches =
        round.index > 0
            ? formContext.watch(`rounds[${round.index - 1}].matches` as `rounds.${number}.matches`)
            : undefined

    const watchPrevRoundGroups =
        round.index > 0
            ? formContext.watch(`rounds[${round.index - 1}].groups` as `rounds.${number}.groups`)
            : undefined

    const prevRoundOutcomes = (
        watchPrevRoundIsGroupRound ? watchPrevRoundGroups : watchPrevRoundMatches
    )
        ?.map(v => (v.outcomes ? v.outcomes?.map(outcome => outcome.outcome).flat() : []))
        .flat()
        .sort((a, b) => a - b)
        .slice(0, teamCounts.thisRound)

    const highestTeamCount = (watchIsGroupRound ? watchGroups : watchMatches)
        ? getHighestTeamsCount(
              (watchIsGroupRound ? watchGroups : watchMatches).map(v => v.teams),
              teamCounts.nextRound,
          )
        : 0

    const matchups: number[][] = []
    const groupsOrMatchesLength = watchIsGroupRound ? watchGroups.length : watchMatches.length
    for (let i = 0; i < (groupsOrMatchesLength ?? 0); i++) {
        matchups.push([])
    }
    if (prevRoundOutcomes !== undefined) {
        let participantsTaken = 0
        for (let i = 0; i < highestTeamCount; i++) {
            const addToList = (index: number) => {
                if (
                    Number(
                        watchIsGroupRound ? watchGroups[index].teams : watchMatches[index].teams,
                    ) > matchups[index].length &&
                    prevRoundOutcomes[participantsTaken] !== undefined
                ) {
                    matchups[index].push(prevRoundOutcomes[participantsTaken])
                    participantsTaken += 1
                }
            }

            if (i % 2 === 0) {
                for (let j = 0; j < groupsOrMatchesLength; j++) {
                    addToList(j)
                }
            } else {
                for (let j = groupsOrMatchesLength - 1; j > -1; j--) {
                    addToList(j)
                }
            }
        }
    }

    type MatchOrGroupInfo = {
        originalIndex: number
        fieldId: string
        participants: number[]
    }
    const matchInfos: MatchOrGroupInfo[] = watchMatches.map((_, index) => ({
        originalIndex: index,
        fieldId: matchFields[index]?.id ?? '',
        participants: !watchIsGroupRound ? matchups[index] : [],
    }))

    // This is just for the display. in the fieldArray the weightings are in order (1,2,3...)
    const matchInfosSortedByWeighting = getWeightings(matchInfos.length).map(v => matchInfos[v - 1])

    const groupInfos: MatchOrGroupInfo[] = watchGroups.map((_, index) => ({
        originalIndex: index,
        fieldId: groupFields[index]?.id ?? '',
        participants: watchIsGroupRound ? matchups[index] : [],
    }))

    // This is just for the display. in the fieldArray the weightings are in order (1,2,3...)
    const groupInfosSortedByWeighting = getWeightings(groupInfos.length).map(v => groupInfos[v - 1])

    const getGroupOrMatchProps = (
        isGroups: boolean,
        info: MatchOrGroupInfo,
    ): CompetitionSetupMatchOrGroupProps => {
        return {
            formContext: formContext,
            roundIndex: round.index,
            fieldInfo: {
                index: info.originalIndex,
                id: info.fieldId,
            },
            roundHasDuplicatable: isGroups ? roundHasDuplicatableGroup : roundHasDuplicatableMatch,
            participantsString: getMatchupsString(info.participants),
            teamCounts: {
                thisRoundWithoutThis: props.getRoundTeamCountWithoutThis(
                    info.originalIndex,
                    isGroups,
                ),
                nextRound: teamCounts.nextRound,
            },
            useDefaultSeeding: watchUseDefaultSeeding,
            outcomeFunctions: {
                findLowestMissingOutcome: yetUnregisteredOutcomes =>
                    findLowestMissingOutcome(isGroups, yetUnregisteredOutcomes, info.originalIndex),
                updateRoundOutcomes: (repeatForPreviousRound, nextRoundTeams) =>
                    updateOutcomes(
                        formContext,
                        round.index,
                        repeatForPreviousRound,
                        nextRoundTeams,
                    ),
                setOutcomeValuesForThis: outcomes =>
                    setOutcomeValuesForMatchOrGroup(
                        formContext,
                        round.index,
                        isGroups,
                        info.originalIndex,
                        outcomes,
                    ),
                updatePreviousRoundOutcomes: thisRoundTeams =>
                    updatePreviousRoundOutcomes(formContext, round.index - 1, thisRoundTeams),
            },
        }
    }

    return (
        <Controller
            name={'rounds[' + round.index + '].useDefaultSeeding'}
            render={({
                field: {onChange: useDefSeedingOnChange, value: useDefSeedingValue = true},
            }) => (
                <Stack spacing={2} sx={{border: 1, borderColor: 'blue', p: 2}}>
                    <Stack
                        spacing={2}
                        direction="row"
                        sx={{
                            justifyContent: 'space-between',
                            alignItems: 'center',
                        }}>
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
                    <CheckboxElement
                        name={`rounds[${round.index}].isGroupRound`}
                        label={<FormInputLabel label={'Group Phase'} required={true} horizontal />}
                    />
                    <FormInputText name={`rounds[${round.index}].name`} label={'Round name'} />
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
                    <Stack
                        direction="row"
                        spacing={2}
                        justifyContent="space-between"
                        sx={{alignItems: 'center'}}>
                        <Stack direction="row" spacing={2}>
                            {watchIsGroupRound === false ? (
                                <>
                                    {matchInfosSortedByWeighting.map((matchInfo, _) => (
                                        <Stack
                                            key={matchInfo.fieldId}
                                            direction="column"
                                            spacing={1}
                                            sx={{maxWidth: 300}}>
                                            <CompetitionSetupMatch
                                                {...getGroupOrMatchProps(false, matchInfo)}
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
                                    <Box sx={{alignSelf: 'center'}}>
                                        <Button
                                            variant="outlined"
                                            onClick={() => {
                                                appendMatch({
                                                    duplicatable: false,
                                                    weighting: matchFields.length + 1,
                                                    teams: `${defaultMatchTeamSize}`,
                                                    name: '',
                                                    outcomes: appendPreparedOutcomes,
                                                    position: matchFields.length,
                                                })
                                            }}
                                            sx={{width: 1}}>
                                            Add Match
                                        </Button>
                                    </Box>
                                </>
                            ) : (
                                <>
                                    {groupInfosSortedByWeighting.map((groupInfo, _) => (
                                        <Stack
                                            key={groupInfo.fieldId}
                                            direction="column"
                                            spacing={1}
                                            sx={{maxWidth: 450}}>
                                            <CompetitionSetupGroup
                                                {...getGroupOrMatchProps(true, groupInfo)}
                                                getLowestGroupMatchPosition={
                                                    getLowestGroupMatchPosition
                                                }
                                            />
                                            <Button
                                                variant="outlined"
                                                onClick={() => {
                                                    removeGroup(groupInfo.originalIndex)
                                                }}>
                                                Remove Group
                                            </Button>
                                        </Stack>
                                    ))}
                                    <Box sx={{alignSelf: 'center'}}>
                                        <Button
                                            variant="outlined"
                                            onClick={() => {
                                                appendGroup({
                                                    duplicatable: false,
                                                    weighting: groupFields.length + 1,
                                                    teams: `${defaultGroupTeamSize}`,
                                                    name: '',
                                                    matches: [],
                                                    outcomes: appendPreparedOutcomes,
                                                    matchTeams: 2,
                                                })
                                            }}
                                            sx={{width: 1}}>
                                            Add Group
                                        </Button>
                                    </Box>
                                </>
                            )}
                        </Stack>
                    </Stack>
                </Stack>
            )}
        />
    )
}

export default CompetitionSetupRound

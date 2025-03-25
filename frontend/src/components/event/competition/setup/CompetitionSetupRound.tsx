import {CheckboxElement, Controller, useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import {Box, Button, Checkbox, FormControlLabel, Stack, Typography} from '@mui/material'
import {CompetitionSetupForm} from '@components/event/competition/setup/CompetitionSetup.tsx'
import CompetitionSetupMatch from '@components/event/competition/setup/CompetitionSetupMatch.tsx'
import {useEffect} from 'react'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {getWeightings} from '@components/event/competition/setup/common.ts'

type Props = {
    round: {index: number; id: string}
    formContext: UseFormReturn<CompetitionSetupForm>
    removeRound: (index: number) => void
    teamCounts: {thisRound: number; nextRound: number}
    getRoundTeamCountWithoutMatch: (ignoreMatchIndex: number) => number
}
const CompetitionSetupRound = ({round, formContext, removeRound, teamCounts, ...props}: Props) => {
    const defaultTeamSize = 2

    const watchUseDefaultSeeding = formContext.watch(
        ('rounds[' + round.index + '].useDefaultSeeding') as `rounds.${number}.useDefaultSeeding`,
    )

    const {
        fields: matchFields,
        append: appendMatch,
        remove: removeMatch,
        move: moveMatch,
    } = useFieldArray({
        control: formContext.control,
        name: `rounds.${round.index}.matches`,
    })

    const watchMatchFields = formContext.watch(`rounds.${round.index}.matches`)

    const weightings = getWeightings(watchMatchFields?.length ?? 0).filter(v => v !== -1)

    // When appending or removing a match, the outcomes are updated (if default seeding is active)
    useEffect(() => {
        if (watchUseDefaultSeeding) {
            updateOutcomes(round.index, true, teamCounts.nextRound)
        }
    }, [watchMatchFields?.length, watchUseDefaultSeeding])

    function findLowestMissingOutcome(
        yetUnregisteredOutcomes: number[],
        ignoreMatchIndex?: number,
    ): number {
        const takenOutcomes =
            watchMatchFields
                ?.filter((_, index) => index !== ignoreMatchIndex)
                ?.map(v => v.outcomes)
                .flat()
                .map(v => v?.outcome ?? 0) ?? []

        const set = new Set([...takenOutcomes, ...yetUnregisteredOutcomes])
        let i = 1
        while (set.has(i)) {
            i++
        }
        return i
    }

    // Default match outcomes that are inserted when appending a match - When Default seeding is enabled they are overwritten by the useEffect above
    const appendMatchOutcomes: {outcome: number}[] = []
    for (let i = 0; i < defaultTeamSize; i++) {
        appendMatchOutcomes.push({
            outcome: findLowestMissingOutcome(appendMatchOutcomes.map(v => v.outcome)),
        })
    }

    const setOutcomeValuesForMatch = (
        roundIndex: number,
        matchIndex: number,
        outcomes: number[],
    ) => {
        const outcomesFormPath =
            `rounds[${roundIndex}].matches[${matchIndex}].outcomes` as `rounds.${number}.matches.${number}.outcomes`

        formContext.setValue(
            outcomesFormPath,
            outcomes.map(v => ({outcome: v})),
        )

        outcomes.forEach((_, i) => {
            const path =
                `rounds[${roundIndex}].matches[${matchIndex}].outcomes[${i}].outcome` as `rounds.${number}.matches.${number}.outcomes.${number}.outcome`
            formContext.setValue(path, outcomes[i])
        })
    }

    // Updates the outcome fields by default seeding - Depends on the "Teams" values of this round
    const updateOutcomes = (
        roundIndex: number,
        repeatForPreviousRound: boolean,
        nextRoundTeams: number,
    ) => {
        const matches = formContext.getValues(
            `rounds[${roundIndex}].matches` as `rounds.${number}.matches`,
        )

        // The matches are ordered by their weighting
        const matchesSortedByWeighting = getWeightings(matches?.length ?? 0).filter(v => v !== -1)

        const matchesInCorrectWeightingPos = matchesSortedByWeighting.map(v =>
            matches?.find((_, matchIndex) => matchIndex === v - 1),
        )

        // This list contains the original indexes of "matches" but is ordered by weighting
        const originalIndexesInWeightingPosOrder = matchesSortedByWeighting
            .map(v => matches?.findIndex((_, matchIndex) => matchIndex === v - 1))
            .filter(v => v !== undefined)

        const matchListHasUndefined =
            matchesInCorrectWeightingPos.filter(v => v === undefined).length > 0

        const sortedMatches = matchesInCorrectWeightingPos.filter(v => v !== undefined)

        console.log('sorted', sortedMatches)

        if (!matchListHasUndefined) {
            const highestDefinedTeamCount =
                sortedMatches.length > 0
                    ? Number(
                          sortedMatches
                              .map(v => v.teams)
                              .reduce((acc, val) => {
                                  if (val === undefined) {
                                      return acc
                                  } else if (acc === undefined) {
                                      return val
                                  } else {
                                      return Number(val) > Number(acc) ? val : acc
                                  }
                              }),
                      )
                    : 0

            const definedTeamsMatches = sortedMatches.filter(v => v.teams !== '')
            const definedTeamsCount =
                definedTeamsMatches !== undefined && definedTeamsMatches.length > 0
                    ? definedTeamsMatches
                          ?.map(v => Number(v.teams))
                          .reduce((acc, val) => +acc + +val)
                    : 0

            const teamsForUndefinedTeamsMatches = nextRoundTeams - (definedTeamsCount ?? 0)

            const undefinedTeamsMatchesCount = sortedMatches.filter(v => v.teams === '').length
            const teamsForEachUndefinedTeamsMatch =
                undefinedTeamsMatchesCount !== undefined && undefinedTeamsMatchesCount !== 0
                    ? Math.ceil(teamsForUndefinedTeamsMatches / undefinedTeamsMatchesCount)
                    : 0

            // The highest "Teams" value a match in this round has. Matches with undefined teams are also taken into account, based on the following round
            // This value defines, how often the for loop goes through each match to distribute the outcomes
            const highestTeamsValue =
                highestDefinedTeamCount > teamsForEachUndefinedTeamsMatch
                    ? highestDefinedTeamCount
                    : teamsForEachUndefinedTeamsMatch

            const getLowest = (taken: number[]) => {
                const set = new Set(taken)
                let i = 1
                while (set.has(i)) {
                    i++
                }
                return i
            }

            const newOutcomes: {outcomes: number[]}[] =
                sortedMatches.map(() => ({outcomes: []})) ?? []
            const takenOutcomes: number[] = []
            for (let i = 0; i < highestTeamsValue; i++) {
                const addOutcomeToMatch = (j: number) => {
                    const lowest = getLowest(takenOutcomes)

                    const teamCountUndefined = Number(sortedMatches[j].teams) === 0

                    if (
                        (Number(sortedMatches[j].teams) > newOutcomes[j].outcomes.length ||
                            teamCountUndefined) &&
                        (undefinedTeamsMatchesCount === 0 || takenOutcomes.length < nextRoundTeams)
                    ) {
                        newOutcomes[j].outcomes.push(lowest)
                        takenOutcomes.push(lowest)
                    }
                }
                // This creates the default seeding (A -> B -> C -> B -> A -> B...)
                if (i % 2 === 0) {
                    for (let j = 0; j < newOutcomes.length; j++) {
                        addOutcomeToMatch(j)
                    }
                } else {
                    for (let j = newOutcomes.length - 1; j > -1; j--) {
                        addOutcomeToMatch(j)
                    }
                }
            }

            const outcomesRevertedToNormalOrder = originalIndexesInWeightingPosOrder
                .map(v => newOutcomes.find((_, matchIndex) => matchIndex === v))
                .filter(v => v !== undefined)
            console.log('new outcomes', outcomesRevertedToNormalOrder)

            outcomesRevertedToNormalOrder?.forEach((val, matchIndex) => {
                setOutcomeValuesForMatch(roundIndex, matchIndex, val.outcomes)
            })

            // To prevent infinit loop because of the recursive call
            if (repeatForPreviousRound) {
                const newTeamsCount = outcomesRevertedToNormalOrder
                    .flat()
                    .map(v => v.outcomes)
                    .flat().length
                updatePreviousRoundOutcomes(newTeamsCount)
            }
        }
    }

    const updatePreviousRoundOutcomes = (thisRoundTeams: number) => {
        if (round.index - 1 >= 0) {
            const prevRoundMatches = formContext.getValues(
                `rounds[${round.index - 1}].matches` as `rounds.${number}.matches`,
            )
            if (prevRoundMatches) {
                // If the previous round has a match with undefined teams, the outcomes of that round are updated
                if (prevRoundMatches.filter(v => v.teams === '').length > 0) {
                    updateOutcomes(round.index - 1, false, thisRoundTeams)
                }
            }
        }
    }

    const roundHasDuplicatable = watchMatchFields?.find(v => v.duplicatable === true) !== undefined

    return (
        <Controller
            name={'rounds[' + round.index + '].useDefaultSeeding'}
            render={({
                field: {onChange: useDefSeedingOnChange, value: useDefSeedingValue = true},
            }) => (
                <Stack spacing={2} sx={{border: 1, borderColor: 'blue', p: 2}}>
                    <Box
                        sx={{
                            width: 1,
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                        }}>
                        <FormControlLabel
                            control={
                                <Checkbox
                                    checked={useDefSeedingValue}
                                    onChange={useDefSeedingOnChange}
                                />
                            }
                            label="[todo] Use default seeding"
                        />
                        <Typography>Max Teams: {teamCounts.thisRound}</Typography>
                    </Box>
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
                        <Stack direction="row" spacing={2} alignItems={'center'}>
                            {matchFields.map((matchField, matchIndex) => (
                                <CompetitionSetupMatch
                                    key={matchField.id}
                                    formContext={formContext}
                                    round={round}
                                    match={{index: matchIndex, id: matchField.id}}
                                    removeMatch={() => removeMatch(matchIndex)}
                                    findLowestMissingOutcome={findLowestMissingOutcome}
                                    teamCounts={{
                                        thisRoundWithoutThis:
                                            props.getRoundTeamCountWithoutMatch(matchIndex),
                                        nextRound: teamCounts.nextRound,
                                    }}
                                    updateRoundOutcomes={updateOutcomes}
                                    useDefaultSeeding={watchUseDefaultSeeding}
                                    setOutcomeValuesForMatch={setOutcomeValuesForMatch}
                                    updatePreviousRoundOutcomes={updatePreviousRoundOutcomes}
                                    roundHasDuplicatable={roundHasDuplicatable}
                                    moveMatch={moveMatch}
                                    maxMatchIndex={matchFields.length - 1}
                                    weighting={weightings[matchIndex]}
                                />
                            ))}
                            <Box>
                                <Button
                                    variant="outlined"
                                    onClick={() => {
                                        appendMatch({
                                            duplicatable: false,
                                            weighting: matchFields.length + 1,
                                            teams: `${defaultTeamSize}`,
                                            name: '',
                                            outcomes: appendMatchOutcomes,
                                            position: matchFields.length,
                                        })
                                    }}
                                    sx={{width: 1}}>
                                    Add Match
                                </Button>
                            </Box>
                        </Stack>
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
                </Stack>
            )}
        />
    )
}

export default CompetitionSetupRound

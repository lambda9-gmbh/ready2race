import {Controller, useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import {Box, Button, Checkbox, FormControlLabel, Stack, Typography} from '@mui/material'
import {CompetitionSetupForm} from '@components/event/competition/setup/CompetitionSetup.tsx'
import CompetitionSetupMatch from '@components/event/competition/setup/CompetitionSetupMatch.tsx'
import {useEffect} from 'react'

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
    } = useFieldArray({
        control: formContext.control,
        name: `rounds.${round.index}.matches`,
    })

    const watchMatchFields = formContext.watch(`rounds.${round.index}.matches`)

    // When appending or removing a match, the outcomes are updated (if default seeding is active)
    useEffect(() => {
        console.log(watchUseDefaultSeeding)
        if (watchUseDefaultSeeding) {
            onTeamsChanged()
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

    const appendMatchOutcomes: {outcome: number}[] = []
    for (let i = 0; i < defaultTeamSize; i++) {
        appendMatchOutcomes.push({
            outcome: findLowestMissingOutcome(appendMatchOutcomes.map(v => v.outcome)),
        })
    }

    const setOutcomeValuesForMatch = (matchIndex: number, outcomes: number[]) => {
        const outcomesFormPath = ('rounds[' +
            round.index +
            '].matches[' +
            matchIndex +
            '].outcomes') as `rounds.${number}.matches.${number}.outcomes`
        formContext.setValue(
            outcomesFormPath,
            outcomes.map(v => ({outcome: v})),
        )

        outcomes.forEach((_, i) => {
            const foo = ('rounds[' +
                round.index +
                '].matches[' +
                matchIndex +
                '].outcomes[' +
                i +
                '].outcome') as `rounds.${number}.matches.${number}.outcomes.${number}.outcome`
            formContext.setValue(foo, outcomes[i])
        })
    }

    const onTeamsChanged = () => {
        const highestDefinedTeamCount =
            watchMatchFields && watchMatchFields.length > 0
                ? Number(
                      watchMatchFields
                          ?.map(v => v.teams)
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

        const definedTeamsMatches = watchMatchFields?.filter(v => v.teams !== '')
        const definedTeamsCount =
            definedTeamsMatches !== undefined && definedTeamsMatches.length > 0
                ? definedTeamsMatches?.map(v => Number(v.teams)).reduce((acc, val) => +acc + +val)
                : 0

        const teamsForUndefinedTeamsMatches = teamCounts.nextRound - (definedTeamsCount ?? 0)

        const undefinedTeamsMatchesCount = watchMatchFields?.filter(v => v.teams === '').length
        const teamsForEachUndefinedTeamsMatch =
            undefinedTeamsMatchesCount !== undefined && undefinedTeamsMatchesCount !== 0
                ? Math.ceil(teamsForUndefinedTeamsMatches / undefinedTeamsMatchesCount)
                : 0

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
            watchMatchFields?.map(() => ({outcomes: []})) ?? []
        const takenOutcomes: number[] = []
        for (let i = 0; i < highestTeamsValue; i++) {
            const addOutcomeToMatch = (j: number) => {
                const lowest = getLowest(takenOutcomes)

                const teamCountUndefined = Number(watchMatchFields?.[j].teams) === 0

                if (
                    (Number(watchMatchFields?.[j].teams) > newOutcomes[j].outcomes.length ||
                        teamCountUndefined) &&
                    (undefinedTeamsMatchesCount === 0 ||
                        takenOutcomes.length < teamCounts.nextRound)
                ) {
                    newOutcomes[j].outcomes.push(lowest)
                    takenOutcomes.push(lowest)
                }
            }
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

        newOutcomes?.forEach((val, matchIndex) => {
            setOutcomeValuesForMatch(matchIndex, val.outcomes)
        })
    }

    return (
        <Controller
            name={'rounds[' + round.index + '].useDefaultSeeding'}
            render={({
                field: {onChange: useDefSeedingOnChange, value: useDefSeedingValue = true},
            }) => (
                <>
                    <Box sx={{width: 1, display: 'flex', justifyContent: 'space-between'}}>
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
                    <Stack
                        direction="row"
                        spacing={2}
                        justifyContent="space-between"
                        sx={{alignItems: 'center'}}>
                        <Stack
                            direction="row"
                            spacing={2}
                            alignItems={'center'}
                            sx={{border: 1, borderColor: 'blue', p: 2}}>
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
                                    onTeamsChanged={onTeamsChanged}
                                    useDefaultSeeding={watchUseDefaultSeeding}
                                    setOutcomeValuesForMatch={setOutcomeValuesForMatch}
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
                </>
            )}
        />
    )
}

export default CompetitionSetupRound

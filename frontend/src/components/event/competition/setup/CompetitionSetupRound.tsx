import {CheckboxElement, Controller, useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import {Box, Button, Checkbox, FormControlLabel, Stack, Typography} from '@mui/material'
import {CompetitionSetupForm} from '@components/event/competition/setup/CompetitionSetup.tsx'
import CompetitionSetupMatch from '@components/event/competition/setup/CompetitionSetupMatch.tsx'
import {useEffect} from 'react'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {
    getWeightings,
    setOutcomeValuesForMatch, sortBackToOriginalOrder,
    updateOutcomes,
    updatePreviousRoundOutcomes,
} from '@components/event/competition/setup/common.ts'

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
            updateOutcomes(formContext, round.index, true, teamCounts.nextRound)
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

    const roundHasDuplicatable = watchMatchFields?.find(v => v.duplicatable === true) !== undefined


    // Todo: Clean this up and merge with function in common
    const watchPrevRoundMatches =
        round.index > 0
            ? formContext.watch(`rounds[${round.index - 1}].matches` as `rounds.${number}.matches`)
            : undefined

    const prevRoundOutcomes = watchPrevRoundMatches
        ?.map(v => (v.outcomes ? v.outcomes?.map(outcome => outcome.outcome).flat() : []))
        .flat()
        .sort((a, b) => a - b)
        .slice(0, teamCounts.thisRound)

    const highest = 20 // TODO

    const matchups: number[][] = []
    for (let i = 0; i < (watchMatchFields?.length ?? 0); i++) {
        matchups.push([])
    }
    if (prevRoundOutcomes !== undefined && watchMatchFields !== undefined) {
        let participantsTaken = 0
        for (let i = 0; i < highest; i++) {
            const addToList = (matchIndex: number) => {
                if (Number(watchMatchFields[matchIndex].teams) > matchups[matchIndex].length) {
                    matchups[matchIndex].push(prevRoundOutcomes[participantsTaken])
                    participantsTaken += 1
                }
            }

            if (i % 2 === 0) {
                for (let j = 0; j < watchMatchFields.length; j++) {
                    addToList(j)
                }
            } else {
                for (let j = watchMatchFields.length - 1; j > -1; j--) {
                    addToList(j)
                }
            }
        }
    }

    const matchesSortedByWeighting = getWeightings(watchMatchFields?.length ?? 0)

    const matchupsInOriginalOrder = watchMatchFields ? sortBackToOriginalOrder(watchMatchFields, matchesSortedByWeighting, matchups).map(v => v.result) : []

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
                        <Stack direction="row" spacing={2}>
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
                                    updateRoundOutcomes={(...props) =>
                                        updateOutcomes(formContext, round.index, ...props)
                                    }
                                    useDefaultSeeding={watchUseDefaultSeeding}
                                    setOutcomeValuesForMatch={(...props) =>
                                        setOutcomeValuesForMatch(formContext, round.index, ...props)
                                    }
                                    updatePreviousRoundOutcomes={(...props) =>
                                        updatePreviousRoundOutcomes(
                                            formContext,
                                            round.index - 1,
                                            ...props,
                                        )
                                    }
                                    roundHasDuplicatable={roundHasDuplicatable}
                                    moveMatch={moveMatch}
                                    maxMatchIndex={matchFields.length - 1}
                                    weighting={weightings[matchIndex]}
                                    participants={matchupsInOriginalOrder[matchIndex]}
                                />
                            ))}
                            <Box sx={{alignSelf: 'center'}}>
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

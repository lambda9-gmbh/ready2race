import {
    CheckboxElement,
    Controller,
    FieldArrayWithId,
    useFieldArray,
    UseFormReturn,
} from 'react-hook-form-mui'
import {Box, Button, Checkbox, FormControlLabel, Stack, Typography} from '@mui/material'
import {CompetitionSetupForm} from '@components/event/competition/setup/CompetitionSetup.tsx'
import CompetitionSetupMatch from '@components/event/competition/setup/CompetitionSetupMatch.tsx'
import {useEffect} from 'react'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {
    getHighestTeamsCount,
    getWeightings,
    setOutcomeValuesForMatch,
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

    const watchPrevRoundMatches =
        round.index > 0
            ? formContext.watch(`rounds[${round.index - 1}].matches` as `rounds.${number}.matches`)
            : undefined

    const prevRoundOutcomes = watchPrevRoundMatches
        ?.map(v => (v.outcomes ? v.outcomes?.map(outcome => outcome.outcome).flat() : []))
        .flat()
        .sort((a, b) => a - b)
        .slice(0, teamCounts.thisRound)

    const highest = watchMatchFields
        ? getHighestTeamsCount(
              watchMatchFields.map(v => v.teams),
              teamCounts.nextRound,
          )
        : 0

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

    type MatchInfo = {
        originalIndex: number
        matchField: FieldArrayWithId<CompetitionSetupForm, `rounds.${number}.matches`, 'id'>
        participants: number[]
    }

    const matchInfos: MatchInfo[] = watchMatchFields
        ? watchMatchFields.map((_, index) => ({
              originalIndex: index,
              matchField: matchFields[index],
              participants: matchups[index],
          }))
        : []

    // This is just for the display - in the fieldArray, the weightings are in order (1,2,3...)
    const matchInfosSortedByWeighting = getWeightings(matchInfos.length).map(v => matchInfos[v - 1])

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
                            {matchInfosSortedByWeighting.map((matchInfo, _) => (
                                <CompetitionSetupMatch
                                    key={matchInfo.matchField.id}
                                    formContext={formContext}
                                    round={round}
                                    match={{
                                        index: matchInfo.originalIndex,
                                        id: matchInfo.matchField.id,
                                    }}
                                    removeMatch={() => removeMatch(matchInfo.originalIndex)}
                                    findLowestMissingOutcome={findLowestMissingOutcome}
                                    teamCounts={{
                                        thisRoundWithoutThis: props.getRoundTeamCountWithoutMatch(
                                            matchInfo.originalIndex,
                                        ),
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
                                    participants={matchInfo.participants}
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

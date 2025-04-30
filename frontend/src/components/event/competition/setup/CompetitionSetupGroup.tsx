import {Autocomplete, Box, Divider, IconButton, Stack, TextField, Typography} from '@mui/material'
import {
    CompetitionSetupMatchOrGroupProps,
    onTeamsChanged,
} from '@components/event/competition/setup/common.ts'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {useFieldArray} from 'react-hook-form-mui'
import CompetitionSetupParticipants from '@components/event/competition/setup/CompetitionSetupParticipants.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {Delete} from '@mui/icons-material'
import {useState} from 'react'
import {AutocompleteOption} from '@utils/types.ts'

type Props = CompetitionSetupMatchOrGroupProps & {
    getLowestGroupMatchPosition: (takenPositions?: number[]) => number // takenPositions is necessary for appending multiple Matches in one render
}
const CompetitionSetupGroup = ({formContext, roundIndex, fieldInfo, ...props}: Props) => {
    const outcomesFormPath =
        `rounds[${roundIndex}].groups[${fieldInfo.index}].outcomes` as `rounds.${number}.groups.${number}.outcomes`

    const {fields: outcomeFields} = useFieldArray({
        control: formContext.control,
        name: outcomesFormPath,
    })

    const watchOutcomes = formContext.watch(outcomesFormPath)

    const controlledOutcomeFields = outcomeFields.map((field, index) => ({
        ...field,
        ...watchOutcomes?.[index],
    }))

    const groupMatchesFormPath =
        `rounds[${roundIndex}].groups[${fieldInfo.index}].matches` as `rounds.${number}.groups.${number}.matches`

    const {
        fields: groupMatchFields,
        append: appendGroupMatch,
        remove: removeGroupMatch,
    } = useFieldArray({
        control: formContext.control,
        name: groupMatchesFormPath,
    })

    const watchGroupMatches = formContext.watch(groupMatchesFormPath)

    const watchTeams = formContext.watch(
        `rounds[${roundIndex}].groups[${fieldInfo.index}].teams` as `rounds.${number}.groups.${number}.teams`,
    )
    const teamsNumber = Number(watchTeams)

    const watchMatchTeams = formContext.watch(
        `rounds[${roundIndex}].groups[${fieldInfo.index}].matchTeams` as `rounds.${number}.groups.${number}.matchTeams`,
    )

    // Calculates the Matchups for each match based on their position
    const possibleMatchups: number[][] = []
    const recursiveMatchupCalculation = (indexes: number[], maxLoopDepth: number) => {
        if (indexes.length === maxLoopDepth) {
            const matchup = indexes.map(v => v + 1)
            possibleMatchups.push(matchup)
        }

        for (let j = indexes[indexes.length - 1] + 1; j < teamsNumber; j++) {
            if (indexes.length < maxLoopDepth) {
                recursiveMatchupCalculation([...indexes, j], maxLoopDepth)
            }
        }
    }
    for (let i = 0; i < teamsNumber; i++) {
        recursiveMatchupCalculation([i], Number(watchMatchTeams))
    }

    const getRepeatingMatchupIteration = (
        weighting: number,
        addWeightingToCount: boolean,
    ): {iteration: number; iterationCount: number} => {
        const sameMatchups = watchGroupMatches.filter(
            v => v.weighting % possibleMatchups.length === weighting % possibleMatchups.length,
        )
        return {
            iteration: sameMatchups.filter(v => v.weighting < weighting).length + 1,
            iterationCount: sameMatchups.length + (addWeightingToCount ? 1 : 0),
        }
    }

    // todo: save participants in match.participants and not the weighting

    const getMatchupString = (weighting: number, addWeightingToCount: boolean): string => {
        const matchup =
            weighting > 0 ? possibleMatchups[(weighting - 1) % possibleMatchups.length] : []

        const iteration = getRepeatingMatchupIteration(weighting, addWeightingToCount)

        return transformMatchupToString(matchup, iteration.iteration, iteration.iterationCount)
    }
    const transformMatchupToString = (
        matchup: number[],
        iteration: number,
        iterationCount: number,
    ) => {
        return (matchup.length ?? 0) > 0
            ? matchup.map(v => `#${v}`).join(' vs ') + // todo: combine with getMatchupString() from common
                  (iterationCount > 1 ? ` (${iteration}/${iterationCount})` : '')
            : ''
    }

    const [matchupSelect, setMatchupSelect] = useState<AutocompleteOption | null>(null)

    const weightingsInGroup = watchGroupMatches.map(m => m.weighting)

    // The matchups repeat after all matchups are listed - e.g. 6 possible matchups, Matchup 1 has weighting 1,7,13...
    // This way one matchup can be repeated multiple times
    const takeLowestWeightingForMatchup = (weighting: number) => {
        if (weightingsInGroup.find(v => v === weighting) === undefined) {
            return weighting
        } else {
            return takeLowestWeightingForMatchup(weighting + possibleMatchups.length)
        }
    }

    const autocompleteMatchups: AutocompleteOption[] = possibleMatchups
        .map((_, index) => ({
            weighting: takeLowestWeightingForMatchup(index + 1),
        }))
        .map(matchup => ({
            id: matchup.weighting.toString(),
            label: getMatchupString(matchup.weighting, true),
        }))

    const addAllMissingMatchupsKey = 'all'

    return (
        <Stack
            spacing={2}
            sx={{border: 1, borderColor: 'darkorange', p: 2, boxSizing: 'border-box'}}>
            <Typography sx={{textAlign: 'center'}}>Weighting: {fieldInfo.index + 1}</Typography>
            <Typography sx={{textAlign: 'center'}}>{props.participantsString}</Typography>
            <Divider />
            <FormInputText
                name={`rounds[${roundIndex}].groups[${fieldInfo.index}].name`}
                label={'Group name'}
            />
            <FormInputText
                name={`rounds.${roundIndex}.groups.${fieldInfo.index}.teams`}
                label={'Teams'}
                onChange={v =>
                    onTeamsChanged(
                        roundIndex,
                        Number(v.target.value),
                        props.useDefaultSeeding,
                        props.participantFunctions,
                        props.teamCounts,
                    )
                }
            />
            <FormInputNumber
                name={`rounds.${roundIndex}.groups.${fieldInfo.index}.matchTeams`}
                label={'Teams per match'}
                required
                integer={true}
                transform={{
                    output: value => Number(value.target.value),
                }}
            />
            <Stack spacing={2}>
                {groupMatchFields.map((matchField, matchIndex) => (
                    <Stack
                        key={matchField.id}
                        spacing={2}
                        sx={{border: 1, borderColor: 'grey', p: 1, boxSizing: 'border-box'}}>
                        <Stack
                            direction="row"
                            spacing={2}
                            sx={{justifyContent: 'space-between', alignItems: 'center'}}>
                            <Typography>{getMatchupString(matchField.weighting, false)}</Typography>
                            <IconButton onClick={() => removeGroupMatch(matchIndex)}>
                                <Delete />
                            </IconButton>
                        </Stack>
                        <FormInputText
                            name={`rounds[${roundIndex}].groups[${fieldInfo.index}].matches[${matchIndex}].name`}
                            label={'Match name'}
                        />
                        <FormInputNumber
                            name={`rounds[${roundIndex}].groups[${fieldInfo.index}].matches[${matchIndex}].position`}
                            label={'Execution order'}
                            required
                            integer
                            min={0}
                            transform={{
                                output: value => Number(value.target.value),
                            }}
                        />
                    </Stack>
                ))}
            </Stack>
            <Box sx={{alignSelf: 'center', width: 1, display: 'flex', flexDirection: 'column'}}>
                {/* Todo: Only Select, not Autocomplete */}
                <Autocomplete
                    options={[
                        {id: addAllMissingMatchupsKey, label: 'All'},
                        ...autocompleteMatchups,
                    ]}
                    renderInput={params => <TextField {...params} placeholder={'Add Match'} />}
                    value={matchupSelect}
                    onChange={(_e, newValue: AutocompleteOption) => {
                        setMatchupSelect(newValue)
                        if (newValue) {
                            const appendableGroupMatch = (weighting: number, position: number) => {
                                return {
                                    weighting: weighting,
                                    teams: '',
                                    name: '',
                                    outcomes: undefined,
                                    position: position,
                                }
                            }
                            if (newValue.id === addAllMissingMatchupsKey) {
                                const takenPositions: number[] = []
                                for (let i = 0; i < autocompleteMatchups.length; i++) {
                                    takenPositions.push(
                                        props.getLowestGroupMatchPosition(takenPositions),
                                    )
                                }
                                appendGroupMatch(
                                    autocompleteMatchups.map((m, i) =>
                                        appendableGroupMatch(Number(m?.id), takenPositions[i]),
                                    ),
                                )
                            } else {
                                appendGroupMatch(
                                    appendableGroupMatch(
                                        Number(newValue.id),
                                        props.getLowestGroupMatchPosition(),
                                    ),
                                )
                            }
                            setMatchupSelect(null)
                        }
                    }}
                    sx={{flex: 1}}
                />
            </Box>
            <CompetitionSetupParticipants
                fieldInfo={fieldInfo}
                roundIndex={roundIndex}
                controlledOutcomeFields={controlledOutcomeFields}
                useDefaultSeeding={props.useDefaultSeeding}
            />
        </Stack>
    )
}

export default CompetitionSetupGroup

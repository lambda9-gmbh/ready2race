import {Autocomplete, Box, Divider, IconButton, Stack, TextField, Typography} from '@mui/material'
import {
    CompetitionSetupMatchOrGroupProps,
    onTeamsChanged,
} from '@components/event/competition/setup/common.ts'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {useFieldArray} from 'react-hook-form-mui'
import CompetitionSetupOutcomes from '@components/event/competition/setup/CompetitionSetupOutcomes.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {Delete, KeyboardArrowDown, KeyboardArrowUp} from '@mui/icons-material'
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
        move: moveGroupMatch,
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
    const recursiveFoo = (indexes: number[], maxLoopDepth: number) => {
        if (indexes.length === maxLoopDepth) {
            const matchup = indexes.map(v => v + 1)
            possibleMatchups.push(matchup)
        }

        for (let j = indexes[indexes.length - 1] + 1; j < teamsNumber; j++) {
            if (indexes.length < maxLoopDepth) {
                recursiveFoo([...indexes, j], maxLoopDepth)
            }
        }
    }
    for (let i = 0; i < teamsNumber; i++) {
        recursiveFoo([i], Number(watchMatchTeams))
    }

    const getMatchupString = (matchIndex: number): string => {
        const weighting = watchGroupMatches[matchIndex]?.weighting ?? 0

        const matchup = possibleMatchups[weighting - 1] ? [...possibleMatchups[weighting - 1]] : []

        return transformMatchupToString(matchup)
    }
    const transformMatchupToString = (matchup: number[]) => {
        return (matchup.length ?? 0) > 0
            ? matchup
                  .map(v => v.toString())
                  .reduce(
                      (acc, val, currentIndex) =>
                          acc + (currentIndex !== 0 ? ' vs ' : '') + `#${val}`,
                  )
            : ''
    }

    const [missingMatchupSelect, setMissingMatchupSelect] = useState<AutocompleteOption | null>(
        null,
    )

    // Todo: Handle repeating matchups
    const weightings = watchGroupMatches.map(m => m.weighting)
    const missingMatchups: AutocompleteOption[] = possibleMatchups
        .map((m, index) => ({
            matchup: m,
            weighting: index + 1,
        }))
        .filter(matchup => weightings.find(v => v === matchup.weighting) === undefined)
        .map(matchup => ({
            id: matchup.weighting.toString(),
            label: transformMatchupToString(matchup.matchup),
        }))

    const addAllMissingMatchupsKey = 'all'

    // todo Other start value - if there are repeating matchups in the weightings -> set true
    const [allowRepeatingMatchups, setAllowRepeatingMatchups] = useState(false)

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
                        Number(v.target.value),
                        props.useDefaultSeeding,
                        props.outcomeFunctions,
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
                    <Stack key={matchField.id} direction="row">
                        <Stack direction="column" sx={{justifyContent: 'center'}}>
                            {matchIndex > 0 && (
                                <IconButton
                                    onClick={() => moveGroupMatch(matchIndex, matchIndex - 1)}>
                                    <KeyboardArrowUp />
                                </IconButton>
                            )}
                            {matchIndex < groupMatchFields.length - 1 && (
                                <IconButton
                                    onClick={() => moveGroupMatch(matchIndex, matchIndex + 1)}>
                                    <KeyboardArrowDown />
                                </IconButton>
                            )}
                        </Stack>
                        <Stack
                            spacing={2}
                            sx={{border: 1, borderColor: 'grey', p: 1, boxSizing: 'border-box'}}>
                            <Stack
                                direction="row"
                                spacing={2}
                                sx={{justifyContent: 'space-between', alignItems: 'center'}}>
                                <Typography>{getMatchupString(matchIndex)}</Typography>
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
                                integer={true}
                                transform={{
                                    output: value => Number(value.target.value),
                                }}
                            />
                        </Stack>
                    </Stack>
                ))}
            </Stack>
            {watchGroupMatches.length < possibleMatchups.length && (
                <Box sx={{alignSelf: 'center', width: 1, display: 'flex', flexDirection: 'column'}}>
                    <Autocomplete
                        options={[{id: addAllMissingMatchupsKey, label: 'All'}, ...missingMatchups]}
                        renderInput={params => <TextField {...params} placeholder={'Add Match'} />}
                        value={missingMatchupSelect}
                        onChange={(_e, newValue: AutocompleteOption) => {
                            setMissingMatchupSelect(newValue)
                            if (newValue) {
                                const appendableGroupMatch = (
                                    weighting: number,
                                    position: number,
                                ) => {
                                    return {
                                        duplicatable: false,
                                        weighting: weighting,
                                        teams: '',
                                        name: '',
                                        outcomes: undefined,
                                        position: position,
                                    }
                                }
                                if (newValue.id === addAllMissingMatchupsKey) {
                                    const takenPositions: number[] = []
                                    for (let i = 0; i < missingMatchups.length; i++) {
                                        takenPositions.push(
                                            props.getLowestGroupMatchPosition(takenPositions),
                                        )
                                    }
                                    appendGroupMatch(
                                        missingMatchups.map((m, i) =>
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
                                setMissingMatchupSelect(null)
                            }
                        }}
                        sx={{flex: 1}}
                    />
                </Box>
            )}
            <CompetitionSetupOutcomes
                fieldInfo={fieldInfo}
                roundIndex={roundIndex}
                controlledOutcomeFields={controlledOutcomeFields}
                useDefaultSeeding={props.useDefaultSeeding}
            />
        </Stack>
    )
}

export default CompetitionSetupGroup

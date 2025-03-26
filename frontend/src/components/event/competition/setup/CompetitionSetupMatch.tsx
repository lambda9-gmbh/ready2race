import {CheckboxElement, useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import {CompetitionSetupForm} from '@components/event/competition/setup/CompetitionSetup.tsx'
import {Button, Divider, Grid2, Stack, Typography} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'

type Props = {
    formContext: UseFormReturn<CompetitionSetupForm>
    round: {index: number; id: string}
    match: {index: number; id: string}
    removeMatch: (index: number) => void
    findLowestMissingOutcome: (
        yetUnregisteredOutcomes: number[],
        ignoreMatchIndex: number,
    ) => number
    teamCounts: {thisRoundWithoutThis: number; nextRound: number}
    updateRoundOutcomes: (repeatForPreviousRound: boolean, nextRoundTeams: number) => void
    useDefaultSeeding: boolean
    setOutcomeValuesForMatch: (matchIndex: number, outcomes: number[]) => void
    updatePreviousRoundOutcomes: (thisRoundTeams: number) => void
    roundHasDuplicatable: boolean
    moveMatch: (from: number, to: number) => void
    maxMatchIndex: number
    weighting: number
    participants: number[]
}
const CompetitionSetupMatch = ({formContext, round, match, ...props}: Props) => {
    const outcomesFormPath =
        `rounds[${round.index}].matches[${match.index}].outcomes` as `rounds.${number}.matches.${number}.outcomes`

    const {fields: outcomeFields} = useFieldArray({
        control: formContext.control,
        name: outcomesFormPath,
    })

    const watchOutcomeFields = formContext.watch(outcomesFormPath)

    const controlledOutcomeFields = outcomeFields.map((field, index) => ({
        ...field,
        ...watchOutcomeFields?.[index],
    }))

    const onTeamsChanged = (teamsValue: number) => {
        if (!props.useDefaultSeeding) {
            const outcomes: number[] = []
            // if teamsValue is 0 (undefined) the necessaryTeamsValue is calculated from the needed teams for next round
            const necessaryTeams =
                teamsValue > 0
                    ? teamsValue
                    : props.teamCounts.nextRound - props.teamCounts.thisRoundWithoutThis

            for (let i = 0; i < necessaryTeams; i++) {
                outcomes.push(props.findLowestMissingOutcome(outcomes, match.index))
            }

            props.setOutcomeValuesForMatch(match.index, outcomes)
            props.updatePreviousRoundOutcomes(
                outcomes.length + props.teamCounts.thisRoundWithoutThis,
            )
        } else {
            props.updateRoundOutcomes(true, props.teamCounts.nextRound)
        }
    }

    // Only one duplicatable in a round is allowed
    const watchDuplicatable = formContext.watch(
        `rounds[${round.index}].matches[${match.index}].duplicatable` as `rounds.${number}.matches.${number}.duplicatable`,
    )
    const duplicatableCheckDisabled = props.roundHasDuplicatable && watchDuplicatable === false

    const matchupStrings: string[] = []
    props.participants.forEach((p, index) => {
        if (index !== 0) {
            matchupStrings.push(' vs ')
        }
        matchupStrings.push(`#${p}`)
    })
    const matchupString = matchupStrings.join('')

    return (
        <Stack direction="column" spacing={1} sx={{maxWidth: 300}}>
            <Stack
                spacing={2}
                sx={{
                    border: 1,
                    borderColor: 'grey',
                    width: 1,
                    p: 2,
                    boxSizing: 'border-box',
                }}>
                <Typography sx={{textAlign: 'center'}}>Weighting: {props.weighting}</Typography>
                <Typography sx={{textAlign: 'center'}}>{matchupString}</Typography>
                <Divider />
                <FormInputText
                    name={`rounds[${round.index}].matches[${match.index}].name`}
                    label={'Match name'}
                />
                <FormInputText
                    name={`rounds.${round.index}.matches.${match.index}.teams`}
                    label={'Teams'}
                    onChange={v => onTeamsChanged(Number(v.target.value))}
                />
                <FormInputNumber
                    name={`rounds[${round.index}].matches[${match.index}].position`}
                    label={'Execution order'}
                    required
                    integer={true}
                    transform={{
                        output: value => Number(value.target.value),
                    }}
                />
                <CheckboxElement
                    name={`rounds[${round.index}].matches[${match.index}].duplicatable`}
                    disabled={duplicatableCheckDisabled}
                    required={false}
                    label={<FormInputLabel label={'Duplicatable'} required={false} horizontal />}
                />
                <Divider />
                <Typography variant="subtitle1">Resulting Seeds</Typography>
                {props.useDefaultSeeding ? (
                    <Grid2 container sx={{width: 1, justifyContent: 'space-between'}}>
                        {controlledOutcomeFields.map((outcome, outcomeIndex) => (
                            <Grid2 key={outcome.id} size={6} sx={{maxWidth: 100}}>
                                <Typography>
                                    #{outcomeIndex + 1}: {outcome.outcome}
                                </Typography>
                            </Grid2>
                        ))}
                    </Grid2>
                ) : (
                    <Stack spacing={2} sx={{border: 1, borderColor: 'lightgrey', p: 1}}>
                        {controlledOutcomeFields.map((outcome, outcomeIndex) => (
                            <Stack
                                key={outcome.id}
                                direction="row"
                                spacing={1}
                                sx={{alignItems: 'center'}}>
                                <Typography>#{outcomeIndex + 1}</Typography>
                                <FormInputNumber
                                    key={`${match.id}-${outcomeIndex}-${outcome}`}
                                    name={`rounds[${round.index}].matches[${match.index}].outcomes[${outcomeIndex}].outcome`}
                                    required
                                    transform={{
                                        output: value => Number(value.target.value),
                                    }}
                                />
                            </Stack>
                        ))}
                    </Stack>
                )}
            </Stack>
            <Button
                variant="outlined"
                onClick={() => {
                    props.removeMatch(match.index)
                }}>
                Remove Match
            </Button>
        </Stack>
    )
}

export default CompetitionSetupMatch

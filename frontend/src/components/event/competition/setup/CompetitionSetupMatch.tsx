import {CheckboxElement, useFieldArray} from 'react-hook-form-mui'
import {Divider, Grid2, Stack, Typography} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {
    CompetitionSetupMatchOrGroupProps,
    onTeamsChanged,
} from '@components/event/competition/setup/common.ts'

type Props = CompetitionSetupMatchOrGroupProps
const CompetitionSetupMatch = ({
    formContext,
    roundIndex,
    fieldInfo,
    ...props
}: Props) => {
    const outcomesFormPath =
        `rounds[${roundIndex}].matches[${fieldInfo.index}].outcomes` as `rounds.${number}.matches.${number}.outcomes`

    const {fields: outcomeFields} = useFieldArray({
        control: formContext.control,
        name: outcomesFormPath,
    })

    const watchOutcomes = formContext.watch(outcomesFormPath)

    const controlledOutcomeFields = outcomeFields.map((field, index) => ({
        ...field,
        ...watchOutcomes?.[index],
    }))


    // Only one duplicatable in a round is allowed
    const watchDuplicatable = formContext.watch(
        `rounds[${roundIndex}].matches[${fieldInfo.index}].duplicatable` as `rounds.${number}.matches.${number}.duplicatable`,
    )
    const duplicatableCheckDisabled = props.roundHasDuplicatable && watchDuplicatable === false

    return (
        <Stack
            spacing={2}
            sx={{
                border: 1,
                borderColor: 'grey',
                p: 2,
                boxSizing: 'border-box',
            }}>
            <Typography sx={{textAlign: 'center'}}>Weighting: {fieldInfo.index + 1}</Typography>
            <Typography sx={{textAlign: 'center'}}>{props.participantsString}</Typography>
            <Divider />
            <FormInputText
                name={`rounds[${roundIndex}].matches[${fieldInfo.index}].name`}
                label={'Match name'}
            />
            <FormInputText
                name={`rounds.${roundIndex}.matches.${fieldInfo.index}.teams`}
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
                name={`rounds[${roundIndex}].matches[${fieldInfo.index}].position`}
                label={'Execution order'}
                required
                integer={true}
                transform={{
                    output: value => Number(value.target.value),
                }}
            />
            <CheckboxElement
                name={`rounds[${roundIndex}].matches[${fieldInfo.index}].duplicatable`}
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
                                key={`${fieldInfo.id}-${outcomeIndex}-${outcome}`}
                                name={`rounds[${roundIndex}].matches[${fieldInfo.index}].outcomes[${outcomeIndex}].outcome`}
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
    )
}

export default CompetitionSetupMatch

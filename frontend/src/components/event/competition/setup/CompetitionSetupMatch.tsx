import {Controller, SwitchElement, useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import {CompetitionSetupForm} from '@components/event/competition/setup/CompetitionSetup.tsx'
import {Button, Stack, TextField} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'

type Props = {
    formContext: UseFormReturn<CompetitionSetupForm>
    round: {index: number; id: string}
    match: {index: number; id: string}
    removeMatch: (index: number) => void
}
const CompetitionSetupMatch = ({formContext, round, match, ...props}: Props) => {
    const {fields: outcomeFields} = useFieldArray({
        control: formContext.control,
        name: ('rounds[' +
            round.index +
            '].matches[' +
            match.index +
            '].outcomes') as `rounds.${number}.matches.${number}.outcomes`,
        keyName: 'fieldId',
    })

    const watchOutcomeFields = formContext.watch(
        ('rounds[' +
            round.index +
            '].matches[' +
            match.index +
            '].outcomes') as `rounds.${number}.matches.${number}.outcomes`,
    )

    const controlledFields = outcomeFields.map((field, index) => ({
        ...field,
        ...watchOutcomeFields?.[index],
    }))

    const onTeamsChanged = (value: number | undefined) => {
        function setVal(v: {outcome: number}[]) {
            formContext.setValue(
                ('rounds[' +
                    round.index +
                    '].matches[' +
                    match.index +
                    '].outcomes') as `rounds.${number}.matches.${number}.outcomes`,
                v,
            )
        }

        console.log('Changed', value, controlledFields)
        if (value === undefined || value < 1 || controlledFields === undefined) {
            setVal([]) // todo: depends on following round when it is undefined/left empty
        } else if (value < controlledFields.length) {
            setVal(controlledFields?.slice(0, value) ?? [])
        } else if (value > controlledFields.length) {
            const l: {outcome: number}[] = []
            for (let i = 0; i < value - controlledFields.length; i++) {
                l.push({outcome: 9})
            }
            setVal([...controlledFields, ...l])
        }
    }

    return (
        <Stack direction="column" spacing={1} key={`match-${round.id}-${match.id}`}>
            <Controller
                name={`rounds.${round.index}.matches.${match.index}.teams`}
                control={formContext.control}
                render={({field: teamsField}) => (
                    <>
                        <Stack
                            sx={{
                                border: 1,
                                borderColor: 'grey',
                                width: 1,
                                p: 2,
                                boxSizing: 'border-box',
                            }}>
                            <FormInputText
                                name={
                                    'rounds[' + round.index + '].matches[' + match.index + '].name'
                                }
                                label={'Match name'}
                            />
                            <FormInputLabel label={'Teams'}>
                                <TextField
                                    onChange={v => {
                                        teamsField.onChange(v)
                                        onTeamsChanged(Number(v.target.value))
                                    }}
                                    value={teamsField.value}
                                />
                            </FormInputLabel>
                            <FormInputNumber
                                name={
                                    'rounds[' +
                                    round.index +
                                    '].matches[' +
                                    match.index +
                                    '].weighting'
                                }
                                label={'Match weighting'}
                                required
                                integer={true}
                            />
                            <SwitchElement
                                name={
                                    'rounds[' +
                                    round.index +
                                    '].matches[' +
                                    match.index +
                                    '].duplicatable'
                                }
                                label={
                                    <FormInputLabel
                                        label={'Duplicatable'}
                                        required={true}
                                        horizontal
                                    />
                                }
                            />
                        </Stack>
                        <Stack spacing={2} sx={{border: 1, borderColor: 'lightgrey', p: 1}}>
                            {controlledFields.map((outcome, outcomeIndex) => (
                                <FormInputNumber
                                    key={`outcome-${round.id}-${match.id}-${outcomeIndex}-${outcome}`}
                                    name={
                                        'rounds[' +
                                        round.index +
                                        '].matches[' +
                                        match.index +
                                        '].outcomes[' +
                                        outcomeIndex +
                                        '].outcome'
                                    }
                                    label={'Outcome weighting'}
                                    required
                                />
                            ))}
                        </Stack>
                    </>
                )}
            />
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

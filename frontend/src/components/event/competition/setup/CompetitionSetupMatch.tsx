import {CheckboxElement, useFieldArray} from 'react-hook-form-mui'
import {Divider, Stack, Typography} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {
    CompetitionSetupMatchOrGroupProps,
    onTeamsChanged,
} from '@components/event/competition/setup/common.ts'
import CompetitionSetupParticipants from '@components/event/competition/setup/CompetitionSetupParticipants.tsx'

type Props = CompetitionSetupMatchOrGroupProps
const CompetitionSetupMatch = ({formContext, roundIndex, fieldInfo, ...props}: Props) => {
    const participantsFormPath =
        `rounds[${roundIndex}].matches[${fieldInfo.index}].participants` as `rounds.${number}.matches.${number}.participants`

    const {fields: participantFields} = useFieldArray({
        control: formContext.control,
        name: participantsFormPath,
    })

    const watchParticipants = formContext.watch(participantsFormPath)

    const controlledParticipantFields = participantFields.map((field, index) => ({
        ...field,
        ...watchParticipants?.[index],
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
            {watchParticipants.length > 0 && (
                <>
                    <Typography variant="subtitle1">Participants</Typography>
                    <CompetitionSetupParticipants
                        fieldInfo={fieldInfo}
                        roundIndex={roundIndex}
                        controlledParticipantFields={controlledParticipantFields}
                        useDefaultSeeding={props.useDefaultSeeding}
                    />
                </>
            )}
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

            <Typography sx={{textAlign: 'center'}}>
                Outcomes: {props.outcomes.join(', ')}
            </Typography>
        </Stack>
    )
}

export default CompetitionSetupMatch

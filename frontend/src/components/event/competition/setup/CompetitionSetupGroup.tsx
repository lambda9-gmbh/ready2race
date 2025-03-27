import {Divider, Stack, Typography} from '@mui/material'
import {
    CompetitionSetupMatchOrGroupProps,
    onTeamsChanged,
} from '@components/event/competition/setup/common.ts'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {useFieldArray} from 'react-hook-form-mui'
import CompetitionSetupOutcomes from '@components/event/competition/setup/CompetitionSetupOutcomes.tsx'

type Props = CompetitionSetupMatchOrGroupProps & {}
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

    // Todo: Group-Matches - Otherwise the Groups won't load from the Backend since the groups are only attached to the matches

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

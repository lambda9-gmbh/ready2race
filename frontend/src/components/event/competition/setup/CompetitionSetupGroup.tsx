import {Divider, Stack, Typography} from '@mui/material'
import {
    CompetitionSetupMatchOrGroupProps,
    onTeamsChanged,
} from '@components/event/competition/setup/common.ts'
import {FormInputText} from '@components/form/input/FormInputText.tsx'

type Props = CompetitionSetupMatchOrGroupProps & {}
const CompetitionSetupGroup = ({formContext, roundIndex, fieldInfo, ...props}: Props) => {
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
        </Stack>
    )
}

export default CompetitionSetupGroup

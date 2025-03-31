import {Stack, Typography} from '@mui/material'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {getMatchupsString} from '@components/event/competition/setup/common.ts'

type Props = {
    fieldInfo: {index: number; id: string}
    roundIndex: number
    useDefaultSeeding: boolean
    controlledParticipantFields: {seed: number; id: string}[]
}
const CompetitionSetupParticipants = ({
    fieldInfo,
    roundIndex,
    useDefaultSeeding,
    controlledParticipantFields,
}: Props) => {
    return (
        <>
            {useDefaultSeeding ? (
                <Typography>
                    {getMatchupsString(controlledParticipantFields.map(v => v.seed))}
                </Typography>
            ) : (
                <Stack spacing={2} sx={{border: 1, borderColor: 'lightgrey', p: 1}}>
                    {controlledParticipantFields.map((participant, index) => (
                        <Stack
                            key={participant.id}
                            direction="row"
                            spacing={1}
                            sx={{alignItems: 'center'}}>
                            <Typography>#</Typography>
                            <FormInputNumber
                                key={`${fieldInfo.id}-${index}-${participant.id}`}
                                name={`rounds[${roundIndex}].matches[${fieldInfo.index}].participants[${index}].seed`}
                                required
                                transform={{
                                    output: value => Number(value.target.value),
                                }}
                            />
                        </Stack>
                    ))}
                </Stack>
            )}
        </>
    )
}
export default CompetitionSetupParticipants

import {Grid2, Stack, Typography} from '@mui/material'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'

type Props = {
    fieldInfo: {index: number; id: string}
    roundIndex: number
    useDefaultSeeding: boolean
    controlledOutcomeFields: {outcome: number; id: string}[]
}
const CompetitionSetupOutcomes = ({
    fieldInfo,
    roundIndex,
    useDefaultSeeding,
    controlledOutcomeFields,
}: Props) => {
    return (
        <>
            {useDefaultSeeding ? (
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
        </>
    )
}
export default CompetitionSetupOutcomes
import {Stack, Typography} from '@mui/material'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {getMatchupsString} from '@components/event/competition/setup/common.ts'

type Props = {
    fieldInfo: {index: number; id: string}
    roundIndex: number
    useDefaultSeeding: boolean
    controlledParticipantFields: {seed: number; id: string}[]
    updatePlaces: (roundIndex: number, updateThisRound: boolean, newTeamsCount?: number) => void
}
const CompetitionSetupParticipants = ({
    fieldInfo,
    roundIndex,
    controlledParticipantFields,
    ...props
}: Props) => {
    return (
        <>
            {props.useDefaultSeeding ? (
                <Typography>
                    {getMatchupsString(controlledParticipantFields.map(v => v.seed))}
                </Typography>
            ) : (
                <Stack spacing={2}>
                    {controlledParticipantFields.map((participant, index) => (
                        <Stack
                            key={participant.id}
                            direction="row"
                            spacing={1}
                            sx={{alignItems: 'center'}}>
                            <Typography>#</Typography>
                            <FormInputNumber
                                name={`rounds.${roundIndex}.matches.${fieldInfo.index}.participants.${index}.seed`}
                                required
                                onChange={() => props.updatePlaces(roundIndex, false)}
                                transform={{
                                    output: value => Number(value.target.value),
                                }}
                                integer
                                min={1}
                            />
                        </Stack>
                    ))}
                </Stack>
            )}
        </>
    )
}
export default CompetitionSetupParticipants

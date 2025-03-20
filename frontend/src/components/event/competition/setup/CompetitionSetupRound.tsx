import {useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import {Box, Button, Stack, Typography} from '@mui/material'
import {CompetitionSetupForm} from '@components/event/competition/setup/CompetitionSetup.tsx'
import CompetitionSetupMatch from '@components/event/competition/setup/CompetitionSetupMatch.tsx'

type Props = {
    roundIndex: number
    roundId: string
    formContext: UseFormReturn<CompetitionSetupForm>
    removeRound: (index: number) => void
    teamCountFollowingRound: number
}
const CompetitionSetupRound = ({roundIndex, roundId, formContext, removeRound, teamCountFollowingRound}: Props) => {
    const {
        fields: matchFields,
        append: appendMatch,
        remove: removeMatch,
    } = useFieldArray({
        control: formContext.control,
        name: ('rounds[' + roundIndex + '].matches') as `rounds.${number}.matches`,
        keyName: 'fieldId',
    })


    return (
        <>
            <Box sx={{display: 'flex', justifyContent: 'center'}}>
                <Typography>Max Teams: {teamCountFollowingRound}</Typography>
            </Box>
            <Stack
                direction="row"
                spacing={2}
                justifyContent="space-between"
                key={`round-${roundId}`}
                sx={{alignItems: 'center'}}>
                <Stack
                    direction="row"
                    spacing={2}
                    alignItems={'center'}
                    sx={{border: 1, borderColor: 'blue', p: 2}}>
                    {matchFields.map((matchField, matchIndex) => (
                        <CompetitionSetupMatch
                            formContext={formContext}
                            round={{index: roundIndex, id: roundId}}
                            match={{index: matchIndex, id: matchField.fieldId}}
                            removeMatch={() => removeMatch(matchIndex)}
                        />
                    ))}
                    <Box>
                        <Button
                            variant="outlined"
                            onClick={() => {
                                appendMatch({
                                    duplicatable: false,
                                    weighting: matchFields.length + 1,
                                    teams: 2,
                                    name: '',
                                    outcomes: [{outcome: 1}, {outcome: matchFields.length + 1}],
                                })
                            }}
                            sx={{width: 1}}>
                            Add Match
                        </Button>
                    </Box>
                </Stack>
                <Box>
                    <Button variant="outlined" onClick={() => removeRound(roundIndex)}>
                        Remove Round
                    </Button>
                </Box>
            </Stack>
        </>
    )
}

export default CompetitionSetupRound

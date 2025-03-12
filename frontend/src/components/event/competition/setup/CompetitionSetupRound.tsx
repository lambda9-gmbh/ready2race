import {Control, useFieldArray} from 'react-hook-form-mui'
import {Setup} from '@components/event/competition/setup/CompetitionSetup.tsx'
import {Box, Button, Stack} from '@mui/material'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'

type Props = {
    roundIndex: number
    roundId: string
    control: Control<Setup>
    removeRound: (index: number) => void
}
const CompetitionSetupRound = ({roundIndex, roundId, control, removeRound}: Props) => {
    const {
        fields: matchFields,
        append: appendMatch,
        remove: removeMatch,
    } = useFieldArray({
        control: control,
        name: ('rounds[' + roundIndex + '].matches') as `rounds.${number}.matches`,
        keyName: 'fieldId',
    })

    return (
        <Stack
            direction="row"
            spacing={2}
            justifyContent="space-between"
            key={`round-${roundId}`}
            sx={{border: 1, borderColor: 'blue', p: 2, alignItems: 'center'}}>
            <Stack direction="row" spacing={2} alignItems={'center'}>
                {matchFields.map((matchField, matchIndex) => (
                    <Stack
                        direction="column"
                        spacing={1}
                        key={`match-${roundId}-${matchField.fieldId}`}>
                        <Stack
                            sx={{
                                border: 1,
                                borderColor: 'grey',
                                width: 1,
                                p: 2,
                                boxSizing: 'border-box',
                            }}>
                            <FormInputNumber
                                name={'rounds[' + roundIndex + '].matches[' + matchIndex + '].name'}
                                label={'Match name'}
                            />
                            <FormInputNumber
                                name={`rounds.${roundIndex}.matches.${matchIndex}.teams`}
                                label={'Teams'}
                            />
                            <FormInputNumber
                                name={
                                    'rounds[' +
                                    roundIndex +
                                    '].matches[' +
                                    matchIndex +
                                    '].weighting'
                                }
                                label={'Match weighting'}
                                required
                            />
                        </Stack>
                        <Stack spacing={2} sx={{border: 1, borderColor: 'lightgrey', p: 1}}>
                            {matchField.outcomes.map((outcome, outcomeIndex) => (
                                <FormInputNumber
                                    key={`outcome-${roundId}-${matchField.fieldId}-${outcome}`}
                                    name={
                                        'rounds[' +
                                        roundIndex +
                                        '].matches[' +
                                        matchIndex +
                                        '].outcomes[' +
                                        outcomeIndex +
                                        ']'
                                    }
                                    label={'Outcome weighting'}
                                    required
                                />
                            ))}
                        </Stack>
                        <Button
                            variant="outlined"
                            onClick={() => {
                                removeMatch(matchIndex)
                            }}>
                            Remove Match
                        </Button>
                    </Stack>
                ))}
                <Box>
                    <Button
                        variant="outlined"
                        onClick={() => {
                            appendMatch({
                                weighting: matchFields.length + 1,
                                teams: 2,
                                name: '',
                                outcomes: [1, matchFields.length + 1],
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
    )
}

export default CompetitionSetupRound

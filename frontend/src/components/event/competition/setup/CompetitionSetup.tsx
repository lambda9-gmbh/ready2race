import {Box, Button, Stack} from '@mui/material'
import {FormContainer, useFieldArray, useForm} from 'react-hook-form-mui'
import CompetitionSetupRound from '@components/event/competition/setup/CompetitionSetupRound.tsx'

const CompetitionSetup = () => {
    const formContext = useForm<Setup>()

    const {
        fields: roundFields,
        append: appendRound,
        remove: removeRound,
    } = useFieldArray({
        control: formContext.control,
        name: 'rounds',
        keyName: 'fieldId',
    })

    return (
        <FormContainer formContext={formContext}>
            <Box>
                <Stack direction="column" spacing={4} alignItems="center">
                    {roundFields.map((roundField, roundIndex) => (
                        <CompetitionSetupRound
                            roundIndex={roundIndex}
                            roundId={roundField.fieldId}
                            control={formContext.control}
                            removeRound={removeRound}
                        />
                    ))}
                    <Box>
                        <Button
                            variant='outlined'
                            onClick={() => {
                                appendRound({
                                    name: '',
                                    required: false,
                                    matches: [],
                                })
                            }}
                            sx={{width: 1}}>
                            Add Round
                        </Button>
                    </Box>
                </Stack>
                <Button variant="outlined" onClick={() => formContext.reset({rounds: []})}>
                    Click to reset
                </Button>
                <Button variant="outlined" onClick={() => formContext.reset(dummyData)}>
                    Click for dummy data
                </Button>
            </Box>
        </FormContainer>
    )
}

export default CompetitionSetup

export type Setup = {
    rounds: SetupRound[]
}

type SetupRound = {
    name: string
    required: boolean
    matches: SetupMatch[]
}

type SetupMatch = {
    weighting: number
    teams?: number
    name?: string
    outcomes: number[]
}

const dummyData: Setup = {
    rounds: [
        {
            name: 'Vorrunde',
            required: true,
            matches: [
                {
                    weighting: 1,
                    teams: undefined,
                    name: undefined,
                    outcomes: [1, 2, 3, 4],
                },
            ],
        },
        {
            name: 'Viertelfinale',
            required: false,
            matches: [
                {
                    weighting: 1,
                    teams: 2,
                    name: 'VF1',
                    outcomes: [1, 8],
                },
                {
                    weighting: 4,
                    teams: 2,
                    name: 'VF2',
                    outcomes: [4, 5],
                },
                {
                    weighting: 3,
                    teams: 2,
                    name: 'VF3',
                    outcomes: [3, 6],
                },
                {
                    weighting: 2,
                    teams: 2,
                    name: 'VF4',
                    outcomes: [2, 7],
                },
            ],
        },
        {
            name: 'Halbfinale',
            required: false,
            matches: [
                {
                    weighting: 1,
                    teams: 2,
                    name: 'HF1',
                    outcomes: [1, 4],
                },
                {
                    weighting: 2,
                    teams: 2,
                    name: 'HF2',
                    outcomes: [2, 3],
                },
            ],
        },
        {
            name: 'Finale',
            required: true,
            matches: [
                {
                    weighting: 1,
                    teams: 2,
                    name: 'F',
                    outcomes: [1, 2],
                },
            ],
        },
    ],
}
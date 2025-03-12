import {Box, Button, Stack} from '@mui/material'
import {FormContainer, useFieldArray, useForm} from 'react-hook-form-mui'
import CompetitionSetupRound from '@components/event/competition/setup/CompetitionSetupRound.tsx'
import {CompetitionSetupDto} from '@api/types.gen.ts'
import {useState} from 'react'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {competitionRoute, eventRoute} from '@routes'
import {getCompetitionSetup, updateCompetitionSetup} from '@api/sdk.gen.ts'
import {SubmitButton} from '@components/form/SubmitButton.tsx'

export type CompetitionSetupForm = CompetitionSetupDto

const CompetitionSetup = () => {
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const [reloadDataTrigger, setReloadDataTrigger] = useState(false)

    const formContext = useForm<CompetitionSetupForm>()

    const {
        fields: roundFields,
        append: appendRound,
        remove: removeRound,
    } = useFieldArray({
        control: formContext.control,
        name: 'rounds',
        keyName: 'fieldId',
    })

    useFetch(
        signal =>
            getCompetitionSetup({signal, path: {eventId: eventId, competitionId: competitionId}}),
        {
            onResponse: ({data}) => {
                if (data) {
                    formContext.reset(data)
                } else {
                    feedback.error('[todo] error!')
                }
            },
            deps: [eventId, competitionId, reloadDataTrigger],
        },
    )

    const handleSubmit = async (formData: CompetitionSetupForm) => {
        setSubmitting(true)
        const {error} = await updateCompetitionSetup({
            path: {eventId: eventId, competitionId: competitionId},
            body: formData,
        })
        setSubmitting(false)

        if (error) {
            feedback.error('[todo] Error!')
        } else {
            feedback.success('[todo] Saved!')
        }
        setReloadDataTrigger(!reloadDataTrigger)
    }

    return (
        <FormContainer formContext={formContext} onSuccess={handleSubmit}>
            <Box>
                <Stack direction="row" spacing={2} sx={{justifyContent: 'end', mb:4}}>
                    <Button variant="outlined" onClick={() => formContext.reset({rounds: []})}>
                        Click to reset
                    </Button>
                    <Button variant="outlined" onClick={() => formContext.reset(dummyData)}>
                        Click for dummy data
                    </Button>
                    <SubmitButton label={'[todo] Save'} submitting={submitting} />
                </Stack>
                <Stack spacing={4} alignItems="center">
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
                            variant="outlined"
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
            </Box>
        </FormContainer>
    )
}

export default CompetitionSetup

const dummyData: CompetitionSetupDto = {
    rounds: [
        {
            name: 'Vorrunde',
            required: true,
            matches: [
                {
                    weighting: 1,
                    teams: undefined,
                    name: undefined,
                    outcomes: [1, 2, 3, 4, 5, 6, 7, 8],
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

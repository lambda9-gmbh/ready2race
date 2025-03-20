import {Box, Button, Stack} from '@mui/material'
import {FormContainer, useFieldArray, useForm} from 'react-hook-form-mui'
import CompetitionSetupRound from '@components/event/competition/setup/CompetitionSetupRound.tsx'
import {
    CompetitionSetupDto,
    CompetitionSetupGroupStatisticEvaluationDto,
    CompetitionSetupMatchDto,
} from '@api/types.gen.ts'
import {useState} from 'react'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {competitionRoute, eventRoute} from '@routes'
import {getCompetitionSetup, updateCompetitionSetup} from '@api/sdk.gen.ts'
import {SubmitButton} from '@components/form/SubmitButton.tsx'

export type CompetitionSetupForm = {
    rounds: Array<{
        name: string
        required: boolean
        matches?: Array<FormSetupMatch>
        groups?: Array<{
            duplicatable: boolean
            weighting: number
            teams?: number
            name?: string
            matches: Array<FormSetupMatch>
            outcomes: Array<{outcome: number}>
        }>
        statisticEvaluations?: Array<CompetitionSetupGroupStatisticEvaluationDto>
    }>
}
type FormSetupMatch = {
    duplicatable: boolean
    weighting: number
    teams?: number
    name?: string
    outcomes?: Array<{outcome: number}>
}

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
                    formContext.reset(mapDtoToForm(data))
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
            body: mapFormToDto(formData),
        })
        setSubmitting(false)

        if (error) {
            feedback.error('[todo] Error!')
        } else {
            feedback.success('[todo] Saved!')
            setReloadDataTrigger(!reloadDataTrigger)
        }
    }

    const formWatch = formContext.watch('rounds')

    function getTeamCountForRound(roundIndex: number) {
        if (
            formWatch[roundIndex].matches !== undefined &&
            formWatch[roundIndex].matches.length > 0
        ) {
            return (
                formWatch[roundIndex].matches
                    .map(v => v.teams)
                    .reduce((acc, val) => {
                        if (val === undefined) {
                            return acc
                        } else if (acc === undefined) {
                            return val
                        } else {
                            return +acc + +val
                        }
                    }) ?? 0
            )
        } else {
            return 0
        }
    }

    return (
        <FormContainer formContext={formContext} onSuccess={handleSubmit}>
            <Box>
                <Stack direction="row" spacing={2} sx={{justifyContent: 'end', mb: 4}}>
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
                            formContext={formContext}
                            removeRound={removeRound}
                            teamCountFollowingRound={getTeamCountForRound(roundIndex)}
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

const dummyData: CompetitionSetupForm = {
    rounds: [
        {
            name: 'Vorrunde',
            required: true,
            matches: [
                {
                    duplicatable: false,
                    weighting: 1,
                    teams: undefined,
                    name: undefined,
                    outcomes: [
                        {outcome: 1},
                        {outcome: 2},
                        {outcome: 3},
                        {outcome: 4},
                        {outcome: 5},
                        {outcome: 6},
                        {outcome: 7},
                        {outcome: 8},
                    ],
                },
            ],
        },
        {
            name: 'Viertelfinale',
            required: false,
            matches: [
                {
                    duplicatable: false,
                    weighting: 1,
                    teams: 2,
                    name: 'VF1',
                    outcomes: [{outcome: 1}, {outcome: 8}],
                },
                {
                    duplicatable: false,
                    weighting: 4,
                    teams: 2,
                    name: 'VF2',
                    outcomes: [{outcome: 4}, {outcome: 5}],
                },
                {
                    duplicatable: false,
                    weighting: 3,
                    teams: 2,
                    name: 'VF3',
                    outcomes: [{outcome: 3}, {outcome: 6}],
                },
                {
                    duplicatable: false,
                    weighting: 2,
                    teams: 2,
                    name: 'VF4',
                    outcomes: [{outcome: 2}, {outcome: 7}],
                },
            ],
        },
        {
            name: 'Halbfinale',
            required: false,
            matches: [
                {
                    duplicatable: false,
                    weighting: 1,
                    teams: 2,
                    name: 'HF1',
                    outcomes: [{outcome: 1}, {outcome: 4}],
                },
                {
                    duplicatable: false,
                    weighting: 2,
                    teams: 2,
                    name: 'HF2',
                    outcomes: [{outcome: 2}, {outcome: 3}],
                },
            ],
        },
        {
            name: 'Finale',
            required: true,
            matches: [
                {
                    duplicatable: false,
                    weighting: 1,
                    teams: 2,
                    name: 'F',
                    outcomes: [{outcome: 1}, {outcome: 2}],
                },
            ],
        },
    ],
}

function mapFormToDto(form: CompetitionSetupForm): CompetitionSetupDto {
    return {
        rounds: form.rounds.map(round => ({
            name: round.name,
            required: round.required,
            matches: round.matches?.map(match => mapFormMatchToDtoMatch(match)),
            groups: round.groups?.map(group => ({
                duplicatable: group.duplicatable,
                weighting: group.weighting,
                teams: group.teams,
                name: group.name,
                matches: group.matches.map(match => mapFormMatchToDtoMatch(match)),
                outcomes: group.outcomes.map(outcome => outcome.outcome),
            })),
            statisticEvaluations: round.statisticEvaluations,
        })),
    }
}

function mapFormMatchToDtoMatch(formMatch: FormSetupMatch): CompetitionSetupMatchDto {
    return {
        duplicatable: formMatch.duplicatable,
        weighting: formMatch.weighting,
        teams: formMatch.teams,
        name: formMatch.name,
        outcomes: formMatch.outcomes?.map(outcome => outcome.outcome),
    }
}

function mapDtoToForm(dto: CompetitionSetupDto): CompetitionSetupForm {
    return {
        rounds: dto.rounds.map(round => ({
            name: round.name,
            required: round.required,
            matches: round.matches?.map(match => mapDtoMatchToFormMatch(match)),
            groups: round.groups?.map(group => ({
                duplicatable: group.duplicatable,
                weighting: group.weighting,
                teams: group.teams,
                name: group.name,
                matches: group.matches.map(match => mapDtoMatchToFormMatch(match)),
                outcomes: group.outcomes.map(outcome => ({outcome: outcome})),
            })),
            statisticEvaluations: round.statisticEvaluations,
        })),
    }
}

function mapDtoMatchToFormMatch(matchDto: CompetitionSetupMatchDto): FormSetupMatch {
    return {
        duplicatable: matchDto.duplicatable,
        weighting: matchDto.weighting,
        teams: matchDto.teams,
        name: matchDto.name,
        outcomes: matchDto.outcomes?.map(outcome => ({outcome: outcome})),
    }
}

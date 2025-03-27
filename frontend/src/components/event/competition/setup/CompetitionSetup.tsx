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
import {competitionSetupDummyData} from '@components/event/competition/setup/common.ts'

export type CompetitionSetupForm = {
    rounds: Array<{
        name: string
        required: boolean
        matches: Array<FormSetupMatch>
        groups: Array<{
            duplicatable: boolean
            weighting: number
            teams: string
            name?: string
            matches: Array<FormSetupMatch>
            outcomes: Array<{outcome: number}>
        }>
        statisticEvaluations?: Array<CompetitionSetupGroupStatisticEvaluationDto>
        useDefaultSeeding: boolean
        isGroupRound: boolean
    }>
}
type FormSetupMatch = {
    duplicatable: boolean
    weighting: number
    teams: string // String to have to option of leaving the field empty ('') without actually having the value be undefined
    name?: string
    outcomes?: Array<{outcome: number}>
    position: number // Will be translated to the array order in the dto
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
        insert: insertRound,
        remove: removeRound,
    } = useFieldArray({
        control: formContext.control,
        name: 'rounds',
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

    // Returns the Team Count for the specified round (not always THIS round)
    // IgnoredIndex can be provided to keep one match or group out of the calculation
    // Returns 0 when the Team Count is unknown (Undefined team field(s)) or an error occurred
    function getTeamCountForRound(
        roundIndex: number,
        isGroupRound: boolean,
        ignoredIndex?: number,
    ) {
        if (formWatch[roundIndex] === undefined || roundIndex > formWatch.length - 1) {
            return 0
        }
        const arrayLength = isGroupRound
            ? formWatch[roundIndex].groups.length
            : formWatch[roundIndex].matches.length

        if (arrayLength < 1) {
            return 0
        }

        // If ignoredIndex is provided, the team value of that match/group will not be added
        const teams = isGroupRound
            ? (ignoredIndex === undefined
                  ? formWatch[roundIndex].groups
                  : formWatch[roundIndex].groups.filter((_, index) => index !== ignoredIndex)
              ).map(g => g.teams)
            : (ignoredIndex === undefined
                  ? formWatch[roundIndex].matches
                  : formWatch[roundIndex].matches.filter((_, index) => index !== ignoredIndex)
              ).map(m => m.teams)

        if (teams.length < 1) {
            return 0
        }

        if (teams.find(v => v === '') !== undefined) {
            return 0
        }

        return (
            teams
                .map(v => Number(v))
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
    }

    const AddRoundButton = ({addIndex}: {addIndex: number}) => {
        return (
            <Box sx={{maxWidth: 200}}>
                <Button
                    variant="outlined"
                    onClick={() => {
                        insertRound(addIndex, {
                            name: '',
                            required: false,
                            matches: [],
                            groups: [],
                            useDefaultSeeding: true,
                            isGroupRound: false,
                        })
                    }}
                    sx={{width: 1}}>
                    Add Round
                </Button>
            </Box>
        )
    }

    return (
        <FormContainer formContext={formContext} onSuccess={handleSubmit}>
            <Box>
                <Stack direction="row" spacing={2} sx={{justifyContent: 'end', mb: 4}}>
                    <Button variant="outlined" onClick={() => formContext.reset({rounds: []})}>
                        Click to reset
                    </Button>
                    <Button
                        variant="outlined"
                        onClick={() => formContext.reset(competitionSetupDummyData)}>
                        Click for dummy data
                    </Button>
                    <SubmitButton label={'[todo] Save'} submitting={submitting} />
                </Stack>
                <Stack spacing={4} alignItems="center">
                    <AddRoundButton addIndex={0} />
                    {roundFields.map((roundField, roundIndex) => (
                        <Stack spacing={2} key={roundField.id} sx={{alignItems: 'center'}}>
                            <CompetitionSetupRound
                                round={{index: roundIndex, id: roundField.id}}
                                formContext={formContext}
                                removeRound={removeRound}
                                teamCounts={{
                                    thisRound: getTeamCountForRound(
                                        roundIndex,
                                        formWatch[roundIndex]?.isGroupRound ?? false,
                                    ),
                                    nextRound: getTeamCountForRound(
                                        roundIndex + 1,
                                        formWatch[roundIndex + 1]?.isGroupRound ?? false,
                                    ),
                                }}
                                getRoundTeamCountWithoutThis={(ignoredIndex, isGroupRound) =>
                                    getTeamCountForRound(roundIndex, isGroupRound, ignoredIndex)
                                }
                            />
                            <AddRoundButton addIndex={roundIndex + 1} />
                        </Stack>
                    ))}
                </Stack>
            </Box>
        </FormContainer>
    )
}

export default CompetitionSetup

function mapFormToDto(form: CompetitionSetupForm): CompetitionSetupDto {
    return {
        rounds: form.rounds.map(round => ({
            name: round.name,
            required: round.required,
            matches: !round.isGroupRound
                ? round.matches?.sort(v => v.position).map(match => mapFormMatchToDtoMatch(match))
                : undefined,
            groups: round.isGroupRound
                ? round.groups?.map(group => ({
                      duplicatable: group.duplicatable,
                      weighting: group.weighting,
                      teams: group.teams !== '' ? Number(group.teams) : undefined,
                      name: group.name,
                      matches: group.matches.map(match => mapFormMatchToDtoMatch(match)),
                      outcomes: group.outcomes.map(outcome => outcome.outcome),
                  }))
                : undefined,
            statisticEvaluations: round.statisticEvaluations,
            useDefaultSeeding: round.useDefaultSeeding,
        })),
    }
}

function mapFormMatchToDtoMatch(formMatch: FormSetupMatch): CompetitionSetupMatchDto {
    return {
        duplicatable: formMatch.duplicatable,
        weighting: formMatch.weighting,
        teams: formMatch.teams !== '' ? Number(formMatch.teams) : undefined,
        name: formMatch.name,
        outcomes: formMatch.outcomes?.map(outcome => outcome.outcome),
    }
}

function mapDtoToForm(dto: CompetitionSetupDto): CompetitionSetupForm {
    return {
        rounds: dto.rounds.map(round => ({
            name: round.name,
            required: round.required,
            matches:
                round.matches?.map((match, index) => mapDtoMatchToFormMatch(match, index)) ?? [],
            groups:
                round.groups?.map(group => ({
                    duplicatable: group.duplicatable,
                    weighting: group.weighting,
                    teams: group.teams?.toString() ?? '',
                    name: group.name,
                    matches: group.matches.map((match, index) =>
                        mapDtoMatchToFormMatch(match, index),
                    ),
                    outcomes: group.outcomes.map(outcome => ({outcome: outcome})),
                })) ?? [],
            statisticEvaluations: round.statisticEvaluations,
            useDefaultSeeding: round.useDefaultSeeding,
            isGroupRound: round.groups !== undefined,
        })),
    }
}

function mapDtoMatchToFormMatch(matchDto: CompetitionSetupMatchDto, order: number): FormSetupMatch {
    return {
        duplicatable: matchDto.duplicatable,
        weighting: matchDto.weighting,
        teams: matchDto.teams?.toString() ?? '',
        name: matchDto.name,
        outcomes: matchDto.outcomes?.map(outcome => ({outcome: outcome})),
        position: order,
    }
}

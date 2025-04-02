import {Box, Button, Stack} from '@mui/material'
import {FormContainer, useFieldArray, useForm} from 'react-hook-form-mui'
import CompetitionSetupRound from '@components/event/competition/setup/CompetitionSetupRound.tsx'
import {
    CompetitionSetupDto,
    CompetitionSetupGroupStatisticEvaluationDto,
    CompetitionSetupMatchDto,
} from '@api/types.gen.ts'
import {useRef, useState} from 'react'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {competitionRoute, eventRoute} from '@routes'
import {getCompetitionSetup, updateCompetitionSetup} from '@api/sdk.gen.ts'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import CompetitionSetupTreeHelper from '@components/event/competition/setup/CompetitionSetupTreeHelper.tsx'

export type CompetitionSetupForm = {
    rounds: Array<FormSetupRound>
}
export type FormSetupRound = {
    name: string
    required: boolean
    matches: Array<FormSetupMatch>
    groups: Array<{
        duplicatable: boolean
        weighting: number
        teams: string // Todo: Change this back to number (string is no longer necessary)
        name?: string
        matches: Array<FormSetupMatch>
        participants: Array<{seed: number}> // in round 1 the list will be empty
        matchTeams: number
    }>
    statisticEvaluations?: Array<CompetitionSetupGroupStatisticEvaluationDto>
    useDefaultSeeding: boolean
    isGroupRound: boolean
}
type FormSetupMatch = {
    duplicatable: boolean
    weighting: number | null // in round: number; in group: null
    teams: string // Todo: Change this back to number (string is no longer necessary) and make it nullable because of matchTeams is groups
    name?: string
    participants: Array<{seed: number}> // in round 1 the list will be empty
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

    // This allows the Tournament Tree Generator Form to exist outside the CompetitionSetup Form while being rendered inside
    const treeHelperPortalContainer = useRef<HTMLDivElement>(null)

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
        <>
            <div ref={treeHelperPortalContainer} />
            <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                <Box>
                    <Stack direction="row" spacing={2} sx={{justifyContent: 'end', mb: 4}}>
                        <Button variant="outlined" onClick={() => formContext.reset({rounds: []})}>
                            Click to reset
                        </Button>
                        <CompetitionSetupTreeHelper
                            resetSetupForm={(formData: CompetitionSetupForm) => {
                                formContext.reset(formData)
                            }}
                            currentFormData={formWatch}
                            portalContainer={treeHelperPortalContainer}
                        />
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
        </>
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
                      matches: group.matches.map(match =>
                          mapFormMatchToDtoMatch(match, group.matchTeams),
                      ),
                      participants: group.participants.map(participant => participant.seed),
                  }))
                : undefined,
            statisticEvaluations: round.statisticEvaluations,
            useDefaultSeeding: round.useDefaultSeeding,
        })),
    }
}

function mapFormMatchToDtoMatch(
    formMatch: FormSetupMatch,
    setTeamsValue?: number,
): CompetitionSetupMatchDto {
    return {
        duplicatable: formMatch.duplicatable,
        weighting: formMatch.weighting ?? undefined,
        teams:
            setTeamsValue === undefined // Groups provide a set value for the teams since all matches in one group need to have the same amount of participants
                ? formMatch.teams !== ''
                    ? Number(formMatch.teams)
                    : undefined
                : setTeamsValue,
        name: formMatch.name,
        participants: formMatch.participants.map(p => p.seed),
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
                    participants: group.participants.map(participant => ({seed: participant})),
                    matchTeams: group.matches[0]?.teams ?? 0,
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
        weighting: matchDto.weighting ?? null,
        teams: matchDto.teams?.toString() ?? '',
        name: matchDto.name,
        participants: matchDto.participants?.map(participant => ({seed: participant})),
        position: order,
    }
}

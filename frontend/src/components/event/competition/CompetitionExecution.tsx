import {
    createNextCompetitionRound,
    getCompetitionExecutionProgress,
    updateMatchResults,
} from '@api/sdk.gen.ts'
import {
    Box,
    Button,
    Card,
    Dialog,
    DialogActions,
    DialogContent,
    Divider,
    Stack,
    Typography,
    useTheme,
} from '@mui/material'
import {competitionRoute, eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import {useState} from 'react'
import LoadingButton from '@components/form/LoadingButton.tsx'
import {FormContainer, useFieldArray, useForm} from 'react-hook-form-mui'
import Throbber from '@components/Throbber.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {groupBy} from '@utils/helpers.ts'

type EditMatchDataTeam = {
    registrationId: string
    startNumber: string
}
type EditMatchDataForm = {
    startTime: string
    teams: EditMatchDataTeam[]
}

type EnterResultsTeam = {
    registrationId: string
    place: string
}
type EnterResultsForm = {
    selectedMatchIndex: number | null
    teamResults: EnterResultsTeam[]
}

const CompetitionExecution = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const theme = useTheme()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const [submitting, setSubmitting] = useState(false)

    const [reloadData, setReloadData] = useState(false)

    const {data: progressDto, pending: progressDtoPending} = useFetch(
        signal =>
            getCompetitionExecutionProgress({
                signal,
                path: {
                    eventId: eventId,
                    competitionId: competitionId,
                },
            }),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error('[todo] Error when fetching progress')
                }
            },
            deps: [eventId, competitionId, reloadData],
        },
    )

    const handleCreateNextRound = async () => {
        setSubmitting(true)
        const {error} = await createNextCompetitionRound({
            path: {
                eventId: eventId,
                competitionId: competitionId,
            },
        })
        setSubmitting(false)
        if (error) {
            feedback.error(`[todo] Error: ${error.errorCode}`)
        } else {
            feedback.success('[todo] New round created')
        }
        setReloadData(!reloadData)
    }

    const currentRound = progressDto?.rounds[progressDto?.rounds.length - 1]

    const editDataFormContext = useForm<EditMatchDataForm>()
    const resultsFormContext = useForm<EnterResultsForm>({
        values: {
            selectedMatchIndex: null,
            teamResults: [],
        },
    })

    const selectedMatchResultIndex = resultsFormContext.watch('selectedMatchIndex')
    const selectedMatchResultMatch = currentRound?.matches[selectedMatchResultIndex ?? -1]

    const [teamResultsError, setTeamResultsError] = useState<string | null>(null)

    const {fields: resultFields} = useFieldArray({
        control: resultsFormContext.control,
        name: 'teamResults',
        rules: {
            validate: values => {
                const duplicatePlaces = Array.from(groupBy(values, val => val.place))
                    .filter(([, items]) => items.length > 1)
                    .map(([place]) => place)

                if (duplicatePlaces.length > 0) {
                    setTeamResultsError(
                        `[todo] Places: ${duplicatePlaces.join(', ')} are used several times. Every place has to be unique.`,
                    )
                    return 'duplicates'
                }

                setTeamResultsError(null)
                return undefined
            },
        },
    })

    const [dialogOpen, setDialogOpen] = useState(false)
    const openDialog = (matchIndex: number) => {
        if (currentRound) {
            setDialogOpen(true)
            resultsFormContext.reset({
                selectedMatchIndex: matchIndex,
                teamResults: currentRound.matches[matchIndex].teams.map(team => ({
                    registrationId: team.registrationId,
                    place: team.place?.toString() ?? '',
                })),
            })
        }
    }

    const closeDialog = () => {
        setDialogOpen(false)
    }

    const onSubmitResults = async (formData: EnterResultsForm) => {
        if (formData.selectedMatchIndex === null || currentRound === undefined) {
            feedback.error(t('common.error.unexpected'))
        } else {
            setSubmitting(true)
            const {error} = await updateMatchResults({
                path: {
                    eventId: eventId,
                    competitionId: competitionId,
                    competitionMatchId: currentRound?.matches[formData.selectedMatchIndex].id,
                },
                body: {
                    teamResults: formData.teamResults.map(results => ({
                        registrationId: results.registrationId,
                        place: Number(results.place),
                    })),
                },
            })
            if (error) {
                feedback.error(t('common.error.unexpected'))
            } else {
                feedback.success('[todo] Results successfully saved')
            }
            setSubmitting(false)
        }
        setReloadData(!reloadData)
        closeDialog()
    }

    return (
        <>
            {progressDto && currentRound ? (
                <Box>
                    <LoadingButton
                        label={'Create next round'}
                        pending={submitting}
                        disabled={
                            progressDto.canNotCreateRoundReasons.length > 0 ||
                            progressDto.lastRoundFinished
                                ? true
                                : undefined
                        }
                        variant={'contained'}
                        onClick={handleCreateNextRound}
                    />
                    {progressDto.canNotCreateRoundReasons.map(reason => (
                        <Typography>{reason}</Typography>
                    ))}
                    <Box>
                        {progressDto.rounds.map((round, roundIndex) => (
                            <Box sx={{p: 2, border: 1}}>
                                <Typography variant={'h3'}>{round.name}</Typography>
                                {round.required && (
                                    <Typography>
                                        {t('event.competition.setup.round.required')}
                                    </Typography>
                                )}
                                {round.matches
                                    .sort((a, b) => a.executionOrder - b.executionOrder)
                                    .map((match, matchIndex) => (
                                        <Card sx={{p: 2, mb: 2}}>
                                            <Stack
                                                direction={'row'}
                                                sx={{
                                                    justifyContent: 'space-between',
                                                    [theme.breakpoints.down('md')]: {
                                                        flexDirection: 'column',
                                                    },
                                                }}>
                                                <Box>
                                                    {match.name && (
                                                        <Typography>{match.name}</Typography>
                                                    )}
                                                    <Typography>
                                                        Weighting: {match.weighting}
                                                    </Typography>
                                                    {match.startTime && (
                                                        <Typography>
                                                            Start time: {match.startTime}
                                                        </Typography>
                                                    )}
                                                    {match.startTimeOffset && (
                                                        <Typography>
                                                            Start time offset:{' '}
                                                            {match.startTimeOffset}
                                                        </Typography>
                                                    )}
                                                </Box>
                                                <Stack direction={'column'} spacing={1}>
                                                    {roundIndex ===
                                                        progressDto.rounds.length - 1 && (
                                                        <Button
                                                            onClick={() => openDialog(matchIndex)}
                                                            variant={'outlined'}>
                                                            Enter Results
                                                        </Button>
                                                    )}
                                                    <Button variant={'outlined'}>Edit Match</Button>
                                                </Stack>
                                            </Stack>

                                            <Typography>Teams:</Typography>
                                            <Box sx={{display: 'flex', gap: 2}}>
                                                {match.teams
                                                    .sort((a, b) => a.startNumber - b.startNumber)
                                                    .map(team => (
                                                        <Box sx={{p: 2, border: 1}}>
                                                            <Typography>
                                                                {team.clubName +
                                                                    (team.name && ` ${team.name}`)}
                                                            </Typography>
                                                            <Typography>
                                                                Start number: {team.startNumber}
                                                            </Typography>
                                                            <Typography>
                                                                Place: {team.place}
                                                            </Typography>
                                                        </Box>
                                                    ))}
                                            </Box>
                                        </Card>
                                    ))}
                                {roundIndex === progressDto.rounds.length - 1 && (
                                    <Button variant={'outlined'}>TODO Delete Round</Button>
                                )}
                            </Box>
                        ))}
                    </Box>
                    <Dialog
                        open={dialogOpen}
                        fullWidth
                        maxWidth={'xs'}
                        onClose={closeDialog}
                        className="ready2race">
                        <Box sx={{m: 4}}>
                            <FormContainer
                                formContext={resultsFormContext}
                                onSuccess={onSubmitResults}>
                                {selectedMatchResultMatch && (
                                    <>
                                        <DialogContent>
                                            {selectedMatchResultMatch.name && (
                                                <>
                                                    <Typography variant={'h2'}>
                                                        Results for "{selectedMatchResultMatch.name}
                                                        "
                                                    </Typography>
                                                    <Divider sx={{my: 4}} />
                                                </>
                                            )}
                                            <Stack spacing={2}>
                                                {teamResultsError && (
                                                    <Typography color={'error'}>
                                                        {teamResultsError}
                                                    </Typography>
                                                )}
                                                {resultFields.map((_, fieldIndex) => (
                                                    <Card sx={{p: 2, flex: 1}}>
                                                        <Stack spacing={2}>
                                                            <Typography variant={'h6'}>
                                                                {`${selectedMatchResultMatch.teams[fieldIndex].clubName}` +
                                                                    (selectedMatchResultMatch.teams[
                                                                        fieldIndex
                                                                    ].name
                                                                        ? ` - ${selectedMatchResultMatch.teams[fieldIndex].name}`
                                                                        : '')}
                                                            </Typography>
                                                            <FormInputNumber
                                                                label={'[todo] Place'}
                                                                name={`teamResults[${fieldIndex}.place`}
                                                                required
                                                                min={1}
                                                                max={resultFields.length}
                                                                integer
                                                            />
                                                        </Stack>
                                                    </Card>
                                                ))}
                                            </Stack>
                                        </DialogContent>
                                        <DialogActions>
                                            <Button onClick={closeDialog} disabled={submitting}>
                                                {t('common.cancel')}
                                            </Button>
                                            {/* todo "Save and next button"*/}
                                            <SubmitButton
                                                label={t('common.save')}
                                                submitting={submitting}
                                            />
                                        </DialogActions>
                                    </>
                                )}
                            </FormContainer>
                        </Box>
                    </Dialog>
                </Box>
            ) : (
                progressDtoPending && <Throbber />
            )}
        </>
    )
}
export default CompetitionExecution

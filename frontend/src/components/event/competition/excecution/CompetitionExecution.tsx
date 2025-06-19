import {
    createNextCompetitionRound,
    getCompetitionExecutionProgress,
    updateMatchData,
    updateMatchResults,
} from '@api/sdk.gen.ts'
import {
    Alert,
    Box,
    Button,
    Card,
    Dialog,
    DialogActions,
    DialogContent,
    Divider,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
    useTheme,
} from '@mui/material'
import {competitionRoute, eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import {BaseSyntheticEvent, useState} from 'react'
import LoadingButton from '@components/form/LoadingButton.tsx'
import {FormContainer, useFieldArray, useForm} from 'react-hook-form-mui'
import Throbber from '@components/Throbber.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {groupBy} from '@utils/helpers.ts'
import {CompetitionMatchDto, CompetitionMatchTeamDto} from '@api/types.gen.ts'
import CompetitionExecutionMatchDialog from '@components/event/competition/excecution/CompetitionExecutionMatchDialog.tsx'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'

type EditMatchTeam = {
    registrationId: string
    startNumber: string
}
type EditMatchForm = {
    selectedMatchDto: CompetitionMatchDto | null
    startTime: string
    teams: EditMatchTeam[]
}

type EnterResultsTeam = {
    registrationId: string
    place: string
}
type EnterResultsForm = {
    selectedMatchDto: CompetitionMatchDto | null
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

    const resultsFormContext = useForm<EnterResultsForm>({
        values: {
            selectedMatchDto: null,
            teamResults: [],
        },
    })

    const selectedResultsMatch = resultsFormContext.watch('selectedMatchDto')

    const selectedResultsMatchIndex = (currentMatch: CompetitionMatchDto) =>
        currentRound ? currentRound.matches.findIndex(val => val.id === currentMatch?.id) : -1

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

    const mapTeamDtoToFormTeamResults = (teams: CompetitionMatchTeamDto[]): EnterResultsTeam[] => {
        return teams
            .sort((a, b) => a.startNumber - b.startNumber)
            .map(team => ({
                registrationId: team.registrationId,
                place: team.place?.toString() ?? '',
            }))
    }

    const [resultsDialogOpen, setResultsDialogOpen] = useState(false)
    const openResultsDialog = (matchIndex: number) => {
        if (currentRound) {
            setResultsDialogOpen(true)
            resultsFormContext.reset({
                selectedMatchDto: currentRound.matches[matchIndex],
                teamResults: mapTeamDtoToFormTeamResults(currentRound.matches[matchIndex].teams),
            })
        }
    }
    const closeResultsDialog = () => {
        setResultsDialogOpen(false)
    }

    const onSubmitResults = async (
        formData: EnterResultsForm,
        event: BaseSyntheticEvent | undefined,
    ) => {
        if (formData.selectedMatchDto === null || currentRound === undefined) {
            feedback.error(t('common.error.unexpected'))
        } else {
            setSubmitting(true)
            const {error} = await updateMatchResults({
                path: {
                    eventId: eventId,
                    competitionId: competitionId,
                    competitionMatchId: formData.selectedMatchDto.id,
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

        if ((event?.nativeEvent as SubmitEvent)?.submitter?.id === 'saveAndNext') {
            if (
                currentRound &&
                formData.selectedMatchDto !== null &&
                currentRound.matches.length >
                    selectedResultsMatchIndex(formData.selectedMatchDto) + 1
            ) {
                const nextMatch =
                    currentRound.matches[selectedResultsMatchIndex(formData.selectedMatchDto) + 1]
                resultsFormContext.reset({
                    selectedMatchDto: nextMatch,
                    teamResults: mapTeamDtoToFormTeamResults(nextMatch.teams),
                })
            }
        } else {
            closeResultsDialog()
        }
    }

    const editMatchFormContext = useForm<EditMatchForm>({
        values: {
            selectedMatchDto: null,
            startTime: '',
            teams: [],
        },
    })

    const selectedEditMatch = editMatchFormContext.watch('selectedMatchDto')

    const selectedEditMatchIndex = (currentMatch: CompetitionMatchDto) =>
        currentRound ? currentRound.matches.findIndex(val => val.id === currentMatch?.id) : -1

    const [teamEditMatchError, setTeamEditMatchError] = useState<string | null>(null)

    const {fields: editMatchFields} = useFieldArray({
        control: editMatchFormContext.control,
        name: 'teams',
        rules: {
            validate: values => {
                const duplicateStartNumbers = Array.from(groupBy(values, val => val.startNumber))
                    .filter(([, items]) => items.length > 1)
                    .map(([startNumber]) => startNumber)

                if (duplicateStartNumbers.length > 0) {
                    setTeamEditMatchError(
                        `[todo] Start numbers: ${duplicateStartNumbers.join(', ')} are used several times. Every place has to be unique.`,
                    )
                    return 'duplicates'
                }
                setTeamEditMatchError(null)
                return undefined
            },
        },
    })

    const mapTeamDtoToFormTeamData = (teams: CompetitionMatchTeamDto[]): EditMatchTeam[] => {
        return teams
            .sort((a, b) => a.startNumber - b.startNumber)
            .map(team => ({
                registrationId: team.registrationId,
                startNumber: team.startNumber?.toString() ?? '',
            }))
    }

    const [editMatchDialogOpen, setEditMatchDialogOpen] = useState(false)
    const openEditMatchDialog = (matchIndex: number) => {
        if (currentRound) {
            setEditMatchDialogOpen(true)
            editMatchFormContext.reset({
                selectedMatchDto: currentRound.matches[matchIndex],
                teams: mapTeamDtoToFormTeamData(currentRound.matches[matchIndex].teams),
            })
        }
    }
    const closeEditMatchDialog = () => {
        setEditMatchDialogOpen(false)
    }

    const onSubmitEditMatch = async (
        formData: EditMatchForm,
        event: BaseSyntheticEvent | undefined,
    ) => {
        if (formData.selectedMatchDto === null || currentRound === undefined) {
            feedback.error(t('common.error.unexpected'))
        } else {
            setSubmitting(true)
            const {error} = await updateMatchData({
                path: {
                    eventId: eventId,
                    competitionId: competitionId,
                    competitionMatchId: formData.selectedMatchDto.id,
                },
                body: {
                    startTime: takeIfNotEmpty(formData.startTime),
                    teams: formData.teams.map(team => ({
                        registrationId: team.registrationId,
                        startNumber: Number(team.startNumber),
                    })),
                },
            })
            if (error) {
                feedback.error(t('common.error.unexpected'))
            } else {
                feedback.success('[todo] Match data successfully saved')
            }
            setSubmitting(false)
        }
        setReloadData(!reloadData)

        if ((event?.nativeEvent as SubmitEvent)?.submitter?.id === 'saveAndNext') {
            if (
                currentRound &&
                formData.selectedMatchDto !== null &&
                currentRound.matches.length > selectedEditMatchIndex(formData.selectedMatchDto) + 1
            ) {
                const nextMatch =
                    currentRound.matches[selectedEditMatchIndex(formData.selectedMatchDto) + 1]
                editMatchFormContext.reset({
                    selectedMatchDto: nextMatch,
                    startTime: nextMatch.startTime ?? '',
                    teams: mapTeamDtoToFormTeamData(nextMatch.teams),
                })
            }
        } else {
            closeEditMatchDialog()
        }
    }

    return (
        <>
            {progressDto && currentRound ? (
                <Box>
                    <Box sx={{display: 'flex', my: 4}}>
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
                            <Alert severity={'warning'}>{reason}</Alert>
                        ))}
                    </Box>
                    <Box>
                        {progressDto.rounds
                            .map((r, idx) => ({roundIndex: idx, round: r}))
                            .sort((a, b) => b.roundIndex - a.roundIndex)
                            .map(r => r.round)
                            .map((round, roundIndex) => (
                                <>
                                    <Stack spacing={2}>
                                        <Typography variant={'h2'}>{round.name}</Typography>
                                        {round.required && (
                                            <Typography>
                                                {t('event.competition.setup.round.required')}
                                            </Typography>
                                        )}
                                        <Box sx={{display: 'flex', flexWrap: 'wrap', gap: 4}}>
                                            {round.matches
                                                .sort((a, b) => a.executionOrder - b.executionOrder)
                                                .map((match, matchIndex) => (
                                                    <Card sx={{p: 2, minWidth: 400, flex: 1}}>
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
                                                                    <Typography variant={'h3'}>
                                                                        {match.name}
                                                                    </Typography>
                                                                )}
                                                                <Typography>
                                                                    Weighting: {match.weighting}
                                                                </Typography>
                                                                <Typography>
                                                                    Start time:{' '}
                                                                    {match.startTime ?? '-'}
                                                                </Typography>
                                                                {match.startTimeOffset && (
                                                                    <Typography>
                                                                        Start time offset:{' '}
                                                                        {match.startTimeOffset}
                                                                    </Typography>
                                                                )}
                                                            </Box>
                                                            <Stack direction={'column'} spacing={1}>
                                                                {roundIndex === 0 && (
                                                                    <Button
                                                                        onClick={() =>
                                                                            openResultsDialog(
                                                                                matchIndex,
                                                                            )
                                                                        }
                                                                        variant={'outlined'}>
                                                                        Enter Results
                                                                    </Button>
                                                                )}
                                                                <Button
                                                                    onClick={() =>
                                                                        openEditMatchDialog(
                                                                            matchIndex,
                                                                        )
                                                                    }
                                                                    variant={'outlined'}>
                                                                    Edit Match
                                                                </Button>
                                                            </Stack>
                                                        </Stack>
                                                        <Divider sx={{my: 2}} />
                                                        <TableContainer>
                                                            <Table>
                                                                <TableHead>
                                                                    <TableRow>
                                                                        <TableCell width="25%">
                                                                            Start number
                                                                        </TableCell>
                                                                        <TableCell width="50%">
                                                                            Team
                                                                        </TableCell>
                                                                        <TableCell width="25%">
                                                                            Place
                                                                        </TableCell>
                                                                    </TableRow>
                                                                </TableHead>
                                                                <TableBody>
                                                                    {match.teams
                                                                        .sort(
                                                                            (a, b) =>
                                                                                a.startNumber -
                                                                                b.startNumber,
                                                                        )
                                                                        .map(team => (
                                                                            <TableRow
                                                                                key={
                                                                                    team.registrationId
                                                                                }>
                                                                                <TableCell width="25%">
                                                                                    {
                                                                                        team.startNumber
                                                                                    }
                                                                                </TableCell>
                                                                                <TableCell width="50%">
                                                                                    {team.clubName +
                                                                                        (team.name &&
                                                                                            ` ${team.name}`)}
                                                                                </TableCell>
                                                                                <TableCell width="25%">
                                                                                    {team.place}
                                                                                </TableCell>
                                                                            </TableRow>
                                                                        ))}
                                                                </TableBody>
                                                            </Table>
                                                        </TableContainer>
                                                    </Card>
                                                ))}
                                        </Box>
                                        {roundIndex === 0 && (
                                            <Button variant={'outlined'}>TODO Delete Round</Button>
                                        )}
                                    </Stack>
                                    {roundIndex < progressDto.rounds.length && (
                                        <Divider sx={{my: 8}} />
                                    )}
                                </>
                            ))}
                    </Box>
                    <Dialog
                        open={resultsDialogOpen}
                        fullWidth
                        maxWidth={'sm'}
                        onClose={closeResultsDialog}
                        className="ready2race">
                        <Box sx={{m: 2}}>
                            <FormContainer
                                formContext={resultsFormContext}
                                onSuccess={onSubmitResults}>
                                {selectedResultsMatch && (
                                    <>
                                        <CompetitionExecutionMatchDialog
                                            enterResults={true}
                                            title={
                                                selectedResultsMatch.name
                                                    ? `Results for "${selectedResultsMatch.name}"`
                                                    : 'Match results'
                                            }
                                            selectedMatchDto={selectedResultsMatch}
                                            fieldArrayError={teamResultsError}
                                            submitting={submitting}
                                            closeDialog={closeResultsDialog}
                                            saveAndNext={
                                                currentRound.matches.length >
                                                selectedResultsMatchIndex(selectedResultsMatch) + 1
                                            }>
                                            <Table>
                                                <TableHead>
                                                    <TableRow>
                                                        <TableCell width="25%">
                                                            Start number
                                                        </TableCell>
                                                        <TableCell width="50%">Team</TableCell>
                                                        <TableCell width="25%">Place</TableCell>
                                                    </TableRow>
                                                </TableHead>
                                                <TableBody>
                                                    {resultFields.map((value, fieldIndex) => (
                                                        <TableRow key={value.id}>
                                                            <TableCell width="25%">
                                                                {
                                                                    selectedResultsMatch.teams[
                                                                        fieldIndex
                                                                    ].startNumber
                                                                }
                                                            </TableCell>
                                                            <TableCell width="50%">
                                                                {`${selectedResultsMatch.teams[fieldIndex].clubName}` +
                                                                    (selectedResultsMatch.teams[
                                                                        fieldIndex
                                                                    ].name
                                                                        ? ` - ${selectedResultsMatch.teams[fieldIndex].name}`
                                                                        : '')}
                                                            </TableCell>
                                                            <TableCell width="25%">
                                                                <FormInputNumber
                                                                    name={`teamResults[${fieldIndex}.place`}
                                                                    required
                                                                    min={1}
                                                                    max={resultFields.length}
                                                                    integer
                                                                />
                                                            </TableCell>
                                                        </TableRow>
                                                    ))}
                                                </TableBody>
                                            </Table>
                                        </CompetitionExecutionMatchDialog>
                                    </>
                                )}
                            </FormContainer>
                        </Box>
                    </Dialog>
                    <Dialog
                        open={editMatchDialogOpen}
                        fullWidth
                        maxWidth={'sm'}
                        onClose={closeEditMatchDialog}
                        className="ready2race">
                        <Box sx={{m: 2}}>
                            <FormContainer
                                formContext={editMatchFormContext}
                                onSuccess={onSubmitEditMatch}>
                                {selectedEditMatch && (
                                    <>
                                        <CompetitionExecutionMatchDialog
                                            enterResults={false}
                                            title={
                                                selectedEditMatch.name
                                                    ? `Match data for "${selectedEditMatch.name}"`
                                                    : 'Match data'
                                            }
                                            selectedMatchDto={selectedEditMatch}
                                            fieldArrayError={teamEditMatchError}
                                            submitting={submitting}
                                            closeDialog={closeEditMatchDialog}
                                            saveAndNext={
                                                currentRound.matches.length >
                                                selectedEditMatchIndex(selectedEditMatch) + 1
                                            }>
                                            {/*todo: Start Time*/}
                                            <FormInputNumber name={''} label={"TODO START TIME"}/>
                                            <Table>
                                                <TableHead>
                                                    <TableRow>
                                                        <TableCell width="40%">
                                                            Start number
                                                        </TableCell>
                                                        <TableCell width="60%">Team</TableCell>
                                                    </TableRow>
                                                </TableHead>
                                                <TableBody>
                                                    {editMatchFields.map((value, fieldIndex) => (
                                                        <TableRow key={value.id}>
                                                            <TableCell width="40%">
                                                                <FormInputNumber
                                                                    name={`teams[${fieldIndex}.startNumber`}
                                                                    required
                                                                    min={1}
                                                                    max={editMatchFields.length}
                                                                    integer
                                                                />
                                                            </TableCell>
                                                            <TableCell width="60%">
                                                                {`${selectedEditMatch.teams[fieldIndex].clubName}` +
                                                                    (selectedEditMatch.teams[
                                                                        fieldIndex
                                                                    ].name
                                                                        ? ` - ${selectedEditMatch.teams[fieldIndex].name}`
                                                                        : '')}
                                                            </TableCell>
                                                        </TableRow>
                                                    ))}
                                                </TableBody>
                                            </Table>

                                        </CompetitionExecutionMatchDialog>
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

import {
    createNextCompetitionRound,
    deleteCurrentCompetitionExecutionRound,
    getCompetitionExecutionProgress,
    updateMatchData,
    updateMatchResults,
} from '@api/sdk.gen.ts'
import {
    Box,
    Button,
    Card,
    Dialog,
    Divider,
    List,
    ListItemText,
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
import {groupBy, shuffle} from '@utils/helpers.ts'
import {
    CompetitionExecutionCanNotCreateRoundReason,
    CompetitionMatchDto,
    CompetitionMatchTeamDto,
    CompetitionRoundDto,
} from '@api/types.gen.ts'
import CompetitionExecutionMatchDialog from '@components/event/competition/excecution/CompetitionExecutionMatchDialog.tsx'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import FormInputDateTime from '@components/form/input/FormInputDateTime.tsx'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import WarningIcon from '@mui/icons-material/Warning'
import Info from '@mui/icons-material/Info'

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

    const {confirmAction} = useConfirmation()

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

    const matchesFiltered = (matches: CompetitionMatchDto[]) => {
        return matches
            .filter(match => match.teams.length > 1)
            .sort((a, b) => a.executionOrder - b.executionOrder)
    }

    const currentRound = progressDto?.rounds[progressDto?.rounds.length - 1]
    const currentRoundMatches = currentRound ? matchesFiltered(currentRound.matches) : undefined

    const resultsFormContext = useForm<EnterResultsForm>({
        values: {
            selectedMatchDto: null,
            teamResults: [],
        },
    })

    const selectedResultsMatch = resultsFormContext.watch('selectedMatchDto')

    const selectedMatchIndex = (currentMatch: CompetitionMatchDto) =>
        currentRoundMatches ? currentRoundMatches.findIndex(val => val.id === currentMatch?.id) : -1

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
        if (currentRoundMatches) {
            setResultsDialogOpen(true)
            console.log(matchIndex, currentRoundMatches)
            resultsFormContext.reset({
                selectedMatchDto: currentRoundMatches[matchIndex],
                teamResults: mapTeamDtoToFormTeamResults(currentRoundMatches[matchIndex].teams),
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
                currentRoundMatches &&
                formData.selectedMatchDto !== null &&
                currentRoundMatches.length > selectedMatchIndex(formData.selectedMatchDto) + 1
            ) {
                const nextMatch =
                    currentRoundMatches[selectedMatchIndex(formData.selectedMatchDto) + 1]
                resultsFormContext.reset({
                    selectedMatchDto: nextMatch,
                    teamResults: mapTeamDtoToFormTeamResults(nextMatch.teams),
                })
            }
        } else {
            closeResultsDialog()
        }
    }

    // todo: merge following code with code for resultsUpdate
    const editMatchFormContext = useForm<EditMatchForm>({
        values: {
            selectedMatchDto: null,
            startTime: '',
            teams: [],
        },
    })

    const selectedEditMatch = editMatchFormContext.watch('selectedMatchDto')

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
        if (currentRoundMatches) {
            setEditMatchDialogOpen(true)
            editMatchFormContext.reset({
                selectedMatchDto: currentRoundMatches[matchIndex],
                teams: mapTeamDtoToFormTeamData(currentRoundMatches[matchIndex].teams),
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
                currentRoundMatches &&
                formData.selectedMatchDto !== null &&
                currentRoundMatches.length > selectedMatchIndex(formData.selectedMatchDto) + 1
            ) {
                const nextMatch =
                    currentRoundMatches[selectedMatchIndex(formData.selectedMatchDto) + 1]
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

    const onRandomizeStartNumbers = () => {
        if (selectedEditMatch) {
            const newStartNumbers = shuffle(selectedEditMatch.teams.map(t => t.startNumber))
            editMatchFormContext.setValue(
                `teams`,
                selectedEditMatch.teams.map((team, idx) => ({
                    registrationId: team.registrationId,
                    startNumber: newStartNumbers[idx].toString(),
                })),
            )
        }
    }

    const deleteCurrentRound = async () => {
        confirmAction(
            async () => {
                setSubmitting(true)
                const {error} = await deleteCurrentCompetitionExecutionRound({
                    path: {
                        eventId: eventId,
                        competitionId: competitionId,
                    },
                })
                setSubmitting(false)
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                } else {
                    feedback.success('[todo] Round successfully deleted')
                }
                setReloadData(!reloadData)
            },
            {
                title: t('common.confirmation.title'),
                content: '[todo] Are you sure you want to delete the latest round?',
                okText: t('common.delete'),
            },
        )
    }

    const allRoundsCreated =
        progressDto?.canNotCreateRoundReasons.find(r => r === 'ALL_ROUNDS_CREATED') !== undefined

    const getReasonText = (reason: CompetitionExecutionCanNotCreateRoundReason) => {
        if (reason === 'NO_ROUNDS_IN_SETUP') {
            return '[todo] There are no rounds defined in the competition setup.'
        } else if (reason === 'NO_SETUP_MATCHES') {
            return '[todo] There are no matches defined for the next round in the competition setup'
        } else if (reason === 'NO_REGISTRATIONS') {
            return '[todo] There are no registrations for this competition.'
        } else if (reason === 'REGISTRATIONS_NOT_FINALIZED') {
            return '[todo] The registrations for this event are not finalized. '
        } // todo: link to event actions tab
        else if (reason === 'NOT_ENOUGH_TEAM_SPACE') {
            return '[todo] There are more registrations than teams defined in the competition setup. Please make sure that there are enough teams or matches defined for the first round of the competition setup.'
        } else if (reason === 'NOT_ALL_PLACES_SET') {
            return '[todo] Match results are missing.'
        }
    }

    const automaticQualifications = (round: CompetitionRoundDto) => {
        return round.matches
            .filter(match => match.teams.length === 1)
            .map(
                match =>
                    match.teams[0].clubName + (match.teams[0].name && ` ${match.teams[0].name}`),
            )
    }

    return (
        <>
            {progressDto ? (
                <Box>
                    {!allRoundsCreated && (
                        <Box sx={{my: 4, display: 'flex', alignItems: 'center'}}>
                            <Button
                                disabled={
                                    progressDto.canNotCreateRoundReasons.length > 0 ||
                                    progressDto.lastRoundFinished
                                        ? true
                                        : undefined
                                }
                                variant={'contained'}
                                onClick={handleCreateNextRound}>
                                Create next round
                            </Button>
                            {progressDto.canNotCreateRoundReasons.length > 0 && (
                                <HtmlTooltip
                                    placement={'right'}
                                    title={
                                        <Stack spacing={1} p={1}>
                                            {progressDto.canNotCreateRoundReasons.map(
                                                (reason, idx) => (
                                                    <>
                                                        <Stack
                                                            direction={'row'}
                                                            spacing={1}
                                                            key={reason}>
                                                            <WarningIcon color={'warning'} />
                                                            <Typography>
                                                                {getReasonText(reason)}
                                                            </Typography>
                                                        </Stack>
                                                        {idx <
                                                            progressDto.canNotCreateRoundReasons
                                                                .length -
                                                                1 && <Divider />}
                                                    </>
                                                ),
                                            )}
                                        </Stack>
                                    }>
                                    <Info sx={{ml: 1}} color={'info'} fontSize={'small'} />
                                </HtmlTooltip>
                            )}
                        </Box>
                    )}
                    <Box>
                        {progressDto.rounds
                            .map((r, idx) => ({roundIndex: idx, round: r}))
                            .sort((a, b) => b.roundIndex - a.roundIndex)
                            .map(r => r.round)
                            .map((round, roundIndex) => (
                                <>
                                    <Stack
                                        key={`${round.matches[0]?.id}-r${roundIndex}`}
                                        spacing={2}>
                                        <Typography variant={'h2'}>{round.name}</Typography>
                                        {round.required && (
                                            <Typography>
                                                {t('event.competition.setup.round.required')}
                                            </Typography>
                                        )}
                                        {automaticQualifications(round).length > 0 && (
                                            <Box>
                                                <Box sx={{my: 2}}>
                                                    <Typography variant={'h6'}>
                                                        [todo] Automatic Qualifications:
                                                    </Typography>
                                                    <List>
                                                        {automaticQualifications(round).map(
                                                            team => (
                                                                <ListItemText>{team}</ListItemText>
                                                            ),
                                                        )}
                                                    </List>
                                                </Box>
                                            </Box>
                                        )}
                                        <Box sx={{display: 'flex', flexWrap: 'wrap', gap: 4}}>
                                            {matchesFiltered(round.matches).map(
                                                (match, matchIndex) => (
                                                    <Card
                                                        key={match.id}
                                                        sx={{p: 2, minWidth: 400, flex: 1}}>
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
                                                                    <LoadingButton
                                                                        disabled={submitting}
                                                                        onClick={() =>
                                                                            openResultsDialog(
                                                                                matchIndex,
                                                                            )
                                                                        }
                                                                        variant={'outlined'}
                                                                        pending={submitting}>
                                                                        {'Enter Results'}
                                                                    </LoadingButton>
                                                                )}
                                                                <LoadingButton
                                                                    onClick={() =>
                                                                        openEditMatchDialog(
                                                                            matchIndex,
                                                                        )
                                                                    }
                                                                    variant={'outlined'}
                                                                    pending={submitting}>
                                                                    {'Edit Match'}
                                                                </LoadingButton>
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
                                                ),
                                            )}
                                        </Box>
                                        {roundIndex === 0 && (
                                            <LoadingButton
                                                pending={submitting}
                                                onClick={deleteCurrentRound}
                                                variant={'outlined'}>
                                                {'[todo] Delete Round'}
                                            </LoadingButton>
                                        )}
                                    </Stack>
                                    {roundIndex < progressDto.rounds.length && (
                                        <Divider
                                            key={`${round.matches[0]?.id}-d${roundIndex}`}
                                            variant={'middle'}
                                            sx={{my: 8}}
                                        />
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
                                {selectedResultsMatch && currentRoundMatches && (
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
                                                currentRoundMatches.length >
                                                selectedMatchIndex(selectedResultsMatch) + 1
                                            }>
                                            <TableContainer>
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
                                            </TableContainer>
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
                                {selectedEditMatch && currentRoundMatches && (
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
                                                currentRoundMatches.length >
                                                selectedMatchIndex(selectedEditMatch) + 1
                                            }>
                                            <FormInputDateTime
                                                name={'startTime'}
                                                label={'[todo] Start time'}
                                                timeSteps={{minutes: 1}}
                                            />
                                            <Box sx={{mt: 4}}>
                                                <LoadingButton
                                                    pending={submitting}
                                                    onClick={onRandomizeStartNumbers}
                                                    variant={'outlined'}>
                                                    {'Randomize start numbers'}
                                                </LoadingButton>

                                                <TableContainer>
                                                    <Table>
                                                        <TableHead>
                                                            <TableRow>
                                                                <TableCell width="25%">
                                                                    Start number
                                                                </TableCell>
                                                                <TableCell width="75%">
                                                                    Team
                                                                </TableCell>
                                                            </TableRow>
                                                        </TableHead>
                                                        <TableBody>
                                                            {editMatchFields.map(
                                                                (value, fieldIndex) => (
                                                                    <TableRow key={value.id}>
                                                                        <TableCell width="25%">
                                                                            <FormInputNumber
                                                                                name={`teams[${fieldIndex}.startNumber`}
                                                                                required
                                                                                min={1}
                                                                                max={
                                                                                    editMatchFields.length
                                                                                }
                                                                                integer
                                                                            />
                                                                        </TableCell>
                                                                        <TableCell width="75%">
                                                                            {`${selectedEditMatch.teams[fieldIndex].clubName}` +
                                                                                (selectedEditMatch
                                                                                    .teams[
                                                                                    fieldIndex
                                                                                ].name
                                                                                    ? ` - ${selectedEditMatch.teams[fieldIndex].name}`
                                                                                    : '')}
                                                                        </TableCell>
                                                                    </TableRow>
                                                                ),
                                                            )}
                                                        </TableBody>
                                                    </Table>
                                                </TableContainer>
                                            </Box>
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

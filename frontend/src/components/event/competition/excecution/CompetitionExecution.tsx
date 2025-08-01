import {
    createNextCompetitionRound,
    getCompetitionExecutionProgress,
    updateMatchData,
    updateMatchResults,
} from '@api/sdk.gen.ts'
import {
    Box,
    Button,
    Card,
    Checkbox,
    Dialog,
    Divider,
    FormControlLabel,
    Link,
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
} from '@mui/material'
import {competitionRoute, eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import {BaseSyntheticEvent, Fragment, useState} from 'react'
import LoadingButton from '@components/form/LoadingButton.tsx'
import {FormContainer, useFieldArray, useForm} from 'react-hook-form-mui'
import Throbber from '@components/Throbber.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {groupBy, shuffle} from '@utils/helpers.ts'
import {
    CompetitionExecutionCanNotCreateRoundReason,
    CompetitionMatchDto,
    CompetitionMatchTeamDto,
} from '@api/types.gen.ts'
import CompetitionExecutionMatchDialog from '@components/event/competition/excecution/CompetitionExecutionMatchDialog.tsx'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import FormInputDateTime from '@components/form/input/FormInputDateTime.tsx'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import WarningIcon from '@mui/icons-material/Warning'
import Info from '@mui/icons-material/Info'
import InlineLink from '@components/InlineLink.tsx'
import CompetitionExecutionRound from '@components/event/competition/excecution/CompetitionExecutionRound.tsx'

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
                    feedback.error(t('event.competition.execution.progress.error'))
                }
                handleAccordionExpandedChange()
            },
            deps: [eventId, competitionId, reloadData],
        },
    )
    const sortedRounds = progressDto?.rounds
        .map((r, idx) => ({roundIndex: idx, round: r}))
        .sort((a, b) => b.roundIndex - a.roundIndex)
        .map(r => r.round)

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
            feedback.error(t('event.competition.execution.nextRound.error'))
        } else {
            feedback.success(t('event.competition.execution.nextRound.success'))
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
                        t(
                            duplicatePlaces.length === 1
                                ? 'event.competition.execution.results.validation.duplicates.single'
                                : 'event.competition.execution.results.validation.duplicates.multiple',
                            {places: duplicatePlaces.join(', ')},
                        ) +
                            ' ' +
                            t('event.competition.execution.results.validation.duplicates.message'),
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
                feedback.error(t('event.competition.execution.results.submit.error'))
            } else {
                feedback.success(t('event.competition.execution.results.submit.success'))
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
                        t(
                            duplicateStartNumbers.length === 1
                                ? 'event.competition.execution.matchData.validation.duplicates.single'
                                : 'event.competition.execution.matchData.validation.duplicates.multiple',
                            {startNumbers: duplicateStartNumbers.join(', ')},
                        ) +
                            ' ' +
                            t(
                                'event.competition.execution.matchData.validation.duplicates.message',
                            ),
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
    const openEditMatchDialog = (roundIndex: number, matchIndex: number) => {
        const selectedMatch = matchesFiltered(sortedRounds?.[roundIndex].matches ?? [])[matchIndex]
        if (selectedMatch) {
            setEditMatchDialogOpen(true)
            editMatchFormContext.reset({
                selectedMatchDto: selectedMatch,
                teams: mapTeamDtoToFormTeamData(selectedMatch.teams),
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
                feedback.error(t('event.competition.execution.matchData.submit.error'))
            } else {
                feedback.success(t('event.competition.execution.matchData.submit.success'))
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

    const allRoundsCreated =
        progressDto?.canNotCreateRoundReasons.find(r => r === 'ALL_ROUNDS_CREATED') !== undefined

    const getReasonText = (reason: CompetitionExecutionCanNotCreateRoundReason) => {
        if (reason === 'REGISTRATIONS_NOT_FINALIZED') {
            return (
                <>
                    {t(
                        'event.competition.execution.nextRound.reasons.registrationsNotFinalized.textStart',
                    )}
                    <InlineLink to={'/event/$eventId'} search={{tab: 'registrations'}}>
                        {t(
                            'event.competition.execution.nextRound.reasons.registrationsNotFinalized.link',
                        )}
                    </InlineLink>
                    {t(
                        'event.competition.execution.nextRound.reasons.registrationsNotFinalized.textEnd',
                    )}
                </>
            )
        } else {
            return reason === 'NO_ROUNDS_IN_SETUP'
                ? t('event.competition.execution.nextRound.reasons.noRoundsInSetup')
                : reason === 'NO_SETUP_MATCHES'
                  ? t('event.competition.execution.nextRound.reasons.noSetupMatches')
                  : reason === 'NO_REGISTRATIONS'
                    ? t('event.competition.execution.nextRound.reasons.noRegistrations')
                    : reason === 'NOT_ENOUGH_TEAM_SPACE'
                      ? t('event.competition.execution.nextRound.reasons.notEnoughTeamSpace')
                      : reason === 'NOT_ALL_PLACES_SET'
                        ? t('event.competition.execution.nextRound.reasons.notAllPlacesSet')
                        : ''
        }
    }

    const [accordionsOpen, setAccordionsOpen] = useState<boolean[][]>([])
    const handleAccordionExpandedChange = (expandedProps?: {
        roundIndex: number
        accordionIndex: number
        isExpanded: boolean
    }) => {
        setAccordionsOpen(
            sortedRounds?.map((_, idx) => [
                expandedProps &&
                expandedProps.accordionIndex === 0 &&
                expandedProps.roundIndex === idx
                    ? expandedProps.isExpanded
                    : (accordionsOpen[idx]?.[0] ?? false),
                expandedProps &&
                expandedProps.accordionIndex === 1 &&
                expandedProps.roundIndex === idx
                    ? expandedProps.isExpanded
                    : (accordionsOpen[idx]?.[1] ?? false),
            ]) ?? [],
        )
    }

    const handleToggleRunningState = async (match: CompetitionMatchDto) => {
        // Check if match has no places set
        const hasPlacesSet = match.teams.some(
            team => team.place !== null && team.place !== undefined,
        )
        if (hasPlacesSet) {
            feedback.error(t('event.competition.execution.running.error.hasPlaces'))
            return
        }

        setSubmitting(true)
        const {error} = await updateMatchRunningState({
            path: {
                eventId: eventId,
                competitionId: competitionId,
                competitionMatchId: match.id,
            },
            body: {
                currentlyRunning: !match.currentlyRunning,
            },
        })
        setSubmitting(false)

        if (error) {
            feedback.error(t('event.competition.execution.running.error.update'))
        } else {
            feedback.success(t('event.competition.execution.running.success'))
            setReloadData(!reloadData)
        }
    }

    // {/* Only show toggle if match has no places set */}
    // {!match.teams.some(
    //     team =>
    //         team.place !== null &&
    //         team.place !== undefined,
    // ) && (
    //     <FormControlLabel
    //         control={
    //             <Checkbox
    //                 checked={match.currentlyRunning}
    //                 onChange={() =>
    //                     handleToggleRunningState(
    //                         match,
    //                     )
    //                 }
    //                 disabled={submitting}
    //             />
    //         }
    //         label={t(
    //             'event.competition.execution.match.currentlyRunning',
    //         )}
    //     />
    // )}

    return progressDto && sortedRounds ? (
        <Box>
            {!allRoundsCreated && (
                <Box sx={{my: 2, display: 'flex', alignItems: 'center'}}>
                    <Button
                        disabled={
                            progressDto.canNotCreateRoundReasons.length > 0 ||
                            progressDto.lastRoundFinished
                                ? true
                                : undefined
                        }
                        variant={'contained'}
                        onClick={handleCreateNextRound}>
                        {t('event.competition.execution.nextRound.create')}
                    </Button>
                    {progressDto.canNotCreateRoundReasons.length > 0 && (
                        <HtmlTooltip
                            placement={'right'}
                            title={
                                <Stack spacing={1} p={1}>
                                    {progressDto.canNotCreateRoundReasons.map((reason, idx) => (
                                        <Fragment key={reason}>
                                            <Stack direction={'row'} spacing={1}>
                                                <WarningIcon color={'warning'} />
                                                <Typography>{getReasonText(reason)}</Typography>
                                            </Stack>
                                            {idx <
                                                progressDto.canNotCreateRoundReasons.length - 1 && (
                                                <Divider />
                                            )}
                                        </Fragment>
                                    ))}
                                </Stack>
                            }>
                            <Info sx={{ml: 1}} color={'info'} fontSize={'small'} />
                        </HtmlTooltip>
                    )}
                </Box>
            )}
            <Stack spacing={6}>
                {sortedRounds.map((round, roundIndex) => (
                    <CompetitionExecutionRound
                        key={round.setupRoundId}
                        round={round}
                        roundIndex={roundIndex}
                        filteredMatches={matchesFiltered(round.matches)}
                        reloadRoundDto={() => setReloadData(!reloadData)}
                        setSubmitting={setSubmitting}
                        submitting={submitting}
                        openResultsDialog={openResultsDialog}
                        openEditMatchDialog={openEditMatchDialog}
                        accordionsExpanded={accordionsOpen[roundIndex]}
                        handleAccordionExpandedChange={(accordionIndex, isExpanded) =>
                            handleAccordionExpandedChange({roundIndex, accordionIndex, isExpanded})
                        }
                    />
                ))}
            </Stack>
            <Dialog
                open={resultsDialogOpen}
                fullWidth
                maxWidth={'sm'}
                onClose={closeResultsDialog}
                className="ready2race">
                <Box sx={{m: 2}}>
                    <FormContainer formContext={resultsFormContext} onSuccess={onSubmitResults}>
                        {selectedResultsMatch && currentRoundMatches && (
                            <CompetitionExecutionMatchDialog
                                enterResults={true}
                                title={
                                    selectedResultsMatch.name
                                        ? t('event.competition.execution.results.title.named', {
                                              matchName: selectedResultsMatch.name,
                                          })
                                        : t('event.competition.execution.results.title.unnamed')
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
                                                    {t(
                                                        'event.competition.execution.match.startNumber',
                                                    )}
                                                </TableCell>
                                                <TableCell width="50%">
                                                    {t('event.competition.execution.match.team')}
                                                </TableCell>
                                                <TableCell width="25%">
                                                    {t('event.competition.execution.match.place')}
                                                </TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {resultFields.map((value, fieldIndex) => (
                                                <TableRow key={value.id}>
                                                    <TableCell width="25%">
                                                        {
                                                            selectedResultsMatch.teams[fieldIndex]
                                                                .startNumber
                                                        }
                                                    </TableCell>
                                                    <TableCell width="50%">
                                                        {`${selectedResultsMatch.teams[fieldIndex].clubName}` +
                                                            (selectedResultsMatch.teams[fieldIndex]
                                                                .name
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
                    <FormContainer formContext={editMatchFormContext} onSuccess={onSubmitEditMatch}>
                        {selectedEditMatch && currentRoundMatches && (
                            <CompetitionExecutionMatchDialog
                                enterResults={false}
                                title={
                                    selectedEditMatch.name
                                        ? t('event.competition.execution.matchData.title.named', {
                                              matchName: selectedEditMatch.name,
                                          })
                                        : t('event.competition.execution.matchData.title.unnamed')
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
                                    label={t('event.competition.execution.match.startTime')}
                                    timeSteps={{minutes: 1}}
                                />
                                <Box sx={{mt: 4}}>
                                    <LoadingButton
                                        pending={submitting}
                                        onClick={onRandomizeStartNumbers}
                                        variant={'outlined'}>
                                        {t(
                                            'event.competition.execution.matchData.randomizeStartNumbers',
                                        )}
                                    </LoadingButton>
                                    <TableContainer>
                                        <Table>
                                            <TableHead>
                                                <TableRow>
                                                    <TableCell width="25%">
                                                        {t(
                                                            'event.competition.execution.match.startNumber',
                                                        )}
                                                    </TableCell>
                                                    <TableCell width="75%">
                                                        {t(
                                                            'event.competition.execution.match.team',
                                                        )}
                                                    </TableCell>
                                                </TableRow>
                                            </TableHead>
                                            <TableBody>
                                                {editMatchFields.map((value, fieldIndex) => (
                                                    <TableRow key={value.id}>
                                                        <TableCell width="25%">
                                                            <FormInputNumber
                                                                name={`teams[${fieldIndex}.startNumber`}
                                                                required
                                                                min={1}
                                                                max={editMatchFields.length}
                                                                integer
                                                            />
                                                        </TableCell>
                                                        <TableCell width="75%">
                                                            {`${selectedEditMatch.teams[fieldIndex].clubName}` +
                                                                (selectedEditMatch.teams[fieldIndex]
                                                                    .name
                                                                    ? ` - ${selectedEditMatch.teams[fieldIndex].name}`
                                                                    : '')}
                                                        </TableCell>
                                                    </TableRow>
                                                ))}
                                            </TableBody>
                                        </Table>
                                    </TableContainer>
                                </Box>
                            </CompetitionExecutionMatchDialog>
                        )}
                    </FormContainer>
                </Box>
            </Dialog>
        </Box>
    ) : (
        progressDtoPending && <Throbber />
    )
}
export default CompetitionExecution

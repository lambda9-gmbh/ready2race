import {
    createNextCompetitionRound,
    deleteCurrentCompetitionExecutionRound,
    downloadStartList,
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
    useTheme,
} from '@mui/material'
import {competitionRoute, eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import {BaseSyntheticEvent, useRef, useState} from 'react'
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
    StartListFileType,
} from '@api/types.gen.ts'
import CompetitionExecutionMatchDialog from '@components/event/competition/excecution/CompetitionExecutionMatchDialog.tsx'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import FormInputDateTime from '@components/form/input/FormInputDateTime.tsx'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import WarningIcon from '@mui/icons-material/Warning'
import Info from '@mui/icons-material/Info'
import InlineLink from '@components/InlineLink.tsx'
import Substitutions from '@components/event/competition/excecution/Substitutions.tsx'
import SelectionMenu from "@components/SelectionMenu.tsx";

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

    const downloadRef = useRef<HTMLAnchorElement>(null)

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
                    feedback.error(t('event.competition.execution.progress.error'))
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
        const selectedMatch = progressDto?.rounds[roundIndex]?.matches[matchIndex]
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
                    feedback.error(t('event.competition.execution.deleteRound.error'))
                } else {
                    feedback.success(t('event.competition.execution.deleteRound.success'))
                }
                setReloadData(!reloadData)
            },
            {
                title: t('common.confirmation.title'),
                content: t('event.competition.execution.deleteRound.confirmation.content'),
                okText: t('common.delete'),
            },
        )
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
                    <InlineLink to={'/event/$eventId'} search={{tab: 'actions'}}>
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

    const automaticQualifications = (round: CompetitionRoundDto) => {
        return round.matches
            .filter(match => match.teams.length === 1)
            .map(
                match =>
                    match.teams[0].clubName + (match.teams[0].name && ` ${match.teams[0].name}`),
            )
    }

    const handleDownloadStartList = async (
        competitionMatchId: string,
        fileType: StartListFileType,
    ) => {
        const {data, error, response} = await downloadStartList({
            path: {
                eventId,
                competitionId,
                competitionMatchId,
            },
            query: {
                fileType,
            },
        })
        const anchor = downloadRef.current

        const disposition = response.headers.get('Content-Disposition')
        const filename = disposition?.match(/attachment; filename="?(.+)"?/)?.[1]

        if (error) {
            if (error.status.value === 409) {
                feedback.error(t('event.competition.execution.startList.error.missingStartTime'))
            } else {
                feedback.error(t('common.error.unexpected'))
            }
        } else if (data !== undefined && anchor) {
            // need Blob constructor for text/csv
            anchor.href = URL.createObjectURL(new Blob([data])) // TODO: @Memory: revokeObjectURL() when done
            anchor.download =
                filename ?? `startList-${competitionMatchId}.${fileType.toLowerCase()}`
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    return progressDto ? (
        <Box>
            <Link ref={downloadRef} display={'none'}></Link>
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
                        {t('event.competition.execution.nextRound.create')}
                    </Button>
                    {progressDto.canNotCreateRoundReasons.length > 0 && (
                        <HtmlTooltip
                            placement={'right'}
                            title={
                                <Stack spacing={1} p={1}>
                                    {progressDto.canNotCreateRoundReasons.map((reason, idx) => (
                                        <>
                                            <Stack direction={'row'} spacing={1} key={reason}>
                                                <WarningIcon color={'warning'} />
                                                <Typography>{getReasonText(reason)}</Typography>
                                            </Stack>
                                            {idx <
                                                progressDto.canNotCreateRoundReasons.length - 1 && (
                                                <Divider />
                                            )}
                                        </>
                                    ))}
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
                            <Stack key={`${round.matches[0]?.id}-r${roundIndex}`} spacing={2}>
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
                                                {t('event.competition.execution.teamsWithBye')}:
                                            </Typography>
                                            <List>
                                                {automaticQualifications(round).map(team => (
                                                    <ListItemText>{team}</ListItemText>
                                                ))}
                                            </List>
                                        </Box>
                                    </Box>
                                )}
                                {/*{roundIndex === 0 && <Substitutions reloadRoundDto={() => setReloadData(!reloadData)} roundDto={round} />}*/}
                                <Box sx={{display: 'flex', flexWrap: 'wrap', gap: 4}}>
                                    {matchesFiltered(round.matches).map((match, matchIndex) => (
                                        <Card key={match.id} sx={{p: 2, minWidth: 400, flex: 1}}>
                                            <Stack
                                                direction={'row'}
                                                sx={{
                                                    justifyContent: 'space-between',
                                                    [theme.breakpoints.down('md')]: {
                                                        flexDirection: 'column',
                                                    },
                                                }}>
                                                <Stack spacing={1}>
                                                    {match.name && (
                                                        <Typography variant={'h3'}>
                                                            {match.name}
                                                        </Typography>
                                                    )}
                                                    <Typography>
                                                        {t(
                                                            'event.competition.execution.match.startTime',
                                                        ) + ': '}
                                                        {match.startTime ?? '-'}
                                                    </Typography>
                                                    {match.startTimeOffset && (
                                                        <Typography>
                                                            {t(
                                                                'event.competition.execution.match.startTimeOffset',
                                                            ) + ': '}
                                                            {match.startTimeOffset}
                                                        </Typography>
                                                    )}
                                                </Stack>
                                                <Stack direction={'column'} spacing={1}>
                                                    {roundIndex === 0 && (
                                                        <LoadingButton
                                                            disabled={submitting}
                                                            onClick={() =>
                                                                openResultsDialog(matchIndex)
                                                            }
                                                            variant={'outlined'}
                                                            pending={submitting}>
                                                            {t(
                                                                'event.competition.execution.results.enter',
                                                            )}
                                                        </LoadingButton>
                                                    )}
                                                    <LoadingButton
                                                        onClick={() =>
                                                            openEditMatchDialog(
                                                                roundIndex,
                                                                matchIndex,
                                                            )
                                                        }
                                                        variant={'outlined'}
                                                        pending={submitting}>
                                                        {t(
                                                            'event.competition.execution.matchData.edit',
                                                        )}
                                                    </LoadingButton>
                                                    <SelectionMenu
                                                        anchor={{
                                                            button: {
                                                                vertical: 'bottom',
                                                                horizontal: 'right',
                                                            },
                                                            menu: {
                                                                vertical: 'top',
                                                                horizontal: 'right',
                                                            }
                                                        }}
                                                        buttonContent={t('event.competition.execution.startList.download')}
                                                        keyLabel={'competition-execution-startlist-download'}
                                                        onSelectItem={(fileType: string) =>
                                                            handleDownloadStartList(match.id, fileType as StartListFileType)
                                                        }
                                                        items={[
                                                            {
                                                                id: 'PDF',
                                                                label: t('event.competition.execution.startList.type.PDF')
                                                            },
                                                            {
                                                                id: 'CSV',
                                                                label: t('event.competition.execution.startList.type.CSV')
                                                            }
                                                        ] satisfies {id: StartListFileType, label: string}[]}
                                                    />
                                                </Stack>
                                            </Stack>
                                            <Divider sx={{my: 2}} />
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
                                                                {t(
                                                                    'event.competition.execution.match.team',
                                                                )}
                                                            </TableCell>
                                                            <TableCell width="25%">
                                                                {t(
                                                                    'event.competition.execution.match.place',
                                                                )}
                                                            </TableCell>
                                                        </TableRow>
                                                    </TableHead>
                                                    <TableBody>
                                                        {match.teams
                                                            .sort(
                                                                (a, b) =>
                                                                    a.startNumber - b.startNumber,
                                                            )
                                                            .map(team => (
                                                                <TableRow key={team.registrationId}>
                                                                    <TableCell width="25%">
                                                                        {team.startNumber}
                                                                    </TableCell>
                                                                    <TableCell width="50%">
                                                                        {team.clubName +
                                                                            (team.name
                                                                                ? ` ${team.name}`
                                                                                : '')}
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
                                    <LoadingButton
                                        pending={submitting}
                                        onClick={deleteCurrentRound}
                                        variant={'outlined'}>
                                        {t('event.competition.execution.deleteRound.delete')}
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

import {
    createNextCompetitionRound,
    downloadStartList,
    getCompetitionExecutionProgress,
    updateMatchData,
    updateMatchResults,
    uploadResultFile,
} from '@api/sdk.gen.ts'
import {
    Box,
    Button,
    Checkbox,
    Divider,
    Link,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
    useMediaQuery,
    useTheme,
} from '@mui/material'
import {competitionRoute, eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import {BaseSyntheticEvent, Fragment, useRef, useState} from 'react'
import LoadingButton from '@components/form/LoadingButton.tsx'
import {Controller, FormContainer, useFieldArray, useForm} from 'react-hook-form-mui'
import Throbber from '@components/Throbber.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {getFilename, groupBy, shuffle} from '@utils/helpers.ts'
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
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import WarningIcon from '@mui/icons-material/Warning'
import Info from '@mui/icons-material/Info'
import InlineLink from '@components/InlineLink.tsx'
import CompetitionExecutionRound from '@components/event/competition/excecution/CompetitionExecutionRound.tsx'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import BaseDialog from '@components/BaseDialog.tsx'
import StartListConfigPicker from '@components/event/competition/excecution/StartListConfigPicker.tsx'
import MatchResultUploadDialog from '@components/event/competition/excecution/MatchResultUploadDialog.tsx'

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
    failed: boolean
    failedReason: string
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

    const smallScreenLayout = useMediaQuery(`(max-width:${theme.breakpoints.values.md}px)`)

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

    const matchesFiltered = (round: CompetitionRoundDto): CompetitionMatchDto[] => {
        return round.matches
            .filter(match => match.teams.length > 0 && (match.teams.length > 1 || round.required))
            .sort((a, b) => a.executionOrder - b.executionOrder)
    }

    const currentRound = progressDto?.rounds[progressDto?.rounds.length - 1]
    const currentRoundMatches = currentRound ? matchesFiltered(currentRound) : undefined

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
                const validValues = values.filter(val => val.failed === false)
                const duplicatePlaces = Array.from(groupBy(validValues, val => val.place))
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

    const watchResultFields = resultsFormContext.watch('teamResults')

    const controlledResultFields = resultFields.map((field, index) => ({
        ...field,
        ...watchResultFields?.[index],
    }))

    const mapTeamDtoToFormTeamResults = (teams: CompetitionMatchTeamDto[]): EnterResultsTeam[] => {
        return teams
            .filter(t => !t.deregistered)
            .sort((a, b) => a.startNumber - b.startNumber)
            .map(team => ({
                registrationId: team.registrationId,
                place: team.place?.toString() ?? '',
                failed: team.failed,
                failedReason: team.failedReason ?? '',
            }))
    }

    const [startListMatch, setStartListMatch] = useState<string | null>(null)
    const showStartListConfigDialog = startListMatch !== null
    const closeStartListConfigDialog = () => setStartListMatch(null)

    const [resultImportMatch, setResultImportMatch] = useState<string | null>(null)
    const showMatchResultImportConfigDialog = resultImportMatch !== null
    const closeMatchResultImportConfigDialog = () => setResultImportMatch(null)

    const handleDownloadStartList = async (
        competitionMatchId: string,
        fileType: StartListFileType,
        config?: string,
    ) => {
        const {data, error, response} = await downloadStartList({
            path: {
                eventId,
                competitionId,
                competitionMatchId,
            },
            query: {
                fileType,
                config,
            },
        })
        const anchor = downloadRef.current

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
                getFilename(response) ?? `startList-${competitionMatchId}.${fileType.toLowerCase()}`
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    const handleUploadMatchResults = async (
        competitionMatchId: string,
        file: File,
        config: string,
    ) => {
        const {error} = await uploadResultFile({
            path: {
                eventId,
                competitionId,
                competitionMatchId,
            },
            body: {
                request: {config},
                files: [file],
            },
        })

        if (error) {
            if (error.status.value === 400) {
                if (error.errorCode === 'FILE_ERROR') {
                    feedback.error(t('event.competition.execution.results.error.FILE_ERROR'))
                } else if (error.message === 'Unsupported file type') {
                    // TODO: replace with error code!
                    feedback.error(t('common.error.upload.unsupportedType'))
                } else {
                    feedback.error(t('common.error.unexpected'))
                }
            } else if (error.status.value === 422) {
                const details = 'details' in error && error.details
                switch (error.errorCode) {
                    case 'SPREADSHEET_NO_HEADERS':
                        feedback.error(t('event.competition.execution.results.error.NO_HEADERS'))
                        break
                    case 'SPREADSHEET_COLUMN_UNKNOWN':
                        feedback.error(
                            t(
                                'event.competition.execution.results.error.COLUMN_UNKNOWN',
                                details as {expected: string},
                            ),
                        )
                        break
                    case 'SPREADSHEET_CELL_BLANK':
                        feedback.error(
                            t(
                                'event.competition.execution.results.error.CELL_BLANK',
                                details as {row: number; column: string},
                            ),
                        )
                        break
                    case 'SPREADSHEET_WRONG_CELL_TYPE':
                        feedback.error(
                            t(
                                'event.competition.execution.results.error.WRONG_CELL_TYPE',
                                details as {
                                    row: number
                                    column: string
                                    actual: string
                                    expected: string
                                },
                            ),
                        )
                        break
                    case 'WRONG_TEAM_COUNT':
                        feedback.error(
                            t(
                                'event.competition.execution.results.error.WRONG_TEAM_COUNT',
                                details as {actual: number; expected: number},
                            ),
                        )
                        break
                    case 'DUPLICATE_PLACES':
                        feedback.error(
                            t('event.competition.execution.results.error.DUPLICATE_PLACES'),
                        )
                        break
                    case 'DUPLICATE_START_NUMBERS':
                        feedback.error(
                            t('event.competition.execution.results.error.DUPLICATE_START_NUMBERS'),
                        )
                        break
                    case 'PLACES_UNCONTINUOUS':
                        feedback.error(
                            t(
                                'event.competition.execution.results.error.PLACES_UNCONTINUOUS',
                                details as {actual: number; expected: number},
                            ),
                        )
                        break
                    default:
                        feedback.error(t('common.error.unexpected'))
                        break
                }
            } else {
                feedback.error(t('common.error.unexpected'))
            }
        } else {
            feedback.success(t('event.competition.execution.results.submit.success'))
        }

        setReloadData(!reloadData)
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
                        place: results.failed ? undefined : Number(results.place),
                        failed: results.failed,
                        failedReason: results.failed
                            ? takeIfNotEmpty(results.failedReason)
                            : undefined,
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
            resultsFormContext.reset()
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
        const round = sortedRounds?.[roundIndex]
        const selectedMatch = round ? matchesFiltered(round)[matchIndex] : null
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
            editMatchFormContext.reset()
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
                        filteredMatches={matchesFiltered(round)}
                        reloadRoundDto={() => setReloadData(!reloadData)}
                        setSubmitting={setSubmitting}
                        submitting={submitting}
                        openResultsDialog={openResultsDialog}
                        openEditMatchDialog={openEditMatchDialog}
                        accordionsExpanded={accordionsOpen[roundIndex]}
                        handleAccordionExpandedChange={(accordionIndex, isExpanded) =>
                            handleAccordionExpandedChange({roundIndex, accordionIndex, isExpanded})
                        }
                        smallScreenLayout={smallScreenLayout}
                        setStartListMatch={setStartListMatch}
                        setResultImportMatch={setResultImportMatch}
                        handleDownloadStartListPDF={matchId =>
                            handleDownloadStartList(matchId, 'PDF')
                        }
                    />
                ))}
            </Stack>
            <BaseDialog
                open={resultsDialogOpen}
                maxWidth={'md'}
                onClose={closeResultsDialog}
                fullScreen={smallScreenLayout}>
                <Box sx={{[theme.breakpoints.up('md')]: {m: 2}}}>
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
                                                <TableCell width="10%">
                                                    {smallScreenLayout
                                                        ? t(
                                                              'event.competition.execution.match.startNumber.short',
                                                          )
                                                        : t(
                                                              'event.competition.execution.match.startNumber.startNumber',
                                                          )}
                                                </TableCell>
                                                <TableCell width="40%">
                                                    {t('event.competition.execution.match.team')}
                                                </TableCell>
                                                <TableCell width="40%">
                                                    {t('event.competition.execution.match.place')}
                                                </TableCell>
                                                <TableCell width="10%">
                                                    {t(
                                                        'event.competition.execution.results.failed',
                                                    )}
                                                </TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {controlledResultFields.map((value, fieldIndex) => (
                                                <Controller
                                                    key={value.id}
                                                    name={`teamResults.${fieldIndex}.failed`}
                                                    render={({
                                                        field: {
                                                            onChange: failedOnChange,
                                                            value: failedValue = false,
                                                        },
                                                    }) => (
                                                        <TableRow key={value.id}>
                                                            <TableCell width="10%">
                                                                {
                                                                    selectedResultsMatch.teams[
                                                                        fieldIndex
                                                                    ].startNumber
                                                                }
                                                            </TableCell>
                                                            <TableCell width="40%">
                                                                {`${selectedResultsMatch.teams[fieldIndex].clubName}` +
                                                                    (selectedResultsMatch.teams[
                                                                        fieldIndex
                                                                    ].name
                                                                        ? ` - ${selectedResultsMatch.teams[fieldIndex].name}`
                                                                        : '')}
                                                            </TableCell>
                                                            <TableCell width="40%">
                                                                {!failedValue ? (
                                                                    <Box sx={{maxWidth: 100}}>
                                                                        {controlledResultFields && (
                                                                            <FormInputNumber
                                                                                name={`teamResults.${fieldIndex}.place`}
                                                                                required
                                                                                min={1}
                                                                                max={
                                                                                    controlledResultFields.filter(
                                                                                        r =>
                                                                                            !r.failed,
                                                                                    ).length
                                                                                }
                                                                                integer
                                                                            />
                                                                        )}
                                                                    </Box>
                                                                ) : (
                                                                    <FormInputText
                                                                        name={`teamResults.${fieldIndex}.failedReason`}
                                                                        label={t(
                                                                            'event.competition.execution.results.failedReason',
                                                                        )}
                                                                    />
                                                                )}
                                                            </TableCell>
                                                            <TableCell width="10%">
                                                                <Checkbox
                                                                    checked={failedValue}
                                                                    onChange={e => {
                                                                        failedOnChange(e)
                                                                        resultsFormContext.setValue(
                                                                            `teamResults.${fieldIndex}.failed`,
                                                                            Boolean(
                                                                                e.target.checked,
                                                                            ),
                                                                        )
                                                                    }}
                                                                />
                                                            </TableCell>
                                                        </TableRow>
                                                    )}
                                                />
                                            ))}
                                        </TableBody>
                                    </Table>
                                </TableContainer>
                            </CompetitionExecutionMatchDialog>
                        )}
                    </FormContainer>
                </Box>
            </BaseDialog>
            <BaseDialog
                open={editMatchDialogOpen}
                maxWidth={'sm'}
                onClose={closeEditMatchDialog}
                fullScreen={smallScreenLayout}>
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
                                                        {smallScreenLayout
                                                            ? t(
                                                                  'event.competition.execution.match.startNumber.short',
                                                              )
                                                            : t(
                                                                  'event.competition.execution.match.startNumber.startNumber',
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
            </BaseDialog>
            <StartListConfigPicker
                open={showStartListConfigDialog}
                onClose={closeStartListConfigDialog}
                onSuccess={async config => handleDownloadStartList(startListMatch!, 'CSV', config)}
            />
            <MatchResultUploadDialog
                open={showMatchResultImportConfigDialog}
                onClose={closeMatchResultImportConfigDialog}
                onSuccess={async (config, file) =>
                    handleUploadMatchResults(resultImportMatch!, file, config)
                }
            />
            <Link ref={downloadRef} display={'none'}></Link>
        </Box>
    ) : (
        progressDtoPending && <Throbber />
    )
}
export default CompetitionExecution

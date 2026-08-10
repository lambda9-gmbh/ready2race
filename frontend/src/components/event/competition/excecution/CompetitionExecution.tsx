import {
    createNextCompetitionRound,
    downloadStartList,
    getTimingConfig,
    pullMatchResultsFromRaceClocker,
    resumeRaceClockerAutoPull,
    getCompetitionExecutionProgress,
    getEventSchedule,
    updateMatchData,
    updateMatchResults,
    uploadResultFile,
} from '@api/sdk.gen.ts'
import {
    Alert,
    AlertTitle,
    Box,
    Checkbox,
    Chip,
    Divider,
    InputAdornment,
    Link,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    ToggleButton,
    ToggleButtonGroup,
    Typography,
    useMediaQuery,
    useTheme,
} from '@mui/material'
import {competitionIndexRoute, competitionRoute, eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {Trans, useTranslation} from 'react-i18next'
import {BaseSyntheticEvent, Fragment, useEffect, useMemo, useRef, useState} from 'react'
import {format} from 'date-fns'
import LoadingButton from '@components/form/LoadingButton.tsx'
import {Controller, FormContainer, useFieldArray, useForm} from 'react-hook-form-mui'
import Throbber from '@components/Throbber.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {getFilename, groupBy, shuffle} from '@utils/helpers.ts'
import {
    formatFailedReason,
    MatchResultStatus,
    matchResultStatus,
    matchResultStatuses,
} from '@utils/matchResultStatus.ts'
import {
    CompetitionExecutionCanNotCreateRoundReason,
    CompetitionExecutionProgressDto,
    CompetitionMatchDto,
    CompetitionMatchTeamDto,
    StartListFileType,
} from '@api/types.gen.ts'
import CompetitionExecutionMatchDialog from '@components/event/competition/excecution/CompetitionExecutionMatchDialog.tsx'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import FormInputDateTime from '@components/form/input/FormInputDateTime.tsx'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import WarningIcon from '@mui/icons-material/Warning'
import TimerOutlinedIcon from '@mui/icons-material/TimerOutlined'
import MoreTimeOutlinedIcon from '@mui/icons-material/MoreTimeOutlined'
import EmojiEventsOutlinedIcon from '@mui/icons-material/EmojiEventsOutlined'
import SyncProblemIcon from '@mui/icons-material/SyncProblem'
import Info from '@mui/icons-material/Info'
import InlineLink from '@components/InlineLink.tsx'
import CompetitionExecutionRound from '@components/event/competition/excecution/CompetitionExecutionRound.tsx'
import RoundProgressionSetting from '@components/event/competition/excecution/RoundProgressionSetting.tsx'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import BaseDialog from '@components/BaseDialog.tsx'
import MatchResultUploadDialog from '@components/event/competition/excecution/MatchResultUploadDialog.tsx'
import FormInputTimecode from '@components/form/input/FormInputTimecode.tsx'
import {
    EditMatchForm,
    emptyEditMatchForm,
    mapMatchDtoToEditMatchForm,
} from '@components/event/competition/excecution/editMatchForm.ts'
import {
    mapDtoToTimingForm,
    timingConfigWarnings,
    effectiveTimingSystem,
} from '@components/event/competition/timing/timingConfigForm.ts'
import {
    ExecutionApiError,
    matchErrorText,
    raceClockerErrorText,
} from '@components/event/competition/excecution/executionError.ts'
import {raceableMatches} from '@components/event/competition/excecution/byeMatches.ts'
import {
    AutoRefreshConfig,
    refreshIntervalMs,
    syncStatus,
} from '@components/event/competition/excecution/autoRefresh.ts'

type EnterResultsTeam = {
    registrationId: string
    place: string
    timeString: string
    failed: boolean
    /** Kürzel und Notiz stehen im Formular getrennt, in der Datenbank zusammen in einem Feld. */
    failedStatus: MatchResultStatus | ''
    failedReason: string
    penaltySeconds: string
    penaltyNote: string
}
type EnterResultsForm = {
    selectedMatchDto: CompetitionMatchDto | null
    teamResults: EnterResultsTeam[]
}

const statusLabelKeys = {
    DNS: 'event.competition.execution.results.status.DNS',
    DNF: 'event.competition.execution.results.status.DNF',
    DSQ: 'event.competition.execution.results.status.DSQ',
} as const satisfies Record<MatchResultStatus, string>

type Props = {
    /**
     * Der automatische Abgleich, wie ihn die Veranstaltung vorgibt. Als Prop und nicht als eigener
     * Abruf: Die Seite über dieser hier hat die Veranstaltung ohnehin schon geladen, und eine
     * Einstellung, die sich am Renntag nicht ändert, verdient keinen zweiten Request.
     */
    autoRefresh: AutoRefreshConfig
}

const CompetitionExecution = ({autoRefresh}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const theme = useTheme()

    const downloadRef = useRef<HTMLAnchorElement>(null)

    const smallScreenLayout = useMediaQuery(`(max-width:${theme.breakpoints.values.md}px)`)

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const [submitting, setSubmitting] = useState(false)

    const [reloadData, setReloadData] = useState(false)

    // Die drei Dialoge stehen hier oben, weil der automatische Abgleich sie kennen muss: Solange
    // einer offen ist, ruht der Takt (siehe `paused` weiter unten). Ihre Öffnen/Schließen-Helfer
    // bleiben unten bei den Formularen, zu denen sie gehören.
    const [resultImportMatch, setResultImportMatch] = useState<string | null>(null)
    const [resultsDialogOpen, setResultsDialogOpen] = useState(false)
    const [editMatchDialogOpen, setEditMatchDialogOpen] = useState(false)

    // Läufe, deren Startzeit über den Zeitplan (Tab Zeitplan) gepflegt wird — für sie bleibt das
    // Startzeit-Feld hier read-only, damit die Kette (Task 10) nicht durch eine hier eingegebene
    // abweichende Zeit ausgehebelt wird. Events ohne Zeitstrahl liefern eine leere Slot-Liste,
    // dann bleibt das Feld wie bisher editierbar.
    const {data: eventSchedule} = useFetch(
        signal => getEventSchedule({signal, path: {eventId}}),
        {deps: [eventId]},
    )
    const slotManagedMatchIds = useMemo(
        () =>
            new Set(
                (eventSchedule?.slots ?? [])
                    .filter(slot => slot.matchId != null)
                    .map(slot => slot.matchId as string),
            ),
        [eventSchedule],
    )

    const {data: timingConfig} = useFetch(
        signal => getTimingConfig({signal, path: {eventId, competitionId}}),
        {deps: [eventId, competitionId]},
    )

    // Dieselbe Prüfung wie im Zeitnahme-Tab, damit beide Stellen nicht auseinanderlaufen.
    const timingWarnings = timingConfig ? timingConfigWarnings(mapDtoToTimingForm(timingConfig)) : []

    /**
     * Der zuletzt erfolgreich geladene Stand. Er liegt hier und nicht in `useFetch`, weil der
     * dortige Fehlerzweig die Daten auf `null` setzt — bei laufendem Abgleich hieße das: ein
     * WLAN-Aussetzer am Steg räumt die Seite leer. Gehalten wird stattdessen der letzte gute
     * Stand, daneben steht der Verbindungshinweis. Dasselbe Vorgehen wie im
     * Schiedsrichter-Board (LiveDashboardPage).
     */
    const [progressDto, setProgressDto] = useState<CompetitionExecutionProgressDto | null>(null)
    /** Wann sich dieser Stand zuletzt geändert hat — nicht, wann zuletzt gefragt wurde. */
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
    /** Fehlgeschlagene Abrufe seit dem letzten erfolgreichen — 0 heißt: Verbindung steht. */
    const [failures, setFailures] = useState(0)
    /** Stand der letzten Antwort; spart den Rumpf, solange sich nichts geändert hat. */
    const etagRef = useRef<string | null>(null)

    // Solange ein Dialog offen ist, ruht der Takt: Ein Abgleich würde unter der Hand die Liste
    // verschieben, auf der die Eingabe gerade steht. Das Umschalten hängt im Abruf an den `deps`,
    // also holt das Schließen den aktuellen Stand sofort nach — genau dann, wenn der
    // Schiedsrichter wieder auf die Liste schaut. Beim Öffnen kostet es einen Abruf; der bringt
    // die frischesten Daten in den Dialog und ist damit keiner zu viel.
    const anyDialogOpen = resultsDialogOpen || editMatchDialogOpen || resultImportMatch !== null
    const autoReloadInterval = refreshIntervalMs(autoRefresh, anyDialogOpen)

    const {pending: progressDtoPending, reload: reloadProgress} = useFetch(
        signal =>
            getCompetitionExecutionProgress({
                signal,
                path: {
                    eventId: eventId,
                    competitionId: competitionId,
                },
                // Unverändert? Dann antwortet der Server mit 304 und ohne Rumpf, und die Seite
                // rührt ihren State nicht an. 'no-store' hält den Browser-Cache aus der Bedingung
                // heraus, sonst beantwortet er sie selbst.
                headers: etagRef.current ? {'If-None-Match': etagRef.current} : undefined,
                cache: 'no-store',
            }),
        {
            autoReloadInterval,
            onResponse: ({data, error, response}) => {
                if (response.status === 304) {
                    // Unverändert: Hier wird bewusst nichts gesetzt außer dem Verbindungszustand,
                    // und der nur, wenn er sich unterscheidet — React verwirft ein useState mit
                    // demselben Wert. Im Ruhezustand rendert die Seite also gar nicht neu, und
                    // Scrollposition wie aufgeklappte Runden bleiben, wo sie sind. Auch die
                    // Uhrzeit unten bleibt stehen: Sie sagt, von wann die Daten sind, nicht wann
                    // zuletzt jemand nachgefragt hat.
                    setFailures(0)
                    return
                }
                if (error !== undefined || data === undefined) {
                    // Kein Snackbar im Takt: Bei stehendem Netz stünde alle fünf Sekunden einer
                    // auf dem Schirm. Beim ersten Laden gibt es ihn weiter, da ist er die einzige
                    // Rückmeldung.
                    if (autoReloadInterval === undefined) {
                        feedback.error(t('event.competition.execution.progress.error'))
                    }
                    setFailures(prev => prev + 1)
                    return
                }
                etagRef.current = response.headers.get('ETag')
                setProgressDto(data)
                setLastUpdated(new Date())
                setFailures(0)
                handleAccordionExpandedChange()
            },
            // Ein abgebrochener Request (Seitenwechsel) landet hier nicht — useFetch prüft das
            // Abort-Signal. Was hier ankommt, ist ein echter Netzfehler.
            onPanic: () => {
                setFailures(prev => prev + 1)
            },
            deps: [eventId, competitionId, reloadData, autoReloadInterval],
        },
    )

    // Zurück im Netz: nicht bis zum nächsten Takt warten. Der Browser meldet das selbst, und beim
    // Abgleich im Minutentakt ist das der Unterschied zwischen "sofort" und "irgendwann".
    useEffect(() => {
        window.addEventListener('online', reloadProgress)
        return () => window.removeEventListener('online', reloadProgress)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const connection = syncStatus({failures, hasData: progressDto !== null})
    /**
     * Warum die Ergebnis- oder Laufdaten-Eingabe abgelehnt wurde. [fallbackKey] ist die bisherige
     * Sammelmeldung der jeweiligen Maske und greift nur noch für Gründe ohne eigenen Code.
     */
    const showMatchError = (
        error: ExecutionApiError,
        fallbackKey:
            | 'event.competition.execution.results.submit.error'
            | 'event.competition.execution.matchData.submit.error',
    ) => {
        const text = matchErrorText(error)
        feedback.error(text === undefined ? t(fallbackKey) : t(text.key, text.values))
    }

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

    const currentRound = progressDto?.rounds[progressDto?.rounds.length - 1]
    const currentRoundMatches = currentRound ? raceableMatches(currentRound) : undefined

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
                    .filter(([val, items]) => items.length > 1 && val !== '')
                    .map(([place]) => place)
                // Teilergebnisse sind erlaubt: Zeilen ohne Platz und ohne Zeit bleiben offen und
                // werden nicht übertragen. Nur eine komplett leere Eingabe ist sinnlos.
                const neitherPlaceNorTimeFilled =
                    validValues.length > 0 &&
                    validValues.every(val => val.place === '' && val.timeString === '')

                if (neitherPlaceNorTimeFilled) {
                    setTeamResultsError(
                        t('event.competition.execution.results.validation.noResultsEntered'),
                    )
                    return 'noResultsEntered'
                } else if (duplicatePlaces.length > 0) {
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
            .map(team => {
                const {status, note} = matchResultStatus(team.failedReason)

                return {
                    registrationId: team.registrationId,
                    place: team.place?.toString() ?? '',
                    timeString: team.timeString?.toString() ?? '',
                    failed: team.failed,
                    failedStatus: status ?? '',
                    failedReason: note ?? '',
                    penaltySeconds: team.penaltySeconds?.toString() ?? '',
                    penaltyNote: team.penaltyNote ?? '',
                }
            })
    }

    const showMatchResultImportConfigDialog = resultImportMatch !== null
    const closeMatchResultImportConfigDialog = () => setResultImportMatch(null)

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

        if (error) {
            if (error.status.value === 409) {
                feedback.error(t('event.competition.execution.startList.error.missingStartTime'))
            } else if (
                error.status.value === 400 &&
                error.errorCode === 'STARTLIST_CONFIG_NOT_CONFIGURED'
            ) {
                feedback.error(t('event.competition.execution.startList.error.notConfigured'))
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

    const handlePullRaceClockerResults = async (competitionMatchId: string) => {
        setSubmitting(true)
        const {error} = await pullMatchResultsFromRaceClocker({
            path: {
                eventId,
                competitionId,
                competitionMatchId,
            },
        })
        setSubmitting(false)

        if (error) {
            const text = raceClockerErrorText(error)
            feedback.error(text === undefined ? t('common.error.unexpected') : t(text.key, text.values))
        } else {
            feedback.success(t('event.competition.execution.results.raceclocker.success'))
            setReloadData(!reloadData)
        }
    }

    const handleResumeRaceClockerAutoPull = async (competitionMatchId: string) => {
        setSubmitting(true)
        const {error} = await resumeRaceClockerAutoPull({
            path: {eventId, competitionId, competitionMatchId},
        })
        setSubmitting(false)

        if (error) {
            feedback.error(t('common.error.unexpected'))
        } else {
            feedback.success(t('event.competition.execution.results.raceclocker.poll.resumed'))
            setReloadData(!reloadData)
        }
    }

    const handleUploadMatchResults = async (competitionMatchId: string, file: File) => {
        const {error} = await uploadResultFile({
            path: {
                eventId,
                competitionId,
                competitionMatchId,
            },
            body: {
                files: [file],
            },
        })

        if (error) {
            if (error.status.value === 400) {
                if (error.errorCode === 'FILE_ERROR') {
                    feedback.error(t('common.error.upload.FILE_ERROR'))
                } else if (error.errorCode === 'RESULT_IMPORT_CONFIG_NOT_CONFIGURED') {
                    feedback.error(t('event.competition.execution.results.error.notConfigured'))
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
                        feedback.error(t('common.error.upload.NO_HEADERS'))
                        break
                    case 'SPREADSHEET_COLUMN_UNKNOWN':
                        feedback.error(
                            t('common.error.upload.COLUMN_UNKNOWN', details as {expected: string}),
                        )
                        break
                    case 'SPREADSHEET_CELL_BLANK':
                        feedback.error(
                            t(
                                'common.error.upload.CELL_BLANK',
                                details as {row: number; column: string},
                            ),
                        )
                        break
                    case 'SPREADSHEET_WRONG_CELL_TYPE':
                        feedback.error(
                            t(
                                'common.error.upload.WRONG_CELL_TYPE',
                                details as {
                                    row: number
                                    column: string
                                    actual: string
                                    expected: string
                                },
                            ),
                        )
                        break
                    case 'SPREADSHEET_UNPARSABLE_STRING':
                        feedback.error(
                            t(
                                'common.error.upload.UNPARSABLE_STRING',
                                details as {
                                    row: number
                                    column: string
                                    value: string
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
                    case 'DUPLICATE_TEAMS':
                        feedback.error(
                            t('event.competition.execution.results.error.DUPLICATE_TEAMS'),
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
                    case 'LIST_DATA_INCOMPLETE':
                        feedback.error(
                            t('event.competition.execution.results.error.LIST_DATA_INCOMPLETE'),
                        )
                        break
                    case 'RESULT_NOT_FAILED_AND_NO_DATA':
                        feedback.error(
                            t(
                                'event.competition.execution.results.error.RESULT_NOT_FAILED_AND_NO_DATA',
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
        setTeamResultsError(null)
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
                    // Offene Zeilen (kein Platz, keine Zeit, nicht ausgeschieden) bleiben ohne
                    // Ergebnis: das Backend verlangt je übertragenem Team Platz, Zeit oder Grund.
                    teamResults: formData.teamResults
                        .filter(
                            results =>
                                results.failed ||
                                takeIfNotEmpty(results.place) !== undefined ||
                                takeIfNotEmpty(results.timeString) !== undefined,
                        )
                        .map(results => ({
                            registrationId: results.registrationId,
                            place:
                                results.failed || !results.place
                                    ? undefined
                                    : Number(results.place),
                            timeString: results.failed
                                ? undefined
                                : takeIfNotEmpty(results.timeString),
                            failed: results.failed,
                            failedReason: results.failed
                                ? (formatFailedReason(
                                      results.failedStatus || null,
                                      results.failedReason,
                                  ) ?? undefined)
                                : undefined,
                            penaltySeconds:
                                takeIfNotEmpty(results.penaltySeconds) !== undefined
                                    ? Number(results.penaltySeconds)
                                    : undefined,
                            penaltyNote: takeIfNotEmpty(results.penaltyNote),
                        })),
                },
            })
            if (error) {
                showMatchError(error, 'event.competition.execution.results.submit.error')
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
        defaultValues: emptyEditMatchForm,
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

    const openEditMatchDialog = (roundIndex: number, matchIndex: number) => {
        const round = sortedRounds?.[roundIndex]
        const selectedMatch = round ? raceableMatches(round)[matchIndex] : null
        if (selectedMatch) {
            setEditMatchDialogOpen(true)
            editMatchFormContext.reset(mapMatchDtoToEditMatchForm(selectedMatch))
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
                showMatchError(error, 'event.competition.execution.matchData.submit.error')
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
                editMatchFormContext.reset(mapMatchDtoToEditMatchForm(nextMatch))
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
            {/* Der Abgleich meldet sich nur, wenn er läuft — bei abgeschalteter Automatik hätte
                eine Uhrzeit hier nichts zu sagen. Rechtsbündig und klein: Der Hinweis soll
                auffallen, wenn die Verbindung weg ist, und sonst nicht im Weg stehen. */}
            {autoRefresh.enabled && (lastUpdated !== null || connection === 'stale') && (
                <Stack
                    direction={'row'}
                    spacing={1}
                    alignItems={'center'}
                    justifyContent={'flex-end'}
                    sx={{mt: 1}}>
                    {connection === 'stale' && (
                        <Chip
                            size={'small'}
                            variant={'outlined'}
                            color={'warning'}
                            icon={<SyncProblemIcon />}
                            label={t('event.competition.execution.autoRefresh.disconnected')}
                        />
                    )}
                    {lastUpdated && (
                        <Typography variant={'caption'} color={'text.secondary'} noWrap>
                            {t('event.competition.execution.autoRefresh.lastUpdated', {
                                time: format(lastUpdated, t('format.timeWithSeconds')),
                            })}
                        </Typography>
                    )}
                </Stack>
            )}
            {timingWarnings.length > 0 && (
                <Alert variant={'outlined'} severity={'warning'} sx={{my: 2}}>
                    <AlertTitle>
                        <Trans i18nKey={'event.competition.timing.incomplete.title'} />
                    </AlertTitle>
                    {timingWarnings.map(warning => (
                        <Typography key={warning}>
                            {t(`event.competition.timing.incomplete.${warning}`)}
                        </Typography>
                    ))}
                    <InlineLink from={competitionIndexRoute.fullPath} search={{tab: 'timing'}}>
                        <Trans i18nKey={'event.competition.timing.incomplete.link'} />
                    </InlineLink>
                </Alert>
            )}
            {!allRoundsCreated && (
                <Box
                    sx={{
                        my: 2,
                        display: 'flex',
                        alignItems: 'center',
                        flexWrap: 'wrap',
                        gap: 2,
                    }}>
                    <Box sx={{display: 'flex', alignItems: 'center'}}>
                        <LoadingButton
                            pending={submitting}
                            disabled={progressDto.canNotCreateRoundReasons.length > 0}
                            variant={'contained'}
                            onClick={handleCreateNextRound}>
                            {t('event.competition.execution.nextRound.create')}
                        </LoadingButton>
                        {progressDto.canNotCreateRoundReasons.length > 0 && (
                            <HtmlTooltip
                                placement={'right'}
                                title={
                                    <Stack spacing={1} p={1}>
                                        {progressDto.canNotCreateRoundReasons.map(
                                            (reason, idx) => (
                                                <Fragment key={reason}>
                                                    <Stack direction={'row'} spacing={1}>
                                                        <WarningIcon color={'warning'} />
                                                        <Typography>
                                                            {getReasonText(reason)}
                                                        </Typography>
                                                    </Stack>
                                                    {idx <
                                                        progressDto.canNotCreateRoundReasons
                                                            .length -
                                                            1 && <Divider />}
                                                </Fragment>
                                            ),
                                        )}
                                    </Stack>
                                }>
                                <Info sx={{ml: 1}} color={'info'} fontSize={'small'} />
                            </HtmlTooltip>
                        )}
                    </Box>
                    <RoundProgressionSetting />
                </Box>
            )}
            <Stack spacing={6}>
                {sortedRounds.map((round, roundIndex) => (
                    <CompetitionExecutionRound
                        key={round.setupRoundId}
                        round={round}
                        roundIndex={roundIndex}
                        filteredMatches={raceableMatches(round)}
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
                        setResultImportMatch={setResultImportMatch}
                        pullRaceClockerResults={handlePullRaceClockerResults}
                        resumeRaceClockerAutoPull={handleResumeRaceClockerAutoPull}
                        handleDownloadStartListPDF={matchId =>
                            handleDownloadStartList(matchId, 'PDF')
                        }
                        handleDownloadStartListCSV={matchId =>
                            handleDownloadStartList(matchId, 'CSV')
                        }
                        /* Das effektive System, nicht die eigene Spalte des Wettkampfs: Setzt die
                           Veranstaltung RaceClocker und erben ihre Wettkämpfe es, ist
                           `timingConfig.timingSystem` null — der Abruf-Status samt „Automatik wieder
                           aufnehmen" verschwände dann genau dort, wo die Automatik läuft. Dieselbe
                           Auflösung wie bei den Warnungen oben. */
                        timingSystem={
                            timingConfig
                                ? effectiveTimingSystem(mapDtoToTimingForm(timingConfig))
                                : 'NONE'
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
                    <FormContainer
                        FormProps={{style: {display: 'contents'}}}
                        formContext={resultsFormContext}
                        onSuccess={onSubmitResults}>
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
                                                <TableCell width="34%">
                                                    {t('event.competition.execution.match.team')}
                                                </TableCell>
                                                <TableCell width="46%">
                                                    {t(
                                                        'event.competition.execution.match.placeAndTime',
                                                    )}
                                                </TableCell>
                                                <TableCell width="10%">
                                                    {t(
                                                        'event.competition.execution.results.failed',
                                                    )}
                                                </TableCell>
                                            </TableRow>
                                        </TableHead>
                                        <TableBody>
                                            {controlledResultFields.map((value, fieldIndex) => {
                                                const team = selectedResultsMatch.teams.find(
                                                    val =>
                                                        val.registrationId === value.registrationId,
                                                )!

                                                return (
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
                                                                    {team.startNumber}
                                                                </TableCell>
                                                                <TableCell width="34%">
                                                                    <Typography>
                                                                        {team.actualClubName ??
                                                                            team.clubName}
                                                                    </Typography>
                                                                    <Typography
                                                                        color={'textSecondary'}
                                                                        variant={'body2'}>
                                                                        {`${t('club.registeredBy')} ` +
                                                                            team.clubName +
                                                                            ` | ${team.name}`}
                                                                    </Typography>
                                                                    <Typography
                                                                        color={'textSecondary'}
                                                                        variant={'body2'}>
                                                                        {team.namedParticipants
                                                                            .map(np =>
                                                                                np.participants
                                                                                    .map(
                                                                                        p =>
                                                                                            `${p.firstName} ${p.lastName}`,
                                                                                    )
                                                                                    .join(', '),
                                                                            )
                                                                            .join(', ')}
                                                                    </Typography>
                                                                </TableCell>
                                                                <TableCell width="46%">
                                                                    {!failedValue ? (
                                                                        <Box
                                                                            sx={{
                                                                                width: 1,
                                                                                display: 'flex',
                                                                                flexDirection:
                                                                                    'column',
                                                                                gap: 2,
                                                                            }}>
                                                                            {controlledResultFields && (
                                                                                <Box
                                                                                    display="flex"
                                                                                    gap={1}
                                                                                    alignItems={
                                                                                        'center'
                                                                                    }>
                                                                                    <EmojiEventsOutlinedIcon
                                                                                        color={
                                                                                            'action'
                                                                                        }
                                                                                    />
                                                                                    <FormInputNumber
                                                                                        name={`teamResults.${fieldIndex}.place`}
                                                                                        min={1}
                                                                                        max={
                                                                                            controlledResultFields.filter(
                                                                                                r =>
                                                                                                    !r.failed,
                                                                                            ).length
                                                                                        }
                                                                                        integer
                                                                                        size="small"
                                                                                        placeholder="#"
                                                                                    />
                                                                                </Box>
                                                                            )}
                                                                            <Box
                                                                                display="flex"
                                                                                gap={1}
                                                                                alignItems={
                                                                                    'center'
                                                                                }>
                                                                                <TimerOutlinedIcon
                                                                                    color={'action'}
                                                                                />
                                                                                <FormInputTimecode
                                                                                    name={`teamResults.${fieldIndex}.timeString`}
                                                                                    size="small"
                                                                                    placeholder="00:00:00.000"
                                                                                />
                                                                            </Box>
                                                                            {/* Wie Platz und Zeit ohne Label: das "– Optional" der
                                                                                Labels sprengt diese enge Spalte. */}
                                                                            <Box
                                                                                display="flex"
                                                                                gap={1}
                                                                                alignItems={
                                                                                    'center'
                                                                                }>
                                                                                <MoreTimeOutlinedIcon
                                                                                    color={'action'}
                                                                                    titleAccess={t(
                                                                                        'event.competition.execution.results.penaltySeconds',
                                                                                    )}
                                                                                />
                                                                                <FormInputNumber
                                                                                    name={`teamResults.${fieldIndex}.penaltySeconds`}
                                                                                    min={1}
                                                                                    integer
                                                                                    size="small"
                                                                                    placeholder={t(
                                                                                        'event.competition.execution.results.penaltyShort',
                                                                                    )}
                                                                                    slotProps={{
                                                                                        input: {
                                                                                            endAdornment: (
                                                                                                <InputAdornment
                                                                                                    position={
                                                                                                        'end'
                                                                                                    }>
                                                                                                    {t(
                                                                                                        'common.form.secondsShort',
                                                                                                    )}
                                                                                                </InputAdornment>
                                                                                            ),
                                                                                        },
                                                                                    }}
                                                                                    sx={{width: 116}}
                                                                                />
                                                                                <FormInputText
                                                                                    name={`teamResults.${fieldIndex}.penaltyNote`}
                                                                                    size="small"
                                                                                    placeholder={t(
                                                                                        'event.competition.execution.results.penaltyNoteShort',
                                                                                    )}
                                                                                    sx={{flex: 1}}
                                                                                />
                                                                            </Box>
                                                                        </Box>
                                                                    ) : (
                                                                        <Box
                                                                            sx={{
                                                                                display: 'flex',
                                                                                flexDirection:
                                                                                    'column',
                                                                                gap: 2,
                                                                            }}>
                                                                            {/* Die Kürzel bleiben in jeder Sprache gleich; was sie
                                                                                bedeuten, steht im Tooltip. */}
                                                                            <Controller
                                                                                name={`teamResults.${fieldIndex}.failedStatus`}
                                                                                render={({
                                                                                    field: {
                                                                                        onChange:
                                                                                            statusOnChange,
                                                                                        value: statusValue,
                                                                                    },
                                                                                }) => (
                                                                                    <ToggleButtonGroup
                                                                                        exclusive
                                                                                        size="small"
                                                                                        value={
                                                                                            statusValue ||
                                                                                            null
                                                                                        }
                                                                                        onChange={(
                                                                                            _,
                                                                                            next,
                                                                                        ) =>
                                                                                            statusOnChange(
                                                                                                next ??
                                                                                                    '',
                                                                                            )
                                                                                        }>
                                                                                        {matchResultStatuses.map(
                                                                                            status => (
                                                                                                <ToggleButton
                                                                                                    key={
                                                                                                        status
                                                                                                    }
                                                                                                    value={
                                                                                                        status
                                                                                                    }
                                                                                                    title={t(
                                                                                                        statusLabelKeys[
                                                                                                            status
                                                                                                        ],
                                                                                                    )}>
                                                                                                    {
                                                                                                        status
                                                                                                    }
                                                                                                </ToggleButton>
                                                                                            ),
                                                                                        )}
                                                                                    </ToggleButtonGroup>
                                                                                )}
                                                                            />
                                                                            <FormInputText
                                                                                name={`teamResults.${fieldIndex}.failedReason`}
                                                                                label={t(
                                                                                    'event.competition.execution.results.failedNote',
                                                                                )}
                                                                                size="small"
                                                                            />
                                                                        </Box>
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
                                                                                    e.target
                                                                                        .checked,
                                                                                ),
                                                                            )
                                                                        }}
                                                                    />
                                                                </TableCell>
                                                            </TableRow>
                                                        )}
                                                    />
                                                )
                                            })}
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
                                fieldArrayError={teamEditMatchError}
                                submitting={submitting}
                                closeDialog={closeEditMatchDialog}
                                saveAndNext={
                                    currentRoundMatches.length >
                                    selectedMatchIndex(selectedEditMatch) + 1
                                }>
                                {slotManagedMatchIds.has(selectedEditMatch.id) ? (
                                    <Box sx={{mb: 2}}>
                                        <Typography sx={{fontSize: '1.1rem', mb: 1}}>
                                            {t('event.competition.execution.match.startTime')}
                                        </Typography>
                                        <Typography>
                                            {selectedEditMatch.startTime
                                                ? format(
                                                      new Date(selectedEditMatch.startTime),
                                                      t('format.datetime'),
                                                  )
                                                : '—'}
                                        </Typography>
                                        <Typography
                                            variant="body2"
                                            color="textSecondary"
                                            sx={{mt: 0.5}}>
                                            {t('event.schedule.managedHint')}
                                        </Typography>
                                    </Box>
                                ) : (
                                    <FormInputDateTime
                                        name={'startTime'}
                                        label={t('event.competition.execution.match.startTime')}
                                        timeSteps={{minutes: 1}}
                                    />
                                )}
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
                                                {editMatchFields.map((value, fieldIndex) => {
                                                    const team = selectedEditMatch.teams.find(
                                                        val =>
                                                            val.registrationId ===
                                                            value.registrationId,
                                                    )!

                                                    return (
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
                                                                <Typography>
                                                                    {team.actualClubName ??
                                                                        team.clubName}
                                                                </Typography>
                                                                <Typography
                                                                    color={'textSecondary'}
                                                                    variant={'body2'}>
                                                                    {`${t('club.registeredBy')} ` +
                                                                        team.clubName +
                                                                        ` | ${team.name}`}
                                                                </Typography>
                                                                <Typography
                                                                    color={'textSecondary'}
                                                                    variant={'body2'}>
                                                                    {team.namedParticipants
                                                                        .map(np =>
                                                                            np.participants
                                                                                .map(
                                                                                    p =>
                                                                                        `${p.firstName} ${p.lastName}`,
                                                                                )
                                                                                .join(', '),
                                                                        )
                                                                        .join(', ')}
                                                                </Typography>
                                                            </TableCell>
                                                        </TableRow>
                                                    )
                                                })}
                                            </TableBody>
                                        </Table>
                                    </TableContainer>
                                </Box>
                            </CompetitionExecutionMatchDialog>
                        )}
                    </FormContainer>
                </Box>
            </BaseDialog>
            <MatchResultUploadDialog
                open={showMatchResultImportConfigDialog}
                onClose={closeMatchResultImportConfigDialog}
                onSuccess={async file => handleUploadMatchResults(resultImportMatch!, file)}
            />
            <Link ref={downloadRef} display={'none'}></Link>
        </Box>
    ) : connection === 'error' ? (
        // Nie etwas angekommen: Hier gibt es keinen letzten guten Stand zu halten, also sagt es
        // die Seite deutlich statt endlos zu drehen.
        <Alert severity={'error'} sx={{my: 2}}>
            {t('event.competition.execution.progress.error')}
        </Alert>
    ) : (
        progressDtoPending && <Throbber />
    )
}
export default CompetitionExecution

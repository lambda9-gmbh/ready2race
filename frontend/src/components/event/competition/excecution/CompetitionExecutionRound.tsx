import {
    CompetitionMatchDto,
    CompetitionRoundDto,
    StartListFileType,
} from '@api/types.gen.ts'
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Box,
    Card,
    Chip,
    Divider,
    FormControlLabel,
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
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import Substitutions from '@components/event/competition/excecution/Substitutions.tsx'
import LoadingButton from '@components/form/LoadingButton.tsx'
import {useTranslation} from 'react-i18next'
import {useFeedback} from '@utils/hooks.ts'
import {Dispatch, Fragment, SetStateAction, SyntheticEvent, useEffect, useState} from 'react'
import {
    deleteCurrentCompetitionExecutionRound,
    skipScheduleRound,
    updateMatchActivation,
} from '@api/sdk.gen.ts'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {competitionRoute, eventRoute} from '@routes'
import SelectionMenu from '@components/SelectionMenu.tsx'
import {format} from 'date-fns'
import Checkbox from '@mui/material/Checkbox'
import {failedLabel} from '@utils/matchResultStatus.ts'
import {roundHasNothingToRace} from '@components/event/competition/excecution/roundCancellation.ts'
import {matchesOnDisplay} from '@components/event/competition/excecution/roundDeletion.ts'
import {roundSkipErrorText} from '@components/event/schedule/scheduleError.ts'
import {MatchResultOption, matchResultOptions} from './matchResultOptions.ts'
import {raceClockerPollStatus} from './raceClockerPollStatus.ts'
import {TimingFormSystem} from '@components/event/competition/timing/timingConfigForm.ts'
import {
    MatchChip,
    arenaChip,
    matchStatusChip,
    roundCounterChips,
} from '@components/event/match/matchStatusChip.ts'

/**
 * Uhr für die verstrichenen Minuten auf den Status-Chips ("Läuft · 4 min", "Überfällig · 8 min").
 * Die Durchführungsseite lädt nur auf Anforderung nach; ohne eigene Uhr stünde die Zahl auf dem
 * Chip still und behauptete nach einer Viertelstunde noch immer "Läuft · 1 min". 30 Sekunden
 * reichen für eine Minutenangabe.
 */
const useNow = (intervalMs = 30_000): Date => {
    const [now, setNow] = useState(() => new Date())
    useEffect(() => {
        const id = window.setInterval(() => setNow(new Date()), intervalMs)
        return () => window.clearInterval(id)
    }, [intervalMs])
    return now
}

/**
 * Ein [MatchChip] als MUI-Chip. Welcher Chip es ist, entscheidet ausschließlich
 * `matchStatusChip.ts` — hier wird nur noch übersetzt und gemalt.
 */
const StatusChip = ({chip}: {chip: MatchChip | null}) => {
    const {t} = useTranslation()
    // Der Schlüssel steht erst zur Laufzeit fest, deshalb die gelockerte Signatur - dasselbe
    // Muster wie `stateChipProps` in EventSchedule.tsx.
    const translate = t as (key: string, values?: Record<string, string | number>) => string
    // null heißt "dieser Chip sagt hier nichts aus" (z.B. der Arena-Chip ohne erhobene
    // Check-in-Daten) - dann gar nichts zeigen, statt eine leere Hülle.
    if (!chip) return null
    return (
        <Chip
            size={'small'}
            label={translate(chip.labelKey, chip.values)}
            color={chip.color}
            sx={chip.strikeThrough ? {textDecoration: 'line-through'} : undefined}
        />
    )
}

type Props = {
    round: CompetitionRoundDto
    roundIndex: number
    filteredMatches: CompetitionMatchDto[]
    reloadRoundDto: () => void
    setSubmitting: (value: boolean) => void
    submitting: boolean
    openResultsDialog: (matchIndex: number) => void
    openEditMatchDialog: (roundIndex: number, matchIndex: number) => void
    accordionsExpanded: boolean[] | undefined
    handleAccordionExpandedChange: (accordionIndex: number, isExpanded: boolean) => void
    smallScreenLayout: boolean
    setResultImportMatch: Dispatch<SetStateAction<string | null>>
    pullRaceClockerResults: (competitionMatchId: string) => Promise<void>
    resumeRaceClockerAutoPull: (competitionMatchId: string) => Promise<void>
    handleDownloadStartListPDF: (competitionMatchId: string) => Promise<void>
    handleDownloadStartListCSV: (competitionMatchId: string) => Promise<void>
    /**
     * Das EFFEKTIVE Zeitnahmesystem des Wettkampfs (`effectiveTimingSystem`), also einschließlich
     * dessen, was er von der Veranstaltung erbt — nicht seine eigene Spalte. Daran hängt unter
     * anderem der Knopf „Automatik wieder aufnehmen"; mit dem lokalen Wert verschwände er bei jedem
     * Wettkampf, der RaceClocker erbt, und der pausierte Lauf ließe sich nirgends mehr freigeben.
     */
    timingSystem: TimingFormSystem
}

const CompetitionExecutionRound = ({
    round,
    roundIndex,
    filteredMatches,
    submitting,
    smallScreenLayout,
    setResultImportMatch,
    pullRaceClockerResults,
    resumeRaceClockerAutoPull,
    handleDownloadStartListPDF,
    handleDownloadStartListCSV,
    timingSystem,
    ...props
}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const theme = useTheme()
    const now = useNow()

    // Die Zählerleiste fasst zusammen, was die Chips darunter einzeln sagen — bei einer einzigen
    // Lauf-Karte wäre das bloße Wiederholung, deshalb bleibt sie dort leer (siehe roundCounterChips).
    const counterChips = roundCounterChips(filteredMatches.map(match => match.status))

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const {confirmAction} = useConfirmation()

    // Wie viele Läufe dieser Runde draußen schon zu sehen sind. Löschen bleibt erlaubt — das
    // Regattabüro muss auch eine gefahrene Runde zurücknehmen können —, aber es soll wissen, dass
    // es damit etwas wegräumt, das am Steg oder in den Ergebnissen bereits jemand gesehen hat.
    const onDisplay = matchesOnDisplay(round.matches, round.required)

    const deleteCurrentRound = async () => {
        confirmAction(
            async () => {
                props.setSubmitting(true)
                const {error} = await deleteCurrentCompetitionExecutionRound({
                    path: {
                        eventId: eventId,
                        competitionId: competitionId,
                    },
                })
                props.setSubmitting(false)
                if (error) {
                    feedback.error(t('event.competition.execution.deleteRound.error'))
                } else {
                    feedback.success(t('event.competition.execution.deleteRound.success'))
                }
                props.reloadRoundDto()
            },
            {
                // Gewarnt wird über `round.matches` und nicht über `filteredMatches`: Gelöscht wird
                // die ganze Runde, nicht der gerade angezeigte Ausschnitt.
                content: onDisplay
                    ? t('event.competition.execution.deleteRound.confirmation.onDisplay', {
                          count: onDisplay,
                      })
                    : t('event.competition.execution.deleteRound.confirmation.content'),
                okText: t('common.delete'),
            },
        )
    }

    // Runde entfällt (Wettkampf → Durchführung, verschoben aus dem Zeitplan): nur anbieten, wenn es
    // in der Runde nichts zu fahren gibt (siehe roundHasNothingToRace) - sonst müssen die Läufe
    // ausgetragen werden, damit die nächste Runde sauber ausgelost werden kann. Ruft denselben
    // Endpunkt wie der frühere Zeitplan-Button (skipScheduleRound); der Server prüft die Regel
    // ohnehin noch einmal serverseitig (EventScheduleService.setRoundSkipped).
    const cancelRound = async () => {
        confirmAction(
            async () => {
                props.setSubmitting(true)
                const {error} = await skipScheduleRound({
                    path: {
                        eventId: eventId,
                        setupRoundId: round.setupRoundId,
                    },
                })
                props.setSubmitting(false)
                if (error) {
                    // "Runde noch nicht gesetzt" und "in der Runde ist noch zu fahren" verlangen
                    // Gegensätzliches; bis zuletzt lasen beide denselben Satz.
                    const {key, values} = roundSkipErrorText(error)
                    feedback.error(t(key, values))
                } else {
                    feedback.success(t('event.competition.execution.cancelRound.success'))
                }
                props.reloadRoundDto()
            },
            {
                content: t('event.competition.execution.cancelRound.confirmation.content', {
                    round: round.name,
                }),
                okText: t('event.competition.execution.cancelRound.confirmation.ok'),
            },
        )
    }

    const handleAccordionExpandedChange =
        (accordionIndex: number) => (_: SyntheticEvent, isExpanded: boolean) => {
            props.handleAccordionExpandedChange(accordionIndex, isExpanded)
        }

    /**
     * Ruft den Lauf an den Start oder nimmt das zurück. Der Haken sagt „Am Start", nicht „Läuft":
     * er setzt `activated_at`, der Ist-Start kommt aus der Zeitnahme oder aus dem „Läuft"-Knopf im
     * Schiedsrichter-Dashboard.
     */
    const handleToggleActivation = async (match: CompetitionMatchDto) => {
        // Check if match has no places set
        const hasPlacesSet = match.teams.some(
            team => team.place !== null && team.place !== undefined,
        )
        if (hasPlacesSet) {
            feedback.error(t('event.competition.execution.running.error.hasPlaces'))
            return
        }

        props.setSubmitting(true)
        const {error} = await updateMatchActivation({
            path: {
                eventId: eventId,
                competitionId: competitionId,
                competitionMatchId: match.id,
            },
            body: {
                activated: match.activatedAt == null,
            },
        })
        props.setSubmitting(false)

        if (error) {
            feedback.error(t('event.competition.execution.running.error.update'))
        } else {
            feedback.success(t('event.competition.execution.running.success'))
            props.reloadRoundDto()
        }
    }

    return (
        <Fragment>
            <Stack
                spacing={2}
                sx={{
                    borderLeft: 1,
                    borderColor: theme.palette.primary.main,
                    pl: 4,
                    py: 2,
                }}>
                <Typography variant={'h2'}>{round.name}</Typography>
                {round.required && (
                    <Typography>{t('event.competition.setup.round.required')}</Typography>
                )}
                <Box>
                    {!round.required &&
                        round.matches.filter(match => match.teams.length === 1).length > 0 && (
                            <Accordion
                                expanded={props.accordionsExpanded?.[0] ?? false}
                                onChange={handleAccordionExpandedChange(0)}>
                                <AccordionSummary
                                    expandIcon={<ExpandMoreIcon />}
                                    aria-expanded={true}
                                    aria-controls={`round-${roundIndex}-${round.name}-panel-teams-with-bye-content`}
                                    id={`round-${roundIndex}-${round.name}-panel-teams-with-bye-header`}>
                                    <Typography component="span">
                                        {t('event.competition.execution.teamsWithBye')} (
                                        {
                                            round.matches.filter(match => match.teams.length === 1)
                                                .length
                                        }
                                        )
                                    </Typography>
                                </AccordionSummary>
                                <AccordionDetails>
                                    <TableContainer>
                                        <Table>
                                            <TableHead>
                                                <TableRow>
                                                    <TableCell width="20%">
                                                        {t(
                                                            'event.competition.setup.match.outcome.outcome',
                                                        )}
                                                    </TableCell>
                                                    <TableCell width="80%">
                                                        {t(
                                                            'event.competition.execution.match.team',
                                                        )}
                                                    </TableCell>
                                                </TableRow>
                                            </TableHead>
                                            <TableBody>
                                                {round.matches
                                                    .filter(match => match.teams.length === 1)
                                                    .sort((a, b) => a.weighting - b.weighting)
                                                    .map(match => (
                                                        <TableRow key={match.id}>
                                                            <TableCell width="20%">
                                                                {match.weighting}
                                                            </TableCell>
                                                            <TableCell width="80%">
                                                                {match.teams[0].clubName +
                                                                    (match.teams[0].name
                                                                        ? ` ${match.teams[0].name}`
                                                                        : '')}
                                                            </TableCell>
                                                        </TableRow>
                                                    ))}
                                            </TableBody>
                                        </Table>
                                    </TableContainer>
                                </AccordionDetails>
                            </Accordion>
                        )}
                    <Accordion
                        expanded={props.accordionsExpanded?.[1] ?? false}
                        onChange={handleAccordionExpandedChange(1)}>
                        <AccordionSummary
                            expandIcon={<ExpandMoreIcon />}
                            aria-controls={`round-${roundIndex}-${round.name}-panel-substitutions-content`}
                            id={`round-${roundIndex}-${round.name}-panel-substitutions-header`}>
                            <Typography component="span">
                                {t('event.competition.execution.substitution.substitutions')}
                            </Typography>
                        </AccordionSummary>
                        <AccordionDetails>
                            <Substitutions
                                reloadRoundDto={() => props.reloadRoundDto()}
                                roundDto={round}
                                roundIndex={roundIndex}
                            />
                        </AccordionDetails>
                    </Accordion>
                </Box>
                <Box sx={{py: 2}}>
                    <Divider variant={'middle'} />
                </Box>
                {counterChips.length > 0 && (
                    <Stack direction={'row'} spacing={1} useFlexGap sx={{flexWrap: 'wrap'}}>
                        {counterChips.map(chip => (
                            <StatusChip key={chip.labelKey} chip={chip} />
                        ))}
                    </Stack>
                )}
                <Box sx={{display: 'flex', flexWrap: 'wrap', gap: 4}}>
                    {filteredMatches.map((match, matchIndex) => (
                        <Card
                            key={match.id}
                            sx={{
                                p: 2,
                                flex: 1,
                                [theme.breakpoints.up('md')]: {
                                    minWidth: 400,
                                },
                                ...(match.activatedAt != null && {
                                    borderColor: 'primary.main',
                                    borderWidth: 2,
                                    borderStyle: 'solid',
                                }),
                            }}>
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
                                        <Typography variant={'h3'}>{match.name}</Typography>
                                    )}
                                    <Typography>
                                        {t('event.competition.execution.match.startTime') + ': '}
                                        {match.startTime
                                            ? format(
                                                  new Date(match.startTime),
                                                  t('format.datetime'),
                                              )
                                            : '-'}
                                    </Typography>
                                    {match.startTimeOffset && (
                                        <Typography>
                                            {t(
                                                'event.competition.execution.match.startTimeOffset',
                                            ) + ': '}
                                            {match.startTimeOffset} {t('common.form.seconds')}
                                        </Typography>
                                    )}
                                    {/* Only show toggle if match has no places set */}
                                    {!match.teams.some(
                                        team => team.place !== null && team.place !== undefined,
                                    ) && (
                                        <FormControlLabel
                                            control={
                                                <Checkbox
                                                    checked={match.activatedAt != null}
                                                    onChange={() => handleToggleActivation(match)}
                                                    disabled={submitting}
                                                />
                                            }
                                            label={t(
                                                'event.competition.execution.match.activated',
                                            )}
                                        />
                                    )}
                                </Stack>
                                <Stack direction={'column'} spacing={1}>
                                    {/* Status oben rechts: Checkbox und farbiger Rahmen bleiben,
                                        wie sie sind — der Chip sagt zusätzlich, was ein nicht
                                        aktiver Lauf ist (beendet, abgesagt, überfällig, teilweise
                                        gewertet), was bis hierher alles gleich aussah. */}
                                    <Stack
                                        direction={'row'}
                                        spacing={1}
                                        useFlexGap
                                        sx={{flexWrap: 'wrap', justifyContent: 'flex-end'}}>
                                        <StatusChip
                                            chip={matchStatusChip(
                                                match.status,
                                                match.startTime,
                                                now,
                                            )}
                                        />
                                        <StatusChip chip={arenaChip(match.status)} />
                                    </Stack>
                                    {roundIndex === 0 && (
                                        <SelectionMenu
                                            anchor={{
                                                button: {
                                                    vertical: 'bottom',
                                                    horizontal: 'right',
                                                },
                                                menu: {
                                                    vertical: 'top',
                                                    horizontal: 'right',
                                                },
                                            }}
                                            buttonContent={t(
                                                'event.competition.execution.results.enter',
                                            )}
                                            keyLabel={'competition-execution-results-enter'}
                                            onSelectItem={async (value: string) => {
                                                const v = value as MatchResultOption
                                                switch (v) {
                                                    case 'form':
                                                        props.openResultsDialog(matchIndex)
                                                        break
                                                    case 'XLS':
                                                        setResultImportMatch(match.id)
                                                        break
                                                    case 'RACECLOCKER':
                                                        await pullRaceClockerResults(match.id)
                                                        break
                                                }
                                            }}
                                            items={matchResultOptions(timingSystem).map(
                                                o =>
                                                    ({
                                                        id: o,
                                                        label: t(
                                                            `event.competition.execution.results.type.${o}`,
                                                        ),
                                                    }) satisfies {
                                                        id: MatchResultOption
                                                        label: string
                                                    },
                                            )}
                                        />
                                    )}
                                    {match.pairingsRecalculatedAt && (
                                        <Typography variant={'caption'} color={'warning.main'}>
                                            {t('event.competition.execution.pairingsRecalculated')}
                                        </Typography>
                                    )}
                                    {timingSystem === 'RACECLOCKER' &&
                                        (() => {
                                            const status = raceClockerPollStatus(match)
                                            if (status.kind === 'none') return null

                                            return (
                                                <Stack spacing={0.5}>
                                                    <Typography
                                                        variant={'caption'}
                                                        color={
                                                            status.kind === 'ok'
                                                                ? 'text.secondary'
                                                                : 'warning.main'
                                                        }>
                                                        {status.kind === 'paused'
                                                            ? t(
                                                                  'event.competition.execution.results.raceclocker.poll.paused',
                                                              )
                                                            : status.kind === 'error'
                                                              ? t(
                                                                    'event.competition.execution.results.raceclocker.poll.error',
                                                                    {
                                                                        reason: status.errorKey
                                                                            ? t(status.errorKey)
                                                                            : t(
                                                                                  'common.error.unexpected',
                                                                              ),
                                                                    },
                                                                )
                                                              : t(
                                                                    'event.competition.execution.results.raceclocker.poll.lastPolled',
                                                                    {
                                                                        time: format(
                                                                            new Date(
                                                                                match.raceClockerPolledAt!,
                                                                            ),
                                                                            'HH:mm:ss',
                                                                        ),
                                                                    },
                                                                )}
                                                    </Typography>
                                                    {status.kind === 'paused' && (
                                                        <LoadingButton
                                                            size={'small'}
                                                            variant={'text'}
                                                            pending={submitting}
                                                            onClick={() =>
                                                                resumeRaceClockerAutoPull(
                                                                    match.id,
                                                                )
                                                            }>
                                                            {t(
                                                                'event.competition.execution.results.raceclocker.poll.resume',
                                                            )}
                                                        </LoadingButton>
                                                    )}
                                                </Stack>
                                            )
                                        })()}
                                    <LoadingButton
                                        onClick={() =>
                                            props.openEditMatchDialog(roundIndex, matchIndex)
                                        }
                                        variant={'outlined'}
                                        pending={submitting}>
                                        {t('event.competition.execution.matchData.edit')}
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
                                            },
                                        }}
                                        buttonContent={t(
                                            'event.competition.execution.startList.download',
                                        )}
                                        keyLabel={'competition-execution-startlist-download'}
                                        onSelectItem={async (fileType: string) => {
                                            const ft = fileType as StartListFileType
                                            switch (ft) {
                                                case 'PDF':
                                                    await handleDownloadStartListPDF(match.id)
                                                    break
                                                case 'CSV':
                                                    await handleDownloadStartListCSV(match.id)
                                                    break
                                            }
                                        }}
                                        items={
                                            [
                                                {
                                                    id: 'PDF',
                                                    label: t(
                                                        'event.competition.execution.startList.type.PDF',
                                                    ),
                                                },
                                                {
                                                    id: 'CSV',
                                                    label: t(
                                                        'event.competition.execution.startList.type.CSV',
                                                    ),
                                                },
                                            ] satisfies {id: StartListFileType; label: string}[]
                                        }
                                    />
                                </Stack>
                            </Stack>
                            <Divider sx={{my: 2}} />
                            <TableContainer>
                                <Table>
                                    <TableHead>
                                        <TableRow>
                                            <TableCell width="15%">
                                                {smallScreenLayout
                                                    ? t(
                                                          'event.competition.execution.match.startNumber.short',
                                                      )
                                                    : t(
                                                          'event.competition.execution.match.startNumber.startNumber',
                                                      )}
                                            </TableCell>
                                            <TableCell width="55%">
                                                {t('event.competition.execution.match.team')}
                                            </TableCell>
                                            <TableCell width="10%">
                                                {t('event.competition.execution.match.place')}
                                            </TableCell>
                                            <TableCell width="20%">
                                                {t('event.competition.execution.match.time')}
                                            </TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {match.teams
                                            .sort((a, b) => a.startNumber - b.startNumber)
                                            .map(team => (
                                                <TableRow key={team.registrationId}>
                                                    <TableCell width="15%">
                                                        {team.startNumber}
                                                    </TableCell>
                                                    <TableCell width="55%">
                                                        <Typography>
                                                            {team.actualClubName ?? team.clubName}
                                                        </Typography>
                                                        <Typography
                                                            variant="body2"
                                                            color="textSecondary">
                                                            {`${t('club.registeredBy')} ` +
                                                                team.clubName +
                                                                ` | ${team.name}`}
                                                        </Typography>
                                                        <Typography
                                                            variant="body2"
                                                            color="textSecondary">
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
                                                    <TableCell width="10%">
                                                        {team.deregistered
                                                            ? t(
                                                                  'event.competition.registration.deregister.deregistered',
                                                              ) +
                                                              (team.deregistrationReason
                                                                  ? ` (${team.deregistrationReason})`
                                                                  : '')
                                                            : team.failed
                                                              ? failedLabel(
                                                                    team.failedReason,
                                                                    t(
                                                                        'event.competition.execution.results.failed',
                                                                    ),
                                                                )
                                                              : team.place}
                                                    </TableCell>
                                                    <TableCell width="20%">
                                                        {team.timeString}
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
                    <Stack direction={'row'} spacing={2}>
                        <LoadingButton
                            pending={submitting}
                            onClick={deleteCurrentRound}
                            variant={'outlined'}>
                            {t('event.competition.execution.deleteRound.delete')}
                        </LoadingButton>
                        {roundHasNothingToRace(round.matches) && (
                            <LoadingButton
                                pending={submitting}
                                onClick={cancelRound}
                                variant={'outlined'}>
                                {t('event.competition.execution.cancelRound.action')}
                            </LoadingButton>
                        )}
                    </Stack>
                )}
            </Stack>
        </Fragment>
    )
}
export default CompetitionExecutionRound

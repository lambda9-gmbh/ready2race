import {Box, Button, Card, CardContent, Divider, Stack, Typography} from '@mui/material'
import SwapHorizIcon from '@mui/icons-material/SwapHoriz'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {LiveDashboardMatchDto, PendingSlotDto} from '@api/types.gen.ts'
import {MatchResultStatus, matchResultStatus} from '@utils/matchResultStatus.ts'
import {raceClockerPollStatus} from '@components/event/competition/excecution/raceClockerPollStatus.ts'
import {
    crewMemberLabel,
    formatMinutes,
    matchControls,
    openResultTeams,
    pendingSlotLabel,
    teamShowsClubLine,
    teamShowsCrew,
} from './common.ts'
import FinishMatchButton from './FinishMatchButton.tsx'
import SeverityIcon from './SeverityIcon.tsx'

/**
 * Ab dieser Kartenbreite ist Platz für die Langform des Status. Darunter würde sie die Spalte im
 * Kopf-Grid so weit aufziehen, dass daneben nur noch der erste Buchstabe des Laufnamens bleibt —
 * beide Zeilen teilen sich dieselbe Spalte.
 */
const WIDE_CARD_PX = 480

/**
 * Ab hier trägt die Karte zusätzlich die Crew je Boot — Nachname, Vereinskurzform und Rolle. Auch
 * das entscheidet die Kartenbreite und nicht das Fenster: auf dem Tablet stehen zwei Spalten
 * nebeneinander, von denen keine so breit wird, obwohl das Fenster es wäre. Ob die Crew überhaupt
 * geladen wurde, hängt dagegen am Fenster (`dashboardCrew`) — die Nutzlast wird je Abruf
 * entschieden, nicht je Karte.
 */
const CREW_CARD_PX = 700

type Props = {
    match: LiveDashboardMatchDto
    onTeamClick: (matchId: string, teamId: string) => void
    /** Nur gesetzt, wenn die Nutzerin den Ablauf steuern darf. */
    onFinish?: (matchId: string, openResults: MatchResultStatus | null) => Promise<void>
    onSetRunning?: (matchId: string, running: boolean) => Promise<void>
}

const LiveDashboardMatchCard = ({match, onTeamClick, onFinish, onSetRunning}: Props) => {
    const {t} = useTranslation()

    const running = match.state === 'RUNNING'
    // Abgesagt wird gekennzeichnet, nicht versteckt: der Schiedsrichter muss die Absage sehen, um
    // sie im Zeitplan zurücknehmen zu können. Solange sie steht, gibt es hier aber nichts zu
    // steuern - aktiviert würde der Lauf sonst wieder abgesagt UND laufend zugleich.
    const skipped = match.state === 'SKIPPED'
    // Vollständig gewertet, aber nicht beendet: der Lauf wartet auf den Beenden-Klick.
    const awaitingFinish = match.state === 'AWAITING_FINISH'
    const {showFinish, showRunToggle} = matchControls(match, onFinish != null, onSetRunning != null)
    // Result columns are reserved for the whole match, not per row: times then line up
    // underneath each other and every team name keeps the same width.
    const hasResults = match.teams.some(team => team.time || team.place != null || team.failed)
    const openTeams = openResultTeams(match)
    const resultsComplete = match.teams.length > 0 && openTeams.length === 0
    const columns = hasResults
        ? '2ch minmax(0, 1fr) 10.5ch 2rem 26px'
        : '2ch minmax(0, 1fr) 26px'

    return (
        <Card
            variant="outlined"
            sx={{
                minWidth: 0,
                overflow: 'hidden',
                // Die Karte richtet sich nach ihrer eigenen Breite, nicht nach der des Fensters:
                // nebeneinander stehende Spalten auf dem Tablet sind schmaler als ein Telefon,
                // ein Blick aufs Fenster würde dort die Langformen erzwingen.
                containerType: 'inline-size',
                // Accent bar instead of a full frame: marks the live race without shouting
                borderLeft: running ? '6px solid' : undefined,
                borderLeftColor: running ? 'success.dark' : undefined,
            }}>
            <CardContent sx={{p: 1.25, '&:last-child': {pb: 0.5}}}>
                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: 'minmax(0, 1fr) auto',
                        columnGap: 1.5,
                        alignItems: 'baseline',
                    }}>
                    <Typography
                        variant="subtitle1"
                        fontWeight={700}
                        noWrap
                        sx={{textDecoration: skipped ? 'line-through' : 'none'}}>
                        {match.matchName ?? match.roundName ?? match.competitionName}
                    </Typography>
                    <Box sx={{justifySelf: 'end', textAlign: 'right'}}>
                        <Typography
                            variant="subtitle1"
                            fontWeight={700}
                            sx={{fontVariantNumeric: 'tabular-nums', color: 'grey.900'}}>
                            {match.startTime
                                ? t('event.liveDashboard.plannedAt', {
                                      time: format(new Date(match.startTime), t('format.time')),
                                  })
                                : '—'}
                        </Typography>
                        {match.startedAt && (
                            <Typography
                                variant="caption"
                                display="block"
                                sx={{color: 'success.dark', fontVariantNumeric: 'tabular-nums'}}>
                                {t('event.liveDashboard.startedAtLabel', {
                                    time: format(new Date(match.startedAt), t('format.time')),
                                })}
                            </Typography>
                        )}
                    </Box>
                    <Typography
                        variant="body2"
                        sx={{
                            color: 'grey.800',
                            display: '-webkit-box',
                            WebkitLineClamp: 2,
                            WebkitBoxOrient: 'vertical',
                            overflow: 'hidden',
                        }}>
                        {[match.competitionName, match.categoryName, match.roundName]
                            .filter(Boolean)
                            .join(' · ')}
                    </Typography>
                    <Box sx={{justifySelf: 'end'}}>
                        <Box
                            component="span"
                            sx={{
                                display: 'inline-block',
                                px: 0.75,
                                py: 0.25,
                                borderRadius: 1,
                                fontSize: '0.8rem',
                                fontWeight: 700,
                                whiteSpace: 'nowrap',
                                backgroundColor: running
                                    ? 'success.dark'
                                    : skipped
                                      ? 'warning.dark'
                                      : awaitingFinish
                                        ? 'info.dark'
                                        : 'grey.200',
                                color:
                                    running || skipped || awaitingFinish
                                        ? 'common.white'
                                        : 'grey.900',
                            }}>
                            {running && match.elapsedMinutes != null ? (
                                t('event.liveDashboard.runningSince', {
                                    duration: formatMinutes(match.elapsedMinutes),
                                })
                            ) : awaitingFinish ? (
                                // Der volle Text sprengt schmale Karten; die Kurzform trägt
                                // dieselbe Aussage. Ausschlaggebend ist die Kartenbreite: der
                                // Kopf teilt sich eine Spalte mit diesem Label, ein zu langes
                                // schneidet nebenan den Laufnamen ab.
                                <>
                                    <Box
                                        component="span"
                                        sx={{
                                            display: 'none',
                                            [`@container (min-width: ${WIDE_CARD_PX}px)`]: {
                                                display: 'inline',
                                            },
                                        }}>
                                        {t('event.liveDashboard.state.AWAITING_FINISH')}
                                    </Box>
                                    <Box
                                        component="span"
                                        sx={{
                                            display: 'inline',
                                            [`@container (min-width: ${WIDE_CARD_PX}px)`]: {
                                                display: 'none',
                                            },
                                        }}>
                                        {t('event.liveDashboard.state.AWAITING_FINISH_SHORT')}
                                    </Box>
                                </>
                            ) : (
                                t(`event.liveDashboard.state.${match.state}`)
                            )}
                        </Box>
                    </Box>
                </Box>
                {(() => {
                    const status = raceClockerPollStatus(match)
                    if (status.kind === 'none' || status.kind === 'ok') return null

                    return (
                        <Typography variant={'caption'} color={'warning.main'}>
                            {status.kind === 'paused'
                                ? t('event.competition.execution.results.raceclocker.poll.paused')
                                : t('event.competition.execution.results.raceclocker.poll.error', {
                                      reason: status.errorKey
                                          ? t(status.errorKey)
                                          : t('common.error.unexpected'),
                                  })}
                        </Typography>
                    )
                })()}
                <Divider sx={{mt: 1.5}} />
                {match.teams.map((team, index) => {
                    const substituted = team.substituted
                    const showClubLine = teamShowsClubLine(team)
                    const showCrew = teamShowsCrew(team)

                    return (
                        <Box
                            key={team.teamId}
                            onClick={() => onTeamClick(match.matchId, team.teamId)}
                            sx={{
                                display: 'grid',
                                gridTemplateColumns: columns,
                                columnGap: 0.75,
                                alignItems: 'center',
                                py: 1.25,
                                mx: -1,
                                px: 1,
                                cursor: 'pointer',
                                borderRadius: 1,
                                borderBottom: index < match.teams.length - 1 ? '1px solid' : 'none',
                                borderBottomColor: 'divider',
                                '&:active': {backgroundColor: 'action.selected'},
                                '@media (hover: hover)': {
                                    '&:hover': {backgroundColor: 'action.hover'},
                                },
                            }}>
                            <Typography
                                variant="subtitle1"
                                fontWeight={700}
                                sx={{fontVariantNumeric: 'tabular-nums', color: 'grey.700'}}>
                                {team.startNumber ?? '–'}
                            </Typography>
                            <Box sx={{minWidth: 0}}>
                                <Stack
                                    direction="row"
                                    spacing={0.5}
                                    alignItems="center"
                                    sx={{minWidth: 0}}>
                                    <Typography
                                        variant="subtitle1"
                                        sx={{
                                            lineHeight: 1.25,
                                            overflowWrap: 'break-word',
                                            display: '-webkit-box',
                                            WebkitLineClamp: 2,
                                            WebkitBoxOrient: 'vertical',
                                            overflow: 'hidden',
                                        }}>
                                        {team.teamName ?? team.clubsShort}
                                    </Typography>
                                    {substituted && (
                                        <SwapHorizIcon
                                            sx={{
                                                fontSize: 22,
                                                flexShrink: 0,
                                                color: 'info.dark',
                                            }}
                                            titleAccess={t(
                                                'event.liveDashboard.substitution.short',
                                            )}
                                        />
                                    )}
                                </Stack>
                                {showClubLine && (
                                    <Typography
                                        variant="body2"
                                        aria-label={t('event.liveDashboard.team.clubs')}
                                        sx={{
                                            color: 'grey.800',
                                            // Die Kette wird nicht gekappt, sie bricht auf zwei
                                            // Zeilen um; ein sechster Verein fällt hinten heraus.
                                            display: '-webkit-box',
                                            WebkitLineClamp: 2,
                                            WebkitBoxOrient: 'vertical',
                                            overflow: 'hidden',
                                        }}>
                                        <Box
                                            component="span"
                                            sx={{
                                                display: 'inline',
                                                [`@container (min-width: ${WIDE_CARD_PX}px)`]: {
                                                    display: 'none',
                                                },
                                            }}>
                                            {team.clubsShort}
                                        </Box>
                                        <Box
                                            component="span"
                                            sx={{
                                                display: 'none',
                                                [`@container (min-width: ${WIDE_CARD_PX}px)`]: {
                                                    display: 'inline',
                                                },
                                            }}>
                                            {team.clubsFull}
                                        </Box>
                                    </Typography>
                                )}
                                {showCrew && (
                                    <Typography
                                        variant="caption"
                                        aria-label={t('event.liveDashboard.team.crew')}
                                        sx={{
                                            color: 'grey.700',
                                            display: 'none',
                                            [`@container (min-width: ${CREW_CARD_PX}px)`]: {
                                                display: '-webkit-box',
                                            },
                                            WebkitLineClamp: 2,
                                            WebkitBoxOrient: 'vertical',
                                            overflow: 'hidden',
                                        }}>
                                        {(team.crew ?? []).map(crewMemberLabel).join(' / ')}
                                    </Typography>
                                )}
                                {team.onWaterRequired && team.onWaterAt && (
                                    <Typography
                                        variant="caption"
                                        display="block"
                                        sx={{
                                            color: 'success.dark',
                                            fontVariantNumeric: 'tabular-nums',
                                        }}>
                                        {t('event.liveDashboard.team.onWaterAt', {
                                            time: format(new Date(team.onWaterAt), t('format.time')),
                                        })}
                                    </Typography>
                                )}
                            </Box>
                            {hasResults && (
                                <>
                                    {/* Times share one right-aligned monospaced column, so they
                                        can be compared by scanning straight down. */}
                                    <Typography
                                        fontWeight={700}
                                        textAlign="right"
                                        sx={{
                                            fontSize: '0.9rem',
                                            fontFamily:
                                                'ui-monospace, SFMono-Regular, Menlo, monospace',
                                            fontVariantNumeric: 'tabular-nums',
                                            letterSpacing: '-0.05em',
                                            color: team.failed ? 'warning.dark' : 'text.primary',
                                        }}>
                                        {team.failed
                                            ? (matchResultStatus(team.failedReason).status ??
                                              t('event.liveDashboard.team.failedShort'))
                                            : (team.time ?? '')}
                                        {team.penaltySeconds != null && (
                                            <Typography
                                                component="span"
                                                color="warning.dark"
                                                display="block"
                                                sx={{
                                                    fontSize: '0.8rem',
                                                    fontVariantNumeric: 'tabular-nums',
                                                }}>
                                                {t('event.liveDashboard.penaltyIncluded', {
                                                    seconds: team.penaltySeconds,
                                                })}
                                            </Typography>
                                        )}
                                    </Typography>
                                    <Box
                                        sx={{
                                            width: '2rem',
                                            height: '2rem',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                            borderRadius: '50%',
                                            backgroundColor:
                                                team.place != null ? 'primary.main' : 'transparent',
                                        }}>
                                        {team.place != null && (
                                            <Typography
                                                fontWeight={700}
                                                color="primary.contrastText"
                                                sx={{
                                                    fontSize: '0.95rem',
                                                    fontVariantNumeric: 'tabular-nums',
                                                }}>
                                                {team.place}
                                            </Typography>
                                        )}
                                    </Box>
                                </>
                            )}
                            <SeverityIcon severity={team.severity} />
                        </Box>
                    )
                })}
                {(showFinish || showRunToggle) && (
                    <Stack
                        direction="row"
                        spacing={1}
                        flexWrap="wrap"
                        justifyContent="flex-end"
                        alignItems="center"
                        sx={{pt: 1.5}}>
                        {running && !resultsComplete && (
                            <Typography variant="caption" sx={{color: 'grey.700', mr: 'auto'}}>
                                {t('event.liveDashboard.control.incompleteWarning')}
                            </Typography>
                        )}
                        {(awaitingFinish || (running && resultsComplete)) && (
                            <Typography variant="caption" sx={{color: 'success.dark', mr: 'auto'}}>
                                {t('event.liveDashboard.resultsCompleteWaiting')}
                            </Typography>
                        )}
                        {showRunToggle && onSetRunning && (
                            <Button
                                size="small"
                                variant="text"
                                onClick={() => onSetRunning(match.matchId, !running)}>
                                {running
                                    ? t('event.liveDashboard.control.deactivate')
                                    : t('event.liveDashboard.control.activate')}
                            </Button>
                        )}
                        {showFinish && onFinish && (
                            <FinishMatchButton
                                openTeamCount={openTeams.length}
                                onFinish={openResults => onFinish(match.matchId, openResults)}
                            />
                        )}
                    </Stack>
                )}
            </CardContent>
        </Card>
    )
}

export default LiveDashboardMatchCard

type PendingSlotCardProps = {
    slot: PendingSlotDto
    /** Nur gesetzt, wenn die Nutzerin den Ablauf steuern darf. */
    onSkip?: (slotId: string, label: string, time: string) => void
}

/**
 * Platzhalter im Referee-Dashboard — entweder ein Programmpunkt (FREE, z.B. "Mittagspause") oder
 * ein wartender Lauf-Slot (Runde noch nicht gesetzt); `slot.name` unterscheidet die Fälle (siehe
 * `PendingSlotDto`). Bewusst ohne Teams oder Ergebnis-Spalten, die gibt es für beide Fälle nicht.
 */
export const LiveDashboardPendingSlotCard = ({slot, onSkip}: PendingSlotCardProps) => {
    const {t} = useTranslation()
    const isFree = slot.name != null
    const label = pendingSlotLabel(slot)
    const time = format(new Date(slot.startTime), t('format.time'))
    const stateLabel = t(isFree ? 'event.schedule.state.FREE' : 'event.schedule.state.WAITING')

    return (
        <Card variant="outlined" sx={{minWidth: 0, overflow: 'hidden'}}>
            <CardContent sx={{p: 1.25, '&:last-child': {pb: 0.75}}}>
                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: 'minmax(0, 1fr) auto',
                        columnGap: 1.5,
                        alignItems: 'baseline',
                    }}>
                    <Typography variant="subtitle1" fontWeight={700} noWrap sx={{color: 'grey.700'}}>
                        {label || stateLabel}
                    </Typography>
                    <Typography
                        variant="subtitle1"
                        fontWeight={700}
                        textAlign="right"
                        sx={{fontVariantNumeric: 'tabular-nums', color: 'grey.900'}}>
                        {time}
                    </Typography>
                </Box>
                <Box
                    sx={{
                        mt: 1,
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        flexWrap: 'wrap',
                        gap: 1,
                    }}>
                    <Box
                        component="span"
                        sx={{
                            display: 'inline-block',
                            px: 0.75,
                            py: 0.25,
                            borderRadius: 1,
                            fontSize: '0.8rem',
                            fontWeight: 700,
                            backgroundColor: 'grey.200',
                            color: 'grey.900',
                        }}>
                        {stateLabel}
                    </Box>
                    {/* Programmpunkte sagt nur die Orga ab (Zeitplan-Tab), nicht das
                        Schiedsrichter-Dashboard - das Backend lehnt das inzwischen auch ab. */}
                    {onSkip && !isFree && (
                        <Button
                            size="small"
                            variant="text"
                            onClick={() => onSkip(slot.slotId, label, time)}>
                            {t('event.schedule.skip')}
                        </Button>
                    )}
                </Box>
            </CardContent>
        </Card>
    )
}

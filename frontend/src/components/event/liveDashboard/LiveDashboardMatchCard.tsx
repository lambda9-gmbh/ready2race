import {Box, Button, Card, CardContent, Divider, Stack, Typography} from '@mui/material'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import CancelIcon from '@mui/icons-material/Cancel'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked'
import SwapHorizIcon from '@mui/icons-material/SwapHoriz'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {LiveDashboardMatchDto, PendingSlotDto} from '@api/types.gen.ts'
import {MatchResultStatus, matchResultStatus} from '@utils/matchResultStatus.ts'
import {
    formatMinutes,
    matchControls,
    openResultTeams,
    pendingSlotLabel,
    Severity,
    shortClubName,
    teamSeverity,
} from './common.ts'
import FinishMatchButton from './FinishMatchButton.tsx'

type Props = {
    match: LiveDashboardMatchDto
    onTeamClick: (matchId: string, teamId: string) => void
    /** Nur gesetzt, wenn die Nutzerin den Ablauf steuern darf. */
    onFinish?: (matchId: string, openResults: MatchResultStatus | null) => Promise<void>
    onSetRunning?: (matchId: string, running: boolean) => Promise<void>
}

// One glanceable icon per team replaces the detail chips — everything else
// lives in the team dialog, one tap away.
// Draußen zählt Kontrast: die dunklen Palette-Varianten bleiben auch bei Sonne
// lesbar, während die konfigurierten main-Töne verblassen.
const severityIcon = (severity: Severity) => {
    const sx = {fontSize: 28, display: 'block'}
    switch (severity) {
        case 'ok':
            return <CheckCircleIcon sx={{...sx, color: 'success.dark'}} />
        case 'warning':
            return <WarningAmberIcon sx={{...sx, color: 'warning.dark'}} />
        case 'error':
            return <CancelIcon sx={{...sx, color: 'error.dark'}} />
        case 'neutral':
            return <RadioButtonUncheckedIcon sx={{...sx, color: 'text.disabled'}} />
    }
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
        ? '2ch minmax(0, 1fr) 7.5ch 2rem 26px'
        : '2ch minmax(0, 1fr) 26px'

    return (
        <Card
            variant="outlined"
            sx={{
                minWidth: 0,
                overflow: 'hidden',
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
                            {running && match.elapsedMinutes != null
                                ? t('event.liveDashboard.runningSince', {
                                      duration: formatMinutes(match.elapsedMinutes),
                                  })
                                : t(`event.liveDashboard.state.${match.state}`)}
                        </Box>
                    </Box>
                </Box>
                <Divider sx={{mt: 1.5}} />
                {match.teams.map((team, index) => {
                    const substituted = team.substituted
                    // Kurzform in der Liste; der vollständige Name steht im Detail-Dialog
                    const fullClub = team.actualClubName ?? team.clubName
                    const clubLine = fullClub != null ? shortClubName(fullClub) : null
                    // Team names often already contain the club — then drop the second line
                    const showClubLine =
                        team.teamName != null &&
                        fullClub != null &&
                        clubLine != null &&
                        !team.teamName.includes(fullClub) &&
                        !team.teamName.includes(clubLine)

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
                                        {team.teamName ?? clubLine ?? ''}
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
                                        noWrap
                                        display="block"
                                        sx={{color: 'grey.800'}}>
                                        {clubLine}
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
                            {severityIcon(teamSeverity(team))}
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
                    {onSkip && (
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

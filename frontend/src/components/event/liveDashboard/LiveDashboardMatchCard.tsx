import {Box, Card, CardContent, Divider, Stack, Typography} from '@mui/material'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import CancelIcon from '@mui/icons-material/Cancel'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked'
import SwapHorizIcon from '@mui/icons-material/SwapHoriz'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {LiveDashboardMatchDto} from '@api/types.gen.ts'
import {formatMinutes, Severity, shortClubName, teamSeverity} from './common.ts'

type Props = {
    match: LiveDashboardMatchDto
    onTeamClick: (matchId: string, teamId: string) => void
}

// One glanceable icon per team replaces the detail chips — everything else
// lives in the team dialog, one tap away.
const severityIcon = (severity: Severity) => {
    const sx = {fontSize: 26, display: 'block'}
    switch (severity) {
        case 'ok':
            return <CheckCircleIcon color="success" sx={sx} />
        case 'warning':
            return <WarningAmberIcon color="warning" sx={sx} />
        case 'error':
            return <CancelIcon color="error" sx={sx} />
        case 'neutral':
            return <RadioButtonUncheckedIcon color="disabled" sx={sx} />
    }
}

const LiveDashboardMatchCard = ({match, onTeamClick}: Props) => {
    const {t} = useTranslation()

    const running = match.state === 'RUNNING'
    // Result columns are reserved for the whole match, not per row: times then line up
    // underneath each other and every team name keeps the same width.
    const hasResults = match.teams.some(team => team.time || team.place != null || team.failed)
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
                borderLeft: running ? '5px solid' : undefined,
                borderLeftColor: running ? 'success.main' : undefined,
            }}>
            <CardContent sx={{p: 1.25, '&:last-child': {pb: 0.5}}}>
                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: 'minmax(0, 1fr) auto',
                        columnGap: 1.5,
                        alignItems: 'baseline',
                    }}>
                    <Typography variant="subtitle1" fontWeight={700} noWrap>
                        {match.matchName ?? match.roundName ?? match.competitionName}
                    </Typography>
                    <Typography
                        variant="subtitle1"
                        fontWeight={700}
                        textAlign="right"
                        sx={{fontVariantNumeric: 'tabular-nums'}}
                        color={running ? 'success.main' : 'text.primary'}>
                        {match.startTime
                            ? format(new Date(match.startTime), t('format.time'))
                            : '—'}
                    </Typography>
                    <Typography
                        variant="body2"
                        color="text.secondary"
                        sx={{
                            display: '-webkit-box',
                            WebkitLineClamp: 2,
                            WebkitBoxOrient: 'vertical',
                            overflow: 'hidden',
                        }}>
                        {[match.competitionName, match.categoryName, match.roundName]
                            .filter(Boolean)
                            .join(' · ')}
                    </Typography>
                    <Typography
                        variant="body2"
                        textAlign="right"
                        color={running ? 'success.main' : 'text.secondary'}>
                        {running && match.elapsedMinutes != null
                            ? t('event.liveDashboard.runningSince', {
                                  duration: formatMinutes(match.elapsedMinutes),
                              })
                            : t(`event.liveDashboard.state.${match.state}`)}
                    </Typography>
                </Box>
                <Divider sx={{mt: 1.5}} />
                {match.teams.map((team, index) => {
                    const substituted = team.participants.some(p => p.substitutedFor)
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
                                color="text.secondary"
                                fontWeight={700}
                                sx={{fontVariantNumeric: 'tabular-nums'}}>
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
                                            color="info"
                                            sx={{fontSize: 20, flexShrink: 0}}
                                            titleAccess={t(
                                                'event.liveDashboard.substitution.short',
                                            )}
                                        />
                                    )}
                                </Stack>
                                {showClubLine && (
                                    <Typography
                                        variant="body2"
                                        color="text.secondary"
                                        noWrap
                                        display="block">
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
                                            color: team.failed ? 'warning.main' : 'text.primary',
                                        }}>
                                        {team.failed
                                            ? t('event.liveDashboard.team.failedShort')
                                            : (team.time ?? '')}
                                        {team.penaltySeconds != null && (
                                            <Typography
                                                component="span"
                                                color="warning.main"
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
            </CardContent>
        </Card>
    )
}

export default LiveDashboardMatchCard

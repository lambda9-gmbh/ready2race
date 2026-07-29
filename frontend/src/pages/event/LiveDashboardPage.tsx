import {useRef, useState} from 'react'
import {
    Alert,
    Badge,
    BottomNavigation,
    BottomNavigationAction,
    Box,
    Paper,
    Stack,
    Typography,
} from '@mui/material'
import LiveTvIcon from '@mui/icons-material/LiveTv'
import FormatListNumberedIcon from '@mui/icons-material/FormatListNumbered'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {getLiveDashboard} from '@api/sdk.gen.ts'
import {LiveDashboardDto, LiveDashboardTeamDto} from '@api/types.gen.ts'
import {useFetch} from '@utils/hooks.ts'
import {eventLiveDashboardRoute} from '@routes'
import LiveDashboardMatchCard from '@components/event/liveDashboard/LiveDashboardMatchCard.tsx'
import LiveDashboardTeamDialog from '@components/event/liveDashboard/LiveDashboardTeamDialog.tsx'

const POLL_INTERVAL_MS = 10_000

const LiveDashboardPage = () => {
    const {t} = useTranslation()
    const {eventId} = eventLiveDashboardRoute.useParams()

    const [tab, setTab] = useState<'live' | 'matches'>('live')
    const [dashboard, setDashboard] = useState<LiveDashboardDto | null>(null)
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
    const [stale, setStale] = useState(false)
    const [liveChanged, setLiveChanged] = useState(false)
    const [selectedTeam, setSelectedTeam] = useState<LiveDashboardTeamDto | null>(null)
    const runningIdsRef = useRef<string | null>(null)
    const tabRef = useRef<'live' | 'matches'>('live')

    useFetch(signal => getLiveDashboard({signal, path: {eventId}}), {
        autoReloadInterval: POLL_INTERVAL_MS,
        deps: [eventId],
        onResponse: ({data}) => {
            if (data !== undefined) {
                setDashboard(data)
                setLastUpdated(new Date())
                setStale(false)
                const ids = data.matches
                    .filter(m => m.state === 'RUNNING')
                    .map(m => m.matchId)
                    .join(',')
                if (runningIdsRef.current !== null && ids !== runningIdsRef.current && tabRef.current !== 'live') {
                    setLiveChanged(true)
                }
                runningIdsRef.current = ids
            } else {
                setStale(true)
            }
        },
        onPanic: () => {
            setStale(true)
        },
    })

    const runningMatches = dashboard?.matches.filter(m => m.state === 'RUNNING') ?? []
    const nextUpcoming = dashboard?.matches.find(m => m.state === 'UPCOMING')
    const scheduledMatches = dashboard?.matches.filter(m => m.state !== 'UNSCHEDULED') ?? []
    const unscheduledMatches = dashboard?.matches.filter(m => m.state === 'UNSCHEDULED') ?? []

    return (
        <Box sx={{pb: 9, maxWidth: 700, mx: 'auto'}}>
            <Stack spacing={2} sx={{p: 2}}>
                <Stack direction="row" justifyContent="space-between" alignItems="center">
                    <Typography variant="h6">{t('event.liveDashboard.title')}</Typography>
                    {lastUpdated && (
                        <Typography variant="caption" color="text.secondary">
                            {t('event.liveDashboard.lastUpdated', {
                                time: format(lastUpdated, t('format.time')),
                            })}
                        </Typography>
                    )}
                </Stack>
                {stale && dashboard && (
                    <Alert severity="warning">{t('event.liveDashboard.staleWarning')}</Alert>
                )}
                {stale && !dashboard && (
                    <Alert severity="error">{t('event.liveDashboard.loadError')}</Alert>
                )}
                {tab === 'live' && (
                    <>
                        {runningMatches.length === 0 && dashboard && (
                            <Alert severity="info">{t('event.liveDashboard.noRunning')}</Alert>
                        )}
                        {runningMatches.map(match => (
                            <LiveDashboardMatchCard
                                key={match.matchId}
                                match={match}
                                onTeamClick={setSelectedTeam}
                            />
                        ))}
                        {runningMatches.length === 0 && nextUpcoming && (
                            <>
                                <Typography variant="subtitle2" color="text.secondary">
                                    {t('event.liveDashboard.nextUp')}
                                </Typography>
                                <LiveDashboardMatchCard
                                    match={nextUpcoming}
                                    onTeamClick={setSelectedTeam}
                                />
                            </>
                        )}
                    </>
                )}
                {tab === 'matches' && (
                    <>
                        {scheduledMatches.map(match => (
                            <LiveDashboardMatchCard
                                key={match.matchId}
                                match={match}
                                onTeamClick={setSelectedTeam}
                            />
                        ))}
                        {unscheduledMatches.length > 0 && (
                            <>
                                <Typography variant="subtitle2" color="text.secondary">
                                    {t('event.liveDashboard.unscheduled')}
                                </Typography>
                                {unscheduledMatches.map(match => (
                                    <LiveDashboardMatchCard
                                        key={match.matchId}
                                        match={match}
                                        onTeamClick={setSelectedTeam}
                                    />
                                ))}
                            </>
                        )}
                        {dashboard && dashboard.matches.length === 0 && (
                            <Alert severity="info">{t('event.liveDashboard.noMatches')}</Alert>
                        )}
                    </>
                )}
            </Stack>
            <LiveDashboardTeamDialog team={selectedTeam} onClose={() => setSelectedTeam(null)} />
            <Paper sx={{position: 'fixed', bottom: 0, left: 0, right: 0, zIndex: 10}} elevation={3}>
                <BottomNavigation
                    showLabels
                    value={tab}
                    onChange={(_, newTab: 'live' | 'matches') => {
                        tabRef.current = newTab
                        setTab(newTab)
                        if (newTab === 'live') {
                            setLiveChanged(false)
                        }
                    }}>
                    <BottomNavigationAction
                        value="live"
                        label={t('event.liveDashboard.tabs.live')}
                        icon={
                            <Badge color="error" variant="dot" invisible={!liveChanged}>
                                <LiveTvIcon />
                            </Badge>
                        }
                    />
                    <BottomNavigationAction
                        value="matches"
                        label={t('event.liveDashboard.tabs.matches')}
                        icon={<FormatListNumberedIcon />}
                    />
                </BottomNavigation>
            </Paper>
        </Box>
    )
}

export default LiveDashboardPage

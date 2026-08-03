import {useEffect, useRef, useState} from 'react'
import {
    Alert,
    Badge,
    BottomNavigation,
    BottomNavigationAction,
    Box,
    CircularProgress,
    Paper,
    Stack,
    Typography,
} from '@mui/material'
import LiveTvIcon from '@mui/icons-material/LiveTv'
import FormatListNumberedIcon from '@mui/icons-material/FormatListNumbered'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {
    finishLiveDashboardMatch,
    getLiveDashboard,
    setLiveDashboardMatchRunning,
} from '@api/sdk.gen.ts'
import {LiveDashboardDto} from '@api/types.gen.ts'
import {useFetch, useFeedback} from '@utils/hooks.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateLiveDashboardGlobal} from '@authorization/privileges.ts'
import {eventLiveDashboardRoute} from '@routes'
import LiveDashboardMatchCard from '@components/event/liveDashboard/LiveDashboardMatchCard.tsx'
import LiveDashboardTeamDialog from '@components/event/liveDashboard/LiveDashboardTeamDialog.tsx'
import RefreshCountdown from '@components/event/liveDashboard/RefreshCountdown.tsx'
import {storedPollInterval} from '@components/event/liveDashboard/common.ts'
import {MatchResultStatus} from '@utils/matchResultStatus.ts'

const LiveDashboardPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()
    const {eventId} = eventLiveDashboardRoute.useParams()
    const mayControl = user.checkPrivilege(updateLiveDashboardGlobal)

    const [tab, setTab] = useState<'live' | 'matches'>('live')
    const [pollIntervalMs, setPollIntervalMs] = useState(storedPollInterval)
    const [dashboard, setDashboard] = useState<LiveDashboardDto | null>(null)
    const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
    const [stale, setStale] = useState(false)
    const [liveChanged, setLiveChanged] = useState(false)
    const [selectedTeamRef, setSelectedTeamRef] = useState<{matchId: string; teamId: string} | null>(
        null,
    )
    const runningIdsRef = useRef<string | null>(null)
    const tabRef = useRef<'live' | 'matches'>('live')
    /** Stand der letzten Antwort; spart den Rumpf, solange sich nichts geändert hat. */
    const etagRef = useRef<string | null>(null)

    // Der Live-Tab braucht nur die laufenden Läufe; die vollständige Liste sieht sich niemand im
    // Sekundentakt an. Beim Wechsel wird neu geladen, der letzte Stand gilt nicht für beide.
    const scope = tab === 'live' ? 'LIVE' : 'ALL'

    const dashboardData = useFetch(
        signal =>
            getLiveDashboard({
                signal,
                path: {eventId},
                query: {scope},
                // Unverändert? Dann antwortet der Server mit 304 und ohne Rumpf. 'no-store' hält
                // den Browser-Cache aus der Bedingung heraus, sonst beantwortet er sie selbst.
                headers: etagRef.current ? {'If-None-Match': etagRef.current} : undefined,
                cache: 'no-store',
            }),
        {
            autoReloadInterval: pollIntervalMs,
            deps: [eventId, pollIntervalMs, scope],
            onResponse: ({data, response}) => {
                if (response.status === 304) {
                    setLastUpdated(new Date())
                    setStale(false)
                    return
                }
                if (data !== undefined) {
                    etagRef.current = response.headers.get('ETag')
                    setDashboard(data)
                    setLastUpdated(new Date())
                    setStale(false)
                    const ids = data.matches
                        .filter(m => m.state === 'RUNNING')
                        .map(m => m.matchId)
                        .join(',')
                    if (
                        runningIdsRef.current !== null &&
                        ids !== runningIdsRef.current &&
                        tabRef.current !== 'live'
                    ) {
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
        },
    )

    const runningMatches = dashboard?.matches.filter(m => m.state === 'RUNNING') ?? []
    const nextUpcoming = dashboard?.matches.find(m => m.state === 'UPCOMING')
    const scheduledMatches = dashboard?.matches.filter(m => m.state !== 'UNSCHEDULED') ?? []
    const unscheduledMatches = dashboard?.matches.filter(m => m.state === 'UNSCHEDULED') ?? []

    const selectedTeam = selectedTeamRef
        ? (dashboard?.matches
              .find(m => m.matchId === selectedTeamRef.matchId)
              ?.teams.find(team => team.teamId === selectedTeamRef.teamId) ?? null)
        : null

    useEffect(() => {
        if (selectedTeam === null && dashboard !== null && selectedTeamRef !== null) {
            setSelectedTeamRef(null)
        }
    }, [selectedTeam, dashboard, selectedTeamRef])

    const handleTeamClick = (matchId: string, teamId: string) => setSelectedTeamRef({matchId, teamId})

    const handleFinish = async (matchId: string, openResults: MatchResultStatus | null) => {
        const {error} = await finishLiveDashboardMatch({
            path: {eventId, matchId},
            query: openResults ? {openResults} : undefined,
        })
        if (error) {
            feedback.error(t('event.liveDashboard.control.error'))
        } else {
            feedback.success(t('event.liveDashboard.control.finished'))
        }
        dashboardData.reload()
    }

    const handleSetRunning = async (matchId: string, running: boolean) => {
        const {error} = await setLiveDashboardMatchRunning({
            path: {eventId, matchId},
            query: {running},
        })
        if (error) {
            feedback.error(t('event.liveDashboard.control.error'))
        }
        dashboardData.reload()
    }

    return (
        <Box
            sx={{
                pb: 9,
                // width + maxWidth statt nur maxWidth: das Layout setzt die Seite als
                // Flex-Kind ein, das sonst mit seinem Inhalt über den Viewport wächst
                width: '100%',
                maxWidth: 700,
                mx: 'auto',
                minWidth: 0,
                overflowX: 'hidden',
            }}>
            <Stack spacing={2} sx={{px: 1, py: 2, minWidth: 0}}>
                <Stack
                    direction="row"
                    justifyContent="space-between"
                    alignItems="baseline"
                    spacing={1}>
                    <Typography
                        variant="subtitle1"
                        fontWeight={700}
                        sx={{minWidth: 0, lineHeight: 1.2}}>
                        {t('event.liveDashboard.title')}
                    </Typography>
                    <Stack direction="row" spacing={0.5} alignItems="center" flexShrink={0}>
                        {lastUpdated && (
                            <Typography variant="caption" noWrap sx={{color: 'grey.700'}}>
                                {format(lastUpdated, t('format.timeWithSeconds'))}
                            </Typography>
                        )}
                        <RefreshCountdown
                            intervalMs={pollIntervalMs}
                            lastUpdated={lastUpdated}
                            onIntervalChange={setPollIntervalMs}
                        />
                    </Stack>
                </Stack>
                {stale && dashboard && (
                    <Alert severity="warning">{t('event.liveDashboard.staleWarning')}</Alert>
                )}
                {stale && !dashboard && (
                    <Alert severity="error">{t('event.liveDashboard.loadError')}</Alert>
                )}
                {!dashboard && !stale && (
                    <Box display="flex" justifyContent="center" py={4}>
                        <CircularProgress />
                    </Box>
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
                                onTeamClick={handleTeamClick}
                                onFinish={mayControl ? handleFinish : undefined}
                                onSetRunning={mayControl ? handleSetRunning : undefined}
                            />
                        ))}
                        {runningMatches.length === 0 && nextUpcoming && (
                            <>
                                <Typography variant="subtitle2" color="text.secondary">
                                    {t('event.liveDashboard.nextUp')}
                                </Typography>
                                <LiveDashboardMatchCard
                                    match={nextUpcoming}
                                    onTeamClick={handleTeamClick}
                                    onSetRunning={mayControl ? handleSetRunning : undefined}
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
                                onTeamClick={handleTeamClick}
                                onFinish={mayControl ? handleFinish : undefined}
                                onSetRunning={mayControl ? handleSetRunning : undefined}
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
                                        onTeamClick={handleTeamClick}
                                        onFinish={mayControl ? handleFinish : undefined}
                                        onSetRunning={mayControl ? handleSetRunning : undefined}
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
            <LiveDashboardTeamDialog
                team={selectedTeam}
                matchId={selectedTeamRef?.matchId ?? null}
                eventId={eventId}
                onClose={() => setSelectedTeamRef(null)}
            />
            <Paper sx={{position: 'fixed', bottom: 0, left: 0, right: 0, zIndex: 10}} elevation={3}>
                <BottomNavigation
                    showLabels
                    value={tab}
                    onChange={(_, newTab: 'live' | 'matches') => {
                        tabRef.current = newTab
                        setTab(newTab)
                        // Der andere Tab hat einen anderen Umfang: der bisherige Stand samt ETag
                        // gilt für ihn nicht, sonst stünde kurz die Live-Auswahl als Gesamtliste da.
                        setDashboard(null)
                        etagRef.current = null
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

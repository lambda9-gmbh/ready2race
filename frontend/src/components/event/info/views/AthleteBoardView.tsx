import {ReactNode} from 'react'
import {Box, CircularProgress, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useAthleteBoardData} from '../athleteBoard/useAthleteBoardData'
import {useServerClock} from '../athleteBoard/useServerClock'
import AthleteBoardMatchCard from '../athleteBoard/AthleteBoardMatchCard'
import AthleteBoardResultCard from '../athleteBoard/AthleteBoardResultCard'

interface AthleteBoardViewProps {
    eventId: string
}

const STALE_AFTER_MISSED_INTERVALS = 2

const AthleteBoardView = ({eventId}: AthleteBoardViewProps) => {
    const {t} = useTranslation()
    const {data, lastUpdated, notFound, initialLoad, loadFailed} = useAthleteBoardData(eventId)
    const now = useServerClock(data?.serverTime)

    if (notFound) {
        return (
            <Box sx={{display: 'flex', height: '100%', alignItems: 'center', justifyContent: 'center', p: 3}}>
                <Typography variant="h5" color="text.secondary">
                    {t('event.info.athleteBoard.eventNotFound')}
                </Typography>
            </Box>
        )
    }

    if (initialLoad && !data) {
        return (
            <Box sx={{display: 'flex', height: '100%', alignItems: 'center', justifyContent: 'center'}}>
                <CircularProgress />
            </Box>
        )
    }

    // Es wurde noch nie erfolgreich geladen und der letzte Versuch ist fehlgeschlagen
    // (Backend tot, HTTP-Fehler, kein Netz). Das darf nicht wie "keine Läufe" aussehen —
    // ein montierter Bildschirm würde sonst bei totem Backend behaupten, es sei kein Lauf
    // auf dem Wasser.
    if (!data && loadFailed) {
        return (
            <Box sx={{display: 'flex', height: '100%', alignItems: 'center', justifyContent: 'center', p: 3}}>
                <Typography variant="h5" color="error">
                    {t('event.info.athleteBoard.loadError')}
                </Typography>
            </Box>
        )
    }

    // "Stand" wird bewusst aus der Serverzeit der letzten erfolgreichen Antwort abgeleitet
    // (nicht aus der Geräteuhr) — sonst widersprechen sich Uhrzeit links (Serveruhr) und
    // "Stand" rechts genau auf dem Bildschirm, für den die Serverzeit-Verankerung gedacht ist.
    const asOfTime = data ? new Date(data.serverTime) : null

    const staleThresholdMs =
        (data?.refreshIntervalSeconds ?? 15) * STALE_AFTER_MISSED_INTERVALS * 1000
    const stale =
        lastUpdated !== null && Date.now() - lastUpdated.getTime() > staleThresholdMs

    const column = (
        title: string,
        emptyText: string,
        items: ReactNode[],
    ) => (
        <Box sx={{flex: 1, minWidth: 0}}>
            <Typography
                sx={{
                    fontSize: 'clamp(1rem, 1.9vw, 1.8rem)',
                    fontWeight: 700,
                    mb: 1,
                    textTransform: 'uppercase',
                    letterSpacing: '0.04em',
                }}>
                {title}
            </Typography>
            {items.length > 0 ? (
                items
            ) : (
                <Typography
                    sx={{fontSize: 'clamp(0.85rem, 1.3vw, 1.1rem)'}}
                    color="text.secondary">
                    {emptyText}
                </Typography>
            )}
        </Box>
    )

    return (
        <Box sx={{height: '100%', overflow: 'auto', p: 'clamp(0.75rem, 1.5vw, 2rem)'}}>
            <Stack
                direction="row"
                justifyContent="space-between"
                alignItems="baseline"
                gap={2}
                sx={{mb: 2}}>
                <Typography sx={{fontSize: 'clamp(1.1rem, 2.2vw, 2.2rem)', fontWeight: 800}}>
                    {data?.eventName ?? ''}
                </Typography>
                <Stack alignItems="flex-end">
                    <Typography sx={{fontSize: 'clamp(1.1rem, 2.2vw, 2.2rem)', fontWeight: 800}}>
                        {now.toLocaleTimeString(undefined, {hour: '2-digit', minute: '2-digit'})}
                    </Typography>
                    {asOfTime && (
                        <Typography
                            sx={{fontSize: 'clamp(0.65rem, 1vw, 0.85rem)'}}
                            color={stale ? 'warning.main' : 'text.secondary'}>
                            {t('event.info.athleteBoard.asOf', {
                                time: asOfTime.toLocaleTimeString(undefined, {
                                    hour: '2-digit',
                                    minute: '2-digit',
                                }),
                            })}
                            {stale ? ` — ${t('event.info.athleteBoard.stale')}` : ''}
                        </Typography>
                    )}
                </Stack>
            </Stack>

            <Stack
                direction={{xs: 'column', lg: 'row'}}
                gap={{xs: 2, lg: 3}}
                alignItems="stretch">
                {column(
                    t('event.info.athleteBoard.running'),
                    t('event.info.athleteBoard.noRunning'),
                    (data?.running ?? []).map(match => (
                        <AthleteBoardMatchCard
                            key={match.matchId}
                            match={match}
                            now={now}
                            variant="running"
                        />
                    )),
                )}
                {column(
                    t('event.info.athleteBoard.upcoming'),
                    t('event.info.athleteBoard.noUpcoming'),
                    (data?.upcoming ?? []).map(match => (
                        <AthleteBoardMatchCard
                            key={match.matchId}
                            match={match}
                            now={now}
                            variant="upcoming"
                            showCountdown={data?.showCountdown ?? true}
                        />
                    )),
                )}
                {column(
                    t('event.info.athleteBoard.results'),
                    t('event.info.athleteBoard.noResults'),
                    (data?.results ?? []).map(result => (
                        <AthleteBoardResultCard key={result.matchId} result={result} />
                    )),
                )}
            </Stack>
        </Box>
    )
}

export default AthleteBoardView

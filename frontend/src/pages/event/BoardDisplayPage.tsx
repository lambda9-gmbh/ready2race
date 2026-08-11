import {useCallback, useEffect, useRef, useState} from 'react'
import {Box, CircularProgress, Fade, IconButton, Stack, Typography} from '@mui/material'
import {
    Fullscreen as FullscreenIcon,
    FullscreenExit as FullscreenExitIcon,
} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import BoardRenderer from '@components/event/board/BoardRenderer'
import {useBoardViewData} from '@components/event/board/useBoardViewData'
import {useServerClock} from '@components/event/info/athleteBoard/useServerClock'
import {boardDisplayRoute} from '@routes'

const STALE_AFTER_MISSED_INTERVALS = 2

/**
 * Die öffentliche Anzeige eines Boards — Nachfolgerin der Athleten-Board-Seite. Ohne
 * Anmeldung und ohne Bedienelemente außer Vollbild: ein fest montierter Bildschirm hat
 * keine Maus, und auf dem Telefon ist eine Seite, die nur zeigt, schneller verstanden.
 * Vollbild läuft über die Fullscreen-API des Browsers (Taste f oder der Knopf, der
 * sich nach ein paar Sekunden versteckt).
 */
const BoardDisplayPage = () => {
    const {t} = useTranslation()
    const {eventId, boardId} = boardDisplayRoute.useParams()

    const {data, lastUpdated, notFound, initialLoad, loadFailed} = useBoardViewData(
        eventId,
        boardId,
    )
    const now = useServerClock(data?.serverTime)

    const [fullscreen, setFullscreen] = useState(false)
    const [showControls, setShowControls] = useState(true)
    const hideTimer = useRef<number | null>(null)

    const toggleFullscreen = useCallback(() => {
        if (document.fullscreenElement) {
            void document.exitFullscreen()
        } else {
            void document.documentElement.requestFullscreen()
        }
    }, [])

    useEffect(() => {
        const onChange = () => setFullscreen(document.fullscreenElement != null)
        document.addEventListener('fullscreenchange', onChange)
        return () => document.removeEventListener('fullscreenchange', onChange)
    }, [])

    useEffect(() => {
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'f') toggleFullscreen()
        }
        window.addEventListener('keydown', onKey)
        return () => window.removeEventListener('keydown', onKey)
    }, [toggleFullscreen])

    const handleMouseMove = useCallback(() => {
        setShowControls(true)
        if (hideTimer.current) window.clearTimeout(hideTimer.current)
        hideTimer.current = window.setTimeout(() => setShowControls(false), 5000)
    }, [])

    useEffect(
        () => () => {
            if (hideTimer.current) window.clearTimeout(hideTimer.current)
        },
        [],
    )

    if (notFound) {
        return (
            <Box
                sx={{
                    display: 'flex',
                    height: '100dvh',
                    alignItems: 'center',
                    justifyContent: 'center',
                    p: 3,
                }}>
                <Typography variant="h5" color="text.secondary">
                    {t('event.boards.notFound')}
                </Typography>
            </Box>
        )
    }

    if (initialLoad && !data) {
        return (
            <Box
                sx={{
                    display: 'flex',
                    height: '100dvh',
                    alignItems: 'center',
                    justifyContent: 'center',
                }}>
                <CircularProgress />
            </Box>
        )
    }

    // Es wurde noch nie erfolgreich geladen und der letzte Versuch ist fehlgeschlagen
    // (Backend tot, HTTP-Fehler, kein Netz). Das darf nicht wie "keine Läufe" aussehen —
    // ein montierter Bildschirm würde sonst bei totem Backend behaupten, es sei kein
    // Lauf in der Arena.
    if (!data && loadFailed) {
        return (
            <Box
                sx={{
                    display: 'flex',
                    height: '100dvh',
                    alignItems: 'center',
                    justifyContent: 'center',
                    p: 3,
                }}>
                <Typography variant="h5" color="error">
                    {t('event.info.athleteBoard.loadError')}
                </Typography>
            </Box>
        )
    }

    if (!data) return null

    // "Stand" wird bewusst aus der Serverzeit der letzten erfolgreichen Antwort
    // abgeleitet (nicht aus der Geräteuhr) — sonst widersprechen sich die Serveruhr der
    // Uhr-Elemente und die "Stand"-Zeile genau auf dem Bildschirm, für den die
    // Verankerung gedacht ist.
    const asOfTime = new Date(data.serverTime)
    const staleThresholdMs =
        (data.refreshIntervalSeconds > 0 ? data.refreshIntervalSeconds : 15) *
        STALE_AFTER_MISSED_INTERVALS *
        1000
    // Beide Bedingungen, nicht nur die Uhr: Im Hintergrund pausiert das Polling bewusst,
    // dabei altert lastUpdated ohne dass die Verbindung gestört wäre. Erst ein
    // tatsächlich fehlgeschlagener Abruf macht aus dem Altern eine Warnung.
    const stale =
        loadFailed && lastUpdated !== null && Date.now() - lastUpdated.getTime() > staleThresholdMs

    const showHeader = data.config.showHeader !== false

    return (
        <Box
            onMouseMove={handleMouseMove}
            sx={{
                height: {xs: 'auto', lg: '100dvh'},
                overflow: {xs: 'visible', lg: 'hidden'},
                position: 'relative',
                display: 'grid',
                gridTemplateRows: showHeader ? 'auto minmax(0, 1fr)' : 'minmax(0, 1fr)',
            }}>
            {/* Der Kopf der alten Bühne: Veranstaltungsname links, Serveruhr rechts,
                darunter die "Stand"-Zeile. Je Board abschaltbar (showHeader). */}
            {showHeader && (
                <Stack
                    direction="row"
                    justifyContent="space-between"
                    alignItems="baseline"
                    gap={2}
                    sx={{
                        pl: 'clamp(0.5rem, 1vw, 1.5rem)',
                        // Rechts liegt der (auto-versteckende) Vollbild-Knopf über der
                        // Ecke — die Uhr rückt ein Stück ein, statt darunter zu liegen.
                        pr: 'clamp(3rem, 4vw, 4.5rem)',
                        pt: 'clamp(0.4rem, 0.9vh, 1rem)',
                    }}>
                    <Typography sx={{fontSize: 'clamp(1rem, 1.8vw, 3rem)', fontWeight: 800}} noWrap>
                        {data.eventName}
                    </Typography>
                    <Stack alignItems="flex-end" sx={{flexShrink: 0}}>
                        <Typography sx={{fontSize: 'clamp(1rem, 1.8vw, 2rem)', fontWeight: 800}}>
                            {now.toLocaleTimeString(undefined, {
                                hour: '2-digit',
                                minute: '2-digit',
                            })}
                        </Typography>
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
                    </Stack>
                </Stack>
            )}

            <BoardRenderer view={data} now={now} />

            {/* Ohne Kopf bleibt die Störung trotzdem sichtbar: dezente Ecke unten rechts. */}
            {!showHeader && stale && (
                <Typography
                    sx={{
                        position: 'absolute',
                        bottom: 8,
                        right: 12,
                        fontSize: 'clamp(0.65rem, 1vw, 0.9rem)',
                        fontWeight: 600,
                    }}
                    color="warning.main">
                    {t('event.info.athleteBoard.asOf', {
                        time: asOfTime.toLocaleTimeString(undefined, {
                            hour: '2-digit',
                            minute: '2-digit',
                        }),
                    })}
                    {` — ${t('event.info.athleteBoard.stale')}`}
                </Typography>
            )}

            <Fade in={showControls}>
                <IconButton
                    onClick={toggleFullscreen}
                    sx={{
                        position: 'absolute',
                        top: 12,
                        right: 12,
                        bgcolor: 'background.paper',
                        boxShadow: 1,
                        '&:hover': {bgcolor: 'background.paper'},
                    }}
                    aria-label={
                        fullscreen ? t('common.exitFullscreen') : t('common.fullscreen')
                    }>
                    {fullscreen ? <FullscreenExitIcon /> : <FullscreenIcon />}
                </IconButton>
            </Fade>
        </Box>
    )
}

export default BoardDisplayPage

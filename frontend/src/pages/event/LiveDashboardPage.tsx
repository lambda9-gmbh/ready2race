import {useEffect, useRef, useState} from 'react'
import {
    Alert,
    Badge,
    BottomNavigation,
    BottomNavigationAction,
    Box,
    CircularProgress,
    IconButton,
    Paper,
    Stack,
    Typography,
    useMediaQuery,
    useTheme,
} from '@mui/material'
import LiveTvIcon from '@mui/icons-material/LiveTv'
import FormatListNumberedIcon from '@mui/icons-material/FormatListNumbered'
import {ArrowBack} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import {
    addLiveDashboardTeamNote,
    deleteLiveDashboardTeamNote,
    finishLiveDashboardMatch,
    getEventTimingConfig,
    getLiveDashboard,
    resumeRaceClockerAutoPull,
    setLiveDashboardMatchActivated,
    skipScheduleSlot,
    startLiveDashboardMatch,
} from '@api/sdk.gen.ts'
import {LiveDashboardDto} from '@api/types.gen.ts'
import {useFetch, useFeedback} from '@utils/hooks.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {updateLiveDashboardGlobal} from '@authorization/privileges.ts'
import LiveDashboardTeamDialog from '@components/event/liveDashboard/LiveDashboardTeamDialog.tsx'
import EventNoticeBanner from '@components/eventNotice/EventNoticeBanner.tsx'
import RefreshCountdown from '@components/event/liveDashboard/RefreshCountdown.tsx'
import DashboardSettingsPopover from '@components/event/liveDashboard/DashboardSettingsPopover.tsx'
import {useShortLabels} from '@components/event/shortLabels.ts'
import {DASHBOARD_COMPACT_KEY, useDeviceFlag} from '@components/event/deviceSettings.ts'
import {
    LiveColumn,
    LiveDashboardActions,
    MatchListColumn,
} from '@components/event/liveDashboard/LiveDashboardColumns.tsx'
import {
    buildLiveDashboardTimeline,
    centeredScrollTop,
    dashboardCrew,
    dashboardEntryDomIdCandidates,
    dashboardScope,
    LiveDashboardTab,
    liveMatches,
    nextUpEntry,
    storedPollInterval,
} from '@components/event/liveDashboard/common.ts'
import ScheduleTimelineIndicator from '@components/event/schedule/ScheduleTimelineIndicator.tsx'
import {
    dashboardEntriesForDay,
    resolveDashboardDay,
} from '@components/event/schedule/timelineIndicator.ts'
import {MatchResultStatus} from '@utils/matchResultStatus.ts'
import {liveDashboardErrorKey} from '@components/event/liveDashboard/liveDashboardError.ts'
import {readCachedRead, writeCachedRead} from '@pwa/readCache.ts'
import {describeStale} from '@components/event/liveDashboard/staleState.ts'
import {useDocumentTitle} from '@utils/useDocumentTitle.ts'

/** The dashboard payload carries no server clock of its own (unlike the athlete board), so the
 * now-marker ticks off the local clock every 30s - plenty for a position on a day-long axis. */
const useLocalClock = (intervalMs: number): Date => {
    const [now, setNow] = useState(() => new Date())
    useEffect(() => {
        const id = window.setInterval(() => setNow(new Date()), intervalMs)
        return () => window.clearInterval(id)
    }, [intervalMs])
    return now
}

/**
 * Ob der Abruf die Crew je Boot mitbestellen soll. Beobachtet wird die Fensterbreite, entschieden
 * wird in `dashboardCrew` — die Schwelle steht als reine Funktion in `common.ts` und ist dort ohne
 * Rendering geprüft. Gehalten wird nur der Schalter, nicht die Breite: ein Zustandswechsel je
 * gezogenem Pixel würde die Seite grundlos neu bauen.
 */
const useCrewRequested = (): boolean => {
    const [requested, setRequested] = useState(() => dashboardCrew(window.innerWidth))
    useEffect(() => {
        const onResize = () => setRequested(dashboardCrew(window.innerWidth))
        window.addEventListener('resize', onResize)
        return () => window.removeEventListener('resize', onResize)
    }, [])
    return requested
}

/**
 * Der nächste Vorfahr, der selbst scrollt — breit ist das die „Läufe"-Spalte (overflowY: auto),
 * schmal gibt es keinen und das Fenster übernimmt (dann null). Die Prüfung auf
 * scrollHeight > clientHeight lässt Boxen aus, die zwar overflow gesetzt haben, aber mit ihrem
 * Inhalt wachsen — etwa den Seitenrahmen, dessen overflowX: hidden das berechnete overflowY auf
 * auto hebt, ohne dass er je scrollt. body/html bleiben außen vor: dort scrollt das Fenster, und
 * dafür ist scrollIntoView der richtige Weg.
 */
const scrollContainerOf = (el: HTMLElement): HTMLElement | null => {
    for (let parent = el.parentElement; parent; parent = parent.parentElement) {
        if (parent === document.body || parent === document.documentElement) {
            return null
        }
        const overflowY = window.getComputedStyle(parent).overflowY
        if (
            (overflowY === 'auto' || overflowY === 'scroll') &&
            parent.scrollHeight > parent.clientHeight
        ) {
            return parent
        }
    }
    return null
}

export type LiveDashboardPageProps = {
    eventId: string
    /**
     * Legt den zuletzt geladenen Stand auf dem Gerät ab und zeigt ihn ohne Verbindung weiter.
     *
     * Nur die Helfer-App schaltet das ein. Am Arbeitsplatzrechner der Verwaltung hätte es keinen
     * Nutzen, würde aber dauerhaft Teilnehmerdaten mit Klarnamen dort ablegen - die Abwägung in
     * der Spezifikation stützt sich ausdrücklich auf den Betrieb am Steg.
     */
    cacheReads?: boolean
    /**
     * Zeigt links in der Kopfzeile einen Zurück-Pfeil. Nur die Helfer-App setzt das: in der
     * installierten PWA (standalone, ohne Browser-Leiste) fehlt der Browser-Zurück-Knopf,
     * ohne den Pfeil säße man im Dashboard fest (beobachtet am 10.08.2026). Ohne `onBack`
     * (Verwaltungsoberfläche) bleibt die Kopfzeile unverändert.
     */
    onBack?: () => void
}

const LiveDashboardPage = ({eventId, cacheReads = false, onBack}: LiveDashboardPageProps) => {
    const {t} = useTranslation()
    useDocumentTitle(t('event.liveDashboard.title'))
    const feedback = useFeedback()
    const {confirmAction} = useConfirmation()
    const user = useUser()
    const theme = useTheme()
    const mayControl = user.checkPrivilege(updateLiveDashboardGlobal)
    const now = useLocalClock(30_000)
    // Ab hier ist Platz für beide Ansichten nebeneinander, der Umschalter entfällt. Derselbe Bruch,
    // an dem RootLayout von Drawer auf feste Seitenleiste wechselt.
    const wide = useMediaQuery(theme.breakpoints.up('md'))

    const [tab, setTab] = useState<LiveDashboardTab>('live')
    const [pollIntervalMs, setPollIntervalMs] = useState(storedPollInterval)
    // Geteilt mit dem Zeitplan-Tab (siehe shortLabels.ts); hier startet es in der Kurzform.
    const [shortLabels, toggleShortLabels] = useShortLabels(true)
    // Geräte-lokal (siehe deviceSettings.ts): dichtere Karten und kleinere Schrift — eine dezente
    // CSS-Stufe für kleine Bildschirme am Steg, umgeschaltet im Einstellungs-Popover.
    const [compact, setCompact] = useDeviceFlag(DASHBOARD_COMPACT_KEY)
    const cacheUserId = user.loggedIn ? user.id : ''
    // Einmal lesen, dreifach verwenden: Der Startwert aller drei Zustände kommt aus demselben
    // Eintrag, und der useState-Initialisierer läuft nur beim ersten Rendern.
    const [cachedStart] = useState(() =>
        cacheReads ? readCachedRead<LiveDashboardDto>('dashboard', cacheUserId, eventId) : null,
    )
    const [dashboard, setDashboard] = useState<LiveDashboardDto | null>(
        cachedStart?.payload ?? null,
    )
    // In REGATTABUERO läuft "Lauf beenden" ausschließlich über den Zeitplan-Tab (siehe
    // EventSchedule.tsx) - der Button verschwindet hier dafür, das Notfall-Override
    // (onSetActivated) bleibt unabhängig vom Modus verfügbar.
    const mayFinish = mayControl && dashboard?.chainProgressionMode !== 'REGATTABUERO'
    const [lastUpdated, setLastUpdated] = useState<Date | null>(
        cachedStart ? new Date(cachedStart.fetchedAt) : null,
    )
    // Ein Stand aus dem Cache gilt bis zum ersten erfolgreichen Abruf als veraltet - sonst
    // stünden die Aktionen auf Daten von vorhin offen.
    const [stale, setStale] = useState(cachedStart !== null)
    const [liveChanged, setLiveChanged] = useState(false)
    const [selectedTeamRef, setSelectedTeamRef] = useState<{
        matchId: string
        teamId: string
    } | null>(null)
    const runningIdsRef = useRef<string | null>(null)
    const tabRef = useRef<LiveDashboardTab>('live')
    const wideRef = useRef(wide)
    /** Stand der letzten Antwort; spart den Rumpf, solange sich nichts geändert hat. */
    const etagRef = useRef<string | null>(null)

    const scope = dashboardScope(wide, tab)
    // Anders als `scope` räumt ein Wechsel hier nichts auf: der ETag ist der Fingerabdruck des
    // serialisierten Datensatzes (siehe respondETagged), und mit der Crew darin ist er ein anderer —
    // der nächste Abruf bringt den Rumpf von selbst. Bis er da ist, zeigen die Karten weiter
    // Stufe 2 (siehe teamShowsCrew), statt für eine bloße Ergänzung leer zu werden.
    const crew = useCrewRequested()

    // Der andere Umfang hat einen anderen Stand: ETag und Daten des bisherigen gelten für ihn
    // nicht, sonst stünde kurz die Live-Auswahl als Gesamtliste da. Am Umschalter hing das früher
    // allein — beim Überschreiten der Breite (Fenster gezogen, Tablet gedreht) gibt es den Klick
    // aber nicht.
    // Beim ersten Rendern darf das nicht greifen: Dort steht der Startwert aus dem Lese-Cache,
    // und ein useEffect läuft auch beim Montieren. Ohne diese Sperre wäre der zwischengespeicherte
    // Stand eine Renderphase später wieder weg - offline dauerhaft, online als roter Fehlerblitz
    // bei jedem Öffnen.
    const scopeRef = useRef(scope)
    useEffect(() => {
        if (scopeRef.current === scope) {
            return
        }
        scopeRef.current = scope
        setDashboard(null)
        etagRef.current = null
    }, [scope])

    // Breit ist "Live" immer sichtbar, der Hinweispunkt am Umschalter hätte nichts zu melden.
    useEffect(() => {
        wideRef.current = wide
        if (wide) {
            setLiveChanged(false)
        }
    }, [wide])

    const dashboardData = useFetch(
        signal =>
            getLiveDashboard({
                signal,
                path: {eventId},
                query: {scope, crew},
                // Unverändert? Dann antwortet der Server mit 304 und ohne Rumpf. 'no-store' hält
                // den Browser-Cache aus der Bedingung heraus, sonst beantwortet er sie selbst.
                headers: etagRef.current ? {'If-None-Match': etagRef.current} : undefined,
                cache: 'no-store',
            }),
        {
            autoReloadInterval: pollIntervalMs,
            deps: [eventId, pollIntervalMs, scope, crew],
            onResponse: ({data, response}) => {
                if (response.status === 304) {
                    setLastUpdated(new Date())
                    setStale(false)
                    return
                }
                if (data !== undefined) {
                    etagRef.current = response.headers.get('ETag')
                    setDashboard(data)
                    if (cacheReads) {
                        writeCachedRead('dashboard', cacheUserId, eventId, data)
                    }
                    setLastUpdated(new Date())
                    setStale(false)
                    // Auch ein Lauf, der neu auf sein Beenden wartet, ist eine Änderung im
                    // Live-Tab und soll den Hinweispunkt setzen.
                    const ids = liveMatches(data.matches)
                        .map(m => m.matchId)
                        .join(',')
                    if (
                        runningIdsRef.current !== null &&
                        ids !== runningIdsRef.current &&
                        !wideRef.current &&
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

    /**
     * Ob RaceClocker den Start dieser Veranstaltung ohnehin selbst meldet — daran hängt allein der
     * Hinweis am „Läuft"-Knopf, nicht der Knopf selbst. Einmal geladen, nicht im Abruftakt: die
     * Einstellung ändert sich am Renntag nicht.
     *
     * Scheitert der Abruf (die Zeitnahme-Voreinstellung verlangt `ReadEventGlobal`, und das hat
     * nicht jede Schiedsrichter-Rolle), bleibt der Hinweis weg. Das ist die richtige Richtung: ein
     * fehlender Hinweis kostet einen überflüssigen Klick, ein falscher ließe jemanden auf eine
     * Automatik warten, die es nicht gibt.
     */
    const timingConfigData = useFetch(signal => getEventTimingConfig({signal, path: {eventId}}), {
        deps: [eventId],
    })
    const raceClockerAutoPull =
        timingConfigData.data?.autoPull === true &&
        timingConfigData.data.timingSystem === 'RACECLOCKER'

    // Der Live-Tab zeigt, was jetzt eine Handlung verlangt: die laufenden Läufe UND die, die
    // vollständig gewertet auf ihr Beenden warten (siehe liveMatches / selectForScope im Backend).
    const currentMatches = liveMatches(dashboard?.matches ?? [])
    const nextUpcoming = dashboard?.matches.find(m => m.state === 'UPCOMING')
    const scheduledMatches = dashboard?.matches.filter(m => m.state !== 'UNSCHEDULED') ?? []
    const unscheduledMatches = dashboard?.matches.filter(m => m.state === 'UNSCHEDULED') ?? []
    const pendingSlots = dashboard?.pendingSlots ?? []
    // "Als Nächstes" ist das chronologisch nächste Ding überhaupt — das kann auch ein noch nicht
    // gesetzter Slot vor dem nächsten echten Lauf sein, solange dessen Startzeit nicht längst
    // vorbei ist (siehe nextUpEntry).
    const nextEntry = nextUpEntry(nextUpcoming, pendingSlots, now)
    // Zeitplan-Ansicht: geplante/laufende/beendete Läufe und wartende Slots gemeinsam nach
    // Startzeit, damit ein Platzhalter genau zwischen seinen Nachbarn auftaucht.
    const scheduledTimeline = buildLiveDashboardTimeline(scheduledMatches, pendingSlots)

    // Kompakter "wo stehen wir gerade"-Balken über den Listen: ein Tag, ausgewählt über den
    // ersten laufenden bzw. nächsten anstehenden Eintrag (Fallback: heute).
    const indicatorDay = resolveDashboardDay(dashboard?.matches ?? [], pendingSlots, now)
    const indicatorEntries = dashboardEntriesForDay(
        dashboard?.matches ?? [],
        pendingSlots,
        indicatorDay,
    )

    const scrollToTimelineEntry = (id: string) => {
        const el = dashboardEntryDomIdCandidates(id)
            .map(domId => document.getElementById(domId))
            .find(found => found !== null)
        if (!el) {
            return
        }
        const container = scrollContainerOf(el)
        if (container) {
            // Nur die Spalte scrollen: scrollIntoView nähme alle scrollbaren Vorfahren mit, also
            // auch das Fenster — Kopfzeile und Zeitstrahl rutschten dabei aus dem Bild (der
            // gemeldete Sprung des Zeitplans nach dem Klick). Deshalb die Mitte selbst rechnen
            // und gezielt einen einzigen Container fahren.
            const elementTop =
                el.getBoundingClientRect().top -
                container.getBoundingClientRect().top +
                container.scrollTop
            container.scrollTo({
                top: centeredScrollTop(
                    elementTop,
                    el.offsetHeight,
                    container.clientHeight,
                    container.scrollHeight,
                ),
                behavior: 'smooth',
            })
        } else {
            // Schmal scrollt keine Spalte in sich selbst — hier ist das Fenster der richtige
            // (und einzige) Scroller, scrollIntoView tut also genau das Gewollte.
            el.scrollIntoView({behavior: 'smooth', block: 'center'})
        }
        el.animate(
            [{backgroundColor: theme.palette.action.selected}, {backgroundColor: 'transparent'}],
            {duration: 1200, easing: 'ease-out'},
        )
    }

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

    const handleTeamClick = (matchId: string, teamId: string) =>
        setSelectedTeamRef({matchId, teamId})

    const handleFinish = async (matchId: string, openResults: MatchResultStatus | null) => {
        const {error} = await finishLiveDashboardMatch({
            path: {eventId, matchId},
            query: openResults ? {openResults} : undefined,
        })
        if (error) {
            // Der haeufigste Fall am Steg ist gar keine Stoerung: die Veranstaltung laeuft im
            // Modus REGATTABUERO, dort beendet das Buero ueber den Zeitplan.
            feedback.error(t(liveDashboardErrorKey(error) ?? 'event.liveDashboard.control.error'))
        } else {
            feedback.success(t('event.liveDashboard.control.finished'))
        }
        dashboardData.reload()
    }

    const handleSetActivated = async (matchId: string, activated: boolean) => {
        const {error} = await setLiveDashboardMatchActivated({
            path: {eventId, matchId},
            query: {activated},
        })
        if (error) {
            feedback.error(t('event.liveDashboard.control.error'))
        }
        dashboardData.reload()
    }

    /**
     * „Läuft": stellt fest, dass das Rennen unterwegs ist. Der Endpunkt ist idempotent und setzt
     * nur den Ist-Start — eine Zeitnahme löst er nicht aus.
     */
    const handleMarkStarted = async (matchId: string) => {
        const {error} = await startLiveDashboardMatch({path: {eventId, matchId}})
        if (error) {
            feedback.error(t('event.liveDashboard.control.error'))
        }
        dashboardData.reload()
    }

    /**
     * Gibt den automatischen RaceClocker-Abruf wieder frei. Der Knopf gehört hierher, weil das
     * Deaktivieren eines Laufs die Automatik pausiert und im Dashboard deaktiviert wird — im
     * Durchführungs-Tab käme der Schiedsrichter am Steg nicht vorbei.
     */
    const handleResumeAutoPull = async (matchId: string, competitionId: string) => {
        const {error} = await resumeRaceClockerAutoPull({
            path: {eventId, competitionId, competitionMatchId: matchId},
        })
        if (error) {
            feedback.error(t('event.liveDashboard.control.error'))
        } else {
            feedback.success(t('event.competition.execution.results.raceclocker.poll.resumed'))
        }
        dashboardData.reload()
    }

    /**
     * Notiz an ein Boot hängen - Kommunikation zwischen Schiedsrichtern, keine Wertung. Der
     * Dialog wartet auf das Promise, bevor er sein Eingabefeld leert; der anschließende Reload
     * bringt die neue Notiz über den Poll in team.notes.
     */
    const handleAddNote = async (matchId: string, teamId: string, note: string) => {
        const {error} = await addLiveDashboardTeamNote({
            path: {eventId, matchId, teamId},
            body: {note},
        })
        if (error) {
            feedback.error(t('event.liveDashboard.notes.error'))
        }
        dashboardData.reload()
    }

    const handleDeleteNote = async (matchId: string, teamId: string, noteId: string) => {
        const {error} = await deleteLiveDashboardTeamNote({
            path: {eventId, matchId, teamId, noteId},
        })
        if (error) {
            feedback.error(t('event.liveDashboard.notes.deleteError'))
        }
        dashboardData.reload()
    }

    const handleSkipSlot = (slotId: string, label: string, time: string) => {
        confirmAction(
            async () => {
                const {error} = await skipScheduleSlot({path: {eventId, slotId}})
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                }
                dashboardData.reload()
            },
            {
                content: t('event.schedule.skipConfirm', {label, time}),
                okText: t('event.schedule.skip'),
            },
        )
    }

    const staleState = describeStale(lastUpdated?.getTime() ?? null, stale, now.getTime())

    const actions: LiveDashboardActions = {
        onTeamClick: handleTeamClick,
        // Bei veraltetem Stand entfallen alle fünf schreibenden Handlungen: Die Karten blenden
        // ihre Knöpfe aus, sobald der Handler `undefined` ist. Niemand soll einen Lauf auf
        // Grundlage von Daten starten oder beenden, die aus dem Gerätespeicher stammen.
        onFinish: mayFinish && !staleState.actionsLocked ? handleFinish : undefined,
        onSetActivated: mayControl && !staleState.actionsLocked ? handleSetActivated : undefined,
        onMarkStarted: mayControl && !staleState.actionsLocked ? handleMarkStarted : undefined,
        onResumeAutoPull:
            mayControl && !staleState.actionsLocked ? handleResumeAutoPull : undefined,
        onSkipSlot: mayControl && !staleState.actionsLocked ? handleSkipSlot : undefined,
        // Kein Handler, sondern ein Kennzeichen der Veranstaltung - bleibt unabhängig vom Stand.
        raceClockerAutoPull,
    }

    const liveColumn = (
        <LiveColumn
            currentMatches={currentMatches}
            nextEntry={nextEntry}
            loaded={dashboard !== null}
            actions={actions}
            shortLabels={shortLabels}
        />
    )
    const matchListColumn = (
        <MatchListColumn
            scheduledTimeline={scheduledTimeline}
            unscheduledMatches={unscheduledMatches}
            empty={
                dashboard !== null && dashboard.matches.length === 0 && pendingSlots.length === 0
            }
            actions={actions}
            shortLabels={shortLabels}
        />
    )

    return (
        <Box
            sx={{
                // Platz für die fixierte Leiste — die gibt es nur schmal.
                pb: {xs: 9, md: 0},
                // width + maxWidth statt nur maxWidth: das Layout setzt die Seite als
                // Flex-Kind ein, das sonst mit seinem Inhalt über den Viewport wächst
                width: '100%',
                maxWidth: {xs: 700, md: 1400},
                mx: 'auto',
                minWidth: 0,
                // Schmal fängt das ausreißende Inhalte ab. Breit muss es weg: overflow macht diese
                // Box zum Scroll-Container und die klebende Live-Spalte darin wirkungslos.
                overflowX: {xs: 'hidden', md: 'visible'},
            }}>
            <Stack spacing={2} sx={{px: 1, py: 2, minWidth: 0}}>
                <Stack
                    direction="row"
                    justifyContent="space-between"
                    alignItems="baseline"
                    spacing={1}>
                    {/* Ohne onBack bleibt die Kopfzeile exakt wie bisher — der Wrapper existiert
                        nur, damit Pfeil und Titel zusammen als ein Flex-Kind links stehen. */}
                    {onBack ? (
                        <Stack
                            direction="row"
                            alignItems="center"
                            spacing={0.5}
                            sx={{minWidth: 0}}>
                            <IconButton
                                size="small"
                                onClick={onBack}
                                aria-label={t('common.back')}
                                sx={{flexShrink: 0}}>
                                <ArrowBack fontSize="small" />
                            </IconButton>
                            <Typography
                                variant="subtitle1"
                                fontWeight={700}
                                sx={{minWidth: 0, lineHeight: 1.2}}>
                                {t('event.liveDashboard.title')}
                            </Typography>
                        </Stack>
                    ) : (
                        <Typography
                            variant="subtitle1"
                            fontWeight={700}
                            sx={{minWidth: 0, lineHeight: 1.2}}>
                            {t('event.liveDashboard.title')}
                        </Typography>
                    )}
                    <Stack direction="row" spacing={0.5} alignItems="center" flexShrink={0}>
                        {lastUpdated && (
                            <Typography variant="caption" noWrap sx={{color: 'grey.700'}}>
                                {format(lastUpdated, t('format.timeWithSeconds'))}
                            </Typography>
                        )}
                        {/* Nur noch Anzeige — den Takt wählt das Einstellungs-Popover daneben. */}
                        <RefreshCountdown intervalMs={pollIntervalMs} lastUpdated={lastUpdated} />
                        <DashboardSettingsPopover
                            shortLabels={shortLabels}
                            toggleShortLabels={toggleShortLabels}
                            pollIntervalMs={pollIntervalMs}
                            onPollIntervalChange={setPollIntervalMs}
                            compact={compact}
                            setCompact={setCompact}
                        />
                    </Stack>
                </Stack>
                {/* Der veranstaltungsweite Hinweis (z.B. Wetterwarnung) — er hängt am selben
                    Poll wie das Dashboard und erscheint damit ohne Neuladen. */}
                <EventNoticeBanner notice={dashboard?.notice} />
                {staleState.show && dashboard && (
                    <Alert severity="warning">
                        {staleState.fromCache && lastUpdated
                            ? t('event.liveDashboard.staleSince', {
                                  time: format(lastUpdated, t('format.datetime')),
                              })
                            : t('event.liveDashboard.staleWarning')}
                    </Alert>
                )}
                {stale && !dashboard && (
                    <Alert severity="error">{t('event.liveDashboard.loadError')}</Alert>
                )}
                {!dashboard && !stale && (
                    <Box display="flex" justifyContent="center" py={4}>
                        <CircularProgress />
                    </Box>
                )}
                {dashboard && indicatorEntries.length > 0 && (
                    <ScheduleTimelineIndicator
                        entries={indicatorEntries}
                        now={now}
                        onEntryClick={scrollToTimelineEntry}
                    />
                )}
                {/* Kompakt ist eine reine CSS-Stufe über den Karten: dichteres Karten-Padding und
                    eine Schriftgröße kleiner je Variante. Die Karten selbst wissen davon nichts —
                    ihre Zeilenlogik (Container-Queries, Spalten) bleibt unangetastet. */}
                <Box
                    sx={
                        compact
                            ? {
                                  '& .MuiCardContent-root': {p: 0.75, '&:last-child': {pb: 0.5}},
                                  '& .MuiTypography-subtitle1': {fontSize: '0.875rem'},
                                  '& .MuiTypography-body2': {fontSize: '0.8rem'},
                                  '& .MuiTypography-caption': {fontSize: '0.7rem'},
                              }
                            : undefined
                    }>
                    {wide ? (
                        <Box
                            sx={{
                                display: 'grid',
                                gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1fr)',
                                gap: 2,
                                alignItems: 'start',
                            }}>
                            {/* Der laufende Lauf bleibt im Blick, während nebenan durch die
                            Gesamtliste gescrollt wird. */}
                            <Stack
                                spacing={2}
                                sx={{
                                    minWidth: 0,
                                    position: 'sticky',
                                    top: 16,
                                    maxHeight: 'calc(100vh - 32px)',
                                    overflowY: 'auto',
                                }}>
                                <Typography variant="subtitle1" fontWeight={700}>
                                    {t('event.liveDashboard.tabs.live')}
                                </Typography>
                                {liveColumn}
                            </Stack>
                            {/* „Läufe" scrollt in sich selbst, statt die ganze Seite in die Länge zu
                            ziehen (Rückmeldung vom 10.08.2026) - der Überlauf greift erst, wenn die
                            Liste höher ist als das Fenster, kurze Listen stehen also ruhig. Die
                            Kopfzeile bleibt beim Scrollen oben. */}
                            <Stack
                                spacing={2}
                                sx={{
                                    minWidth: 0,
                                    position: 'sticky',
                                    top: 16,
                                    maxHeight: 'calc(100vh - 32px)',
                                    overflowY: 'auto',
                                }}>
                                <Typography
                                    variant="subtitle1"
                                    fontWeight={700}
                                    sx={{
                                        position: 'sticky',
                                        top: 0,
                                        bgcolor: 'background.default',
                                        zIndex: 1,
                                    }}>
                                    {t('event.liveDashboard.tabs.matches')}
                                </Typography>
                                {matchListColumn}
                            </Stack>
                        </Box>
                    ) : tab === 'live' ? (
                        liveColumn
                    ) : (
                        matchListColumn
                    )}
                </Box>
            </Stack>
            <LiveDashboardTeamDialog
                team={selectedTeam}
                matchId={selectedTeamRef?.matchId ?? null}
                eventId={eventId}
                onClose={() => setSelectedTeamRef(null)}
                // Dasselbe Muster wie die fünf Schreibaktionen oben: bei veraltetem Stand
                // entfallen die Handler, und der Dialog zeigt die Notizen nur noch an.
                onAddNote={mayControl && !staleState.actionsLocked ? handleAddNote : undefined}
                onDeleteNote={
                    mayControl && !staleState.actionsLocked ? handleDeleteNote : undefined
                }
            />
            {/* Nur schmal: breit stehen beide Ansichten nebeneinander, eine über die ganze
                Fensterbreite geklebte Telefonleiste hätte dort nichts zu schalten. */}
            {!wide && (
                <Paper
                    sx={{position: 'fixed', bottom: 0, left: 0, right: 0, zIndex: 10}}
                    elevation={3}>
                    <BottomNavigation
                        showLabels
                        value={tab}
                        onChange={(_, newTab: LiveDashboardTab) => {
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
            )}
        </Box>
    )
}

export default LiveDashboardPage

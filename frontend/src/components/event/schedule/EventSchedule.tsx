import {useEffect, useRef, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {
    Box,
    Button,
    Chip,
    ChipProps,
    IconButton,
    Stack,
    Tab,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Tabs,
    Tooltip,
    Typography,
} from '@mui/material'
import {
    Add,
    Delete,
    DirectionsRun,
    Edit,
    EventBusy,
    EventRepeat,
    OpenInNew,
    PlayArrow,
    Replay,
    Stop,
    Undo,
} from '@mui/icons-material'
import {format} from 'date-fns'
import {Link} from '@tanstack/react-router'
import {eventRoute} from '@routes'
import {
    activateScheduleSlot,
    deleteScheduleSlot,
    finishScheduleSlot,
    getEventSchedule,
    markMatchStartedFromExecution,
    reopenMatch,
    skipScheduleSlot,
    unskipScheduleSlot,
    updateMatchActivation,
} from '@api/sdk.gen.ts'
import {EventDto, EventScheduleSlotDto, UnplannedSetupMatchDto} from '@api/types.gen.ts'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateEventGlobal} from '@authorization/privileges.ts'
import Throbber from '@components/Throbber.tsx'
import {
    advanceOffer,
    competitionTag,
    groupSlotsByDay,
    isCancellable,
    isEditable,
    slotLabel,
    slotsInRound,
} from './common.ts'
import {ScheduleApiError, slotActionErrorText, slotActionUnexpectedKey} from './scheduleError.ts'
import {useShortLabels} from '@components/event/shortLabels.ts'
import {EXECUTION_NEW_TAB_KEY, useDeviceFlag} from '@components/event/deviceSettings.ts'
import {scheduleSlotsToEntries} from './timelineIndicator.ts'
import {delayParts, latestStartDelaySeconds} from '@utils/scheduleDelay.ts'
import {
    matchStatusChip,
    slotMatchStatus,
    unplannedMatchStatus,
} from '@components/event/match/matchStatusChip.ts'
import {byeExplanation} from '@components/event/match/matchBye.ts'
import ScheduleStartlistExportButton from './ScheduleStartlistExportButton.tsx'
import ScheduleSettingsPopover from './ScheduleSettingsPopover.tsx'
import ScheduleSlotDialog from './ScheduleSlotDialog.tsx'
import ScheduleShiftDialog from './ScheduleShiftDialog.tsx'
import ScheduleAdvanceDialog from './ScheduleAdvanceDialog.tsx'
import ScheduleImportDialog from './ScheduleImportDialog.tsx'
import ScheduleTimelineIndicator from './ScheduleTimelineIndicator.tsx'

/** Clock for the timeline's now-marker: the Zeitplan tab has no server-time feed of its own, so a
 * locally ticking clock (refreshed every 30s, plenty for a position marker) stands in for it. */
const useLocalClock = (intervalMs: number): Date => {
    const [now, setNow] = useState(() => new Date())
    useEffect(() => {
        const id = window.setInterval(() => setNow(new Date()), intervalMs)
        return () => window.clearInterval(id)
    }, [intervalMs])
    return now
}

/** Einheitliche Breite eines Aktions-Platzes (IconButton size=small: 20px Icon + 2×5px Padding). */
const actionSlotSx = {
    width: 30,
    height: 30,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
} as const

/**
 * Der Chip in der Status-Spalte.
 *
 * Ist der Slot mit einem Lauf verknüpft, entscheidet der Lauf-Status — bis hierher stand dort nur
 * "Verknüpft", eine Aussage über den Plan statt über den Lauf. Programmpunkte und wartende Runden
 * haben keinen Lauf und behalten deshalb unverändert ihren Slot-Chip.
 *
 * [now] speist nur die Anzeige ("Überfällig", verstrichene Minuten); der Zustand selbst kommt vom
 * Server.
 */
const stateChipProps = (
    slot: EventScheduleSlotDto,
    now: Date,
    t: (key: string, values?: Record<string, string | number>) => string,
): {label: string; color: ChipProps['color']; sx?: ChipProps['sx']} => {
    const matchStatus = slotMatchStatus(slot)
    if (matchStatus) {
        const chip = matchStatusChip(matchStatus, slot.startTime, now)
        return {
            label: t(chip.labelKey, chip.values),
            color: chip.color,
            sx: chip.strikeThrough ? {textDecoration: 'line-through'} : undefined,
        }
    }
    if (slot.matchFinishedAt) {
        return {label: t('event.schedule.state.finished'), color: 'success'}
    }
    switch (slot.state) {
        case 'WAITING':
            return {label: t('event.schedule.state.WAITING'), color: 'warning'}
        case 'LINKED':
            return {label: t('event.schedule.state.LINKED'), color: 'primary'}
        case 'OBSOLETE':
            return {
                label: t('event.schedule.state.OBSOLETE'),
                color: 'default',
                sx: {textDecoration: 'line-through'},
            }
        case 'SKIPPED':
            return {label: t('event.schedule.state.SKIPPED'), color: 'default'}
        case 'FREE':
        default:
            return {label: t('event.schedule.state.FREE'), color: 'default'}
    }
}

type Props = {
    /** Die geladene Veranstaltung — das Einstellungs-Popover schreibt seine Felder an ihr fest. */
    event: EventDto
    /** Lädt die Event-Daten der Seite neu, nachdem das Popover gespeichert hat. */
    reloadEvent: () => void
}

const EventSchedule = ({event, reloadEvent}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()
    const {confirmAction} = useConfirmation()
    const {eventId} = eventRoute.useParams()

    const canEdit = user.checkPrivilege(updateEventGlobal)

    const [lastRequested, setLastRequested] = useState(Date.now())
    const reload = () => setLastRequested(Date.now())

    // Der Zeitplan zieht sich selbst nach: Am Regattatag arbeiten Schiedsrichter-Dashboard und
    // Kette an denselben Läufen, und ein Zeitplan, der nur bei eigenen Aktionen neu lädt, zeigte
    // deren Stand erst nach einem Reload (beobachtet am 10.08.2026). 30 Sekunden reichen - für
    // die Sekunden-Frische ist das Dashboard da - und ein verdeckter Tab fragt gar nicht erst.
    useEffect(() => {
        const id = window.setInterval(() => {
            if (document.visibilityState === 'visible') {
                setLastRequested(Date.now())
            }
        }, 30_000)
        return () => window.clearInterval(id)
    }, [])

    // Tages-Auswahl: null heißt "noch keine Wahl getroffen" - dann gewinnt der heutige Tag, wenn
    // die Veranstaltung heute läuft, sonst alle Tage. Erst ein Klick legt die Wahl fest.
    const [selectedDay, setSelectedDay] = useState<string | null>(null)

    const [dialogOpen, setDialogOpen] = useState(false)
    const [editingSlot, setEditingSlot] = useState<EventScheduleSlotDto | undefined>(undefined)
    const [presetMatch, setPresetMatch] = useState<UnplannedSetupMatchDto | undefined>(undefined)

    const [shiftDialogOpen, setShiftDialogOpen] = useState(false)
    const [shiftDaySlots, setShiftDaySlots] = useState<EventScheduleSlotDto[]>([])

    // Der eben entfallene Slot, solange das Vorziehen angeboten wird - undefined heißt "kein
    // offenes Angebot".
    const [advanceSlot, setAdvanceSlot] = useState<EventScheduleSlotDto | undefined>(undefined)

    const [importDialogOpen, setImportDialogOpen] = useState(false)

    // Geteilt mit dem Schiedsrichter-Board (siehe shortLabels.ts). Seit dem 11.08.2026 startet
    // auch der Zeitplan in der Kurzform - die vollen Wettkampfnamen sprengten jede Zeile, und wer
    // sie will, schaltet einmal um und behält das überall. Umgeschaltet wird seit dem 11.08.2026
    // im Einstellungs-Popover der Kopfzeile statt am Spaltenkopf "Slot".
    const [shortLabels, toggleShortLabels] = useShortLabels(true)

    // Geräte-lokal (siehe deviceSettings.ts): ob der Sprung "Zur Durchführung" ein neues Fenster
    // öffnet. Am Regattatag lebt der Zeitplan oft auf einem eigenen Bildschirm - der Sprung soll
    // ihn dann nicht wegnavigieren.
    const [openExecutionInNewTab, setOpenExecutionInNewTab] = useDeviceFlag(EXECUTION_NEW_TAB_KEY)

    const now = useLocalClock(30_000)
    const rowRefs = useRef(new Map<string, HTMLTableRowElement>())
    const [highlightedSlotId, setHighlightedSlotId] = useState<string | null>(null)
    const highlightTimeoutRef = useRef<number | null>(null)

    const scrollToSlot = (slotId: string) => {
        rowRefs.current.get(slotId)?.scrollIntoView({behavior: 'smooth', block: 'center'})
        if (highlightTimeoutRef.current != null) {
            window.clearTimeout(highlightTimeoutRef.current)
        }
        setHighlightedSlotId(slotId)
        highlightTimeoutRef.current = window.setTimeout(() => setHighlightedSlotId(null), 1500)
    }

    useEffect(
        () => () => {
            if (highlightTimeoutRef.current != null) {
                window.clearTimeout(highlightTimeoutRef.current)
            }
        },
        [],
    )

    const {data, pending} = useFetch(signal => getEventSchedule({signal, path: {eventId}}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(t('common.load.error.single', {entity: t('event.schedule.tab')}))
            }
        },
        deps: [eventId, lastRequested],
    })

    // Warum die Slot-Aktion abgelehnt wurde, statt des bisherigen "Es ist ein Fehler aufgetreten".
    // [fallback] erlaubt der aufrufenden Aktion, für den unbekannten Rest bei ihrer eigenen,
    // spezifischeren Sammelmeldung zu bleiben (z. B. "Löschen fehlgeschlagen").
    const showSlotActionError = (error: ScheduleApiError, fallback?: string) => {
        const {key, values} = slotActionErrorText(error)
        feedback.error(
            key === slotActionUnexpectedKey && fallback !== undefined ? fallback : t(key, values),
        )
    }

    const openAddDialog = () => {
        setEditingSlot(undefined)
        setPresetMatch(undefined)
        setDialogOpen(true)
    }

    const openEditDialog = (slot: EventScheduleSlotDto) => {
        setEditingSlot(slot)
        setPresetMatch(undefined)
        setDialogOpen(true)
    }

    const openPlanDialog = (match: UnplannedSetupMatchDto) => {
        setEditingSlot(undefined)
        setPresetMatch(match)
        setDialogOpen(true)
    }

    const closeDialog = () => setDialogOpen(false)

    const openShiftDialog = (daySlots: EventScheduleSlotDto[]) => {
        setShiftDaySlots(daySlots)
        setShiftDialogOpen(true)
    }

    const closeShiftDialog = () => setShiftDialogOpen(false)

    const openImportDialog = () => setImportDialogOpen(true)
    const closeImportDialog = () => setImportDialogOpen(false)

    const handleDelete = (slot: EventScheduleSlotDto) => {
        confirmAction(async () => {
            const {error} = await deleteScheduleSlot({path: {eventId, slotId: slot.id}})
            if (error) {
                showSlotActionError(
                    error,
                    t('entity.delete.error', {entity: t('event.schedule.slot')}),
                )
            } else {
                feedback.success(t('entity.delete.success', {entity: t('event.schedule.slot')}))
            }
            reload()
        })
    }

    const handleSkip = (slot: EventScheduleSlotDto) => {
        const siblingCount = slot.setupRoundId
            ? slotsInRound(data?.slots ?? [], slot.setupRoundId).length - 1
            : 0
        confirmAction(
            async () => {
                const {error} = await skipScheduleSlot({path: {eventId, slotId: slot.id}})
                if (error) {
                    showSlotActionError(error)
                } else if (advanceOffer(data?.slots ?? [], slot) !== null) {
                    // Erst nach der bestätigten Absage, und nur, wenn es überhaupt etwas
                    // vorzuziehen gibt: ein Dialog, der sich bloß öffnet, um "geht nicht" zu
                    // sagen, ist am Renntag ein Klick zu viel.
                    setAdvanceSlot(slot)
                }
                reload()
            },
            {
                // Der Umfang steht im Text UND auf dem Button: bei einer Runde aus mehreren Läufen
                // muss unmissverständlich sein, dass hier nur dieser eine Lauf entfällt.
                content: slot.setupRoundId
                    ? t(
                          siblingCount > 0
                              ? 'event.schedule.skipConfirm'
                              : 'event.schedule.skipConfirmOnly',
                          {
                              label: slotLabel(slot),
                              time: format(new Date(slot.startTime), t('format.time')),
                              round: slot.roundName ?? '',
                              count: siblingCount,
                          },
                      )
                    : t('event.schedule.skipConfirmFree', {
                          label: slotLabel(slot),
                          time: format(new Date(slot.startTime), t('format.time')),
                      }),
                okText: t(
                    slot.setupRoundId
                        ? 'event.schedule.skipOk'
                        : 'event.schedule.skipOkFree',
                ),
            },
        )
    }

    const handleUnskip = (slot: EventScheduleSlotDto) => {
        confirmAction(
            async () => {
                const {error} = await unskipScheduleSlot({path: {eventId, slotId: slot.id}})
                if (error) {
                    showSlotActionError(error)
                } else {
                    // Das Gegenstück zum Vorziehen nach der Absage: Wer den Tag damals in die
                    // frei gewordene Zeit nachrücken ließ, braucht jetzt Platz für den
                    // zurückgekehrten Slot. Angeboten wird das vorhandene Verschieben-Werkzeug
                    // des Tages - vorgeöffnet, nicht erzwungen: Wer nie vorgezogen hat, schließt
                    // es einfach wieder.
                    const daySlots = (data?.slots ?? []).filter(
                        s => s.startTime.slice(0, 10) === slot.startTime.slice(0, 10),
                    )
                    if (daySlots.length > 0) {
                        feedback.success(t('event.schedule.unskipShiftHint'))
                        openShiftDialog(daySlots)
                    }
                }
                reload()
            },
            {
                content: t('event.schedule.unskipConfirm', {
                    label: slotLabel(slot),
                    time: format(new Date(slot.startTime), t('format.time')),
                }),
                okText: t('event.schedule.unskip'),
            },
        )
    }

    // Regattabüro greift direkt vom Zeitplan ein (C1) - unabhängig vom chainProgressionMode der
    // Veranstaltung, die Aktion selbst prüft serverseitig nur, dass der Slot LINKED ist. Im
    // Nicht-REGATTABUERO-Modus (SCHIEDSRICHTER/DEAKTIVIERT) ist Aktivieren/Beenden normalerweise
    // Aufgabe des Schiedsrichter-Dashboards - die Bestätigung warnt hier zusätzlich und der
    // OK-Button heißt "Trotzdem ..." (C2).
    const handleActivate = (slot: EventScheduleSlotDto) => {
        const mode = data?.chainProgressionMode ?? 'DEAKTIVIERT'
        const isOffice = mode === 'REGATTABUERO'

        confirmAction(
            async () => {
                const {error} = await activateScheduleSlot({path: {eventId, slotId: slot.id}})
                if (error) {
                    showSlotActionError(error)
                }
                reload()
            },
            {
                content: isOffice ? (
                    t('event.schedule.activateConfirm', {
                        label: slotLabel(slot),
                        time: format(new Date(slot.startTime), t('format.time')),
                    })
                ) : (
                    <>
                        {t('event.schedule.activateConfirm', {
                            label: slotLabel(slot),
                            time: format(new Date(slot.startTime), t('format.time')),
                        })}
                        <br />
                        <br />
                        {t('event.schedule.refereeModeWarning')}
                    </>
                ),
                okText: isOffice
                    ? t('event.schedule.activate')
                    : t('event.schedule.activateAnyway'),
            },
        )
    }

    const handleFinishSlot = (slot: EventScheduleSlotDto) => {
        const mode = data?.chainProgressionMode ?? 'DEAKTIVIERT'
        const isOffice = mode === 'REGATTABUERO'

        confirmAction(
            async () => {
                const {error} = await finishScheduleSlot({path: {eventId, slotId: slot.id}})
                if (error) {
                    showSlotActionError(error)
                }
                reload()
            },
            {
                content: isOffice ? (
                    t('event.schedule.finishConfirm', {
                        label: slotLabel(slot),
                        time: format(new Date(slot.startTime), t('format.time')),
                    })
                ) : (
                    <>
                        {t('event.schedule.finishConfirm', {
                            label: slotLabel(slot),
                            time: format(new Date(slot.startTime), t('format.time')),
                        })}
                        <br />
                        <br />
                        {t('event.schedule.refereeModeWarning')}
                    </>
                ),
                okText: isOffice ? t('event.schedule.finish') : t('event.schedule.finishAnyway'),
            },
        )
    }

    /** Ist-Start aus dem Büro — dasselbe „Läuft" wie im Schiedsrichter-Dashboard, ohne Dialog. */
    const handleMarkStarted = async (slot: EventScheduleSlotDto) => {
        if (!slot.matchId || !slot.competitionId) return
        const {error} = await markMatchStartedFromExecution({
            path: {
                eventId,
                competitionId: slot.competitionId,
                competitionMatchId: slot.matchId,
            },
        })
        if (error) {
            showSlotActionError(error)
        }
        reload()
    }

    /** Nimmt die Aktivierung zurück — löscht auch den Ist-Start und pausiert den Abruf (Backend). */
    const handleDeactivate = (slot: EventScheduleSlotDto) => {
        if (!slot.matchId || !slot.competitionId) return
        confirmAction(
            async () => {
                const {error} = await updateMatchActivation({
                    path: {
                        eventId,
                        competitionId: slot.competitionId!,
                        competitionMatchId: slot.matchId!,
                    },
                    body: {activated: false},
                })
                if (error) {
                    showSlotActionError(error)
                }
                reload()
            },
            {
                content: t('event.schedule.deactivateConfirm', {
                    label: slotLabel(slot),
                    time: format(new Date(slot.startTime), t('format.time')),
                }),
                okText: t('event.schedule.deactivate'),
            },
        )
    }

    /** Beenden zurücknehmen — der Server erlaubt es nur in der jüngsten Runde des Wettkampfs. */
    const handleReopen = (slot: EventScheduleSlotDto) => {
        if (!slot.matchId || !slot.competitionId) return
        confirmAction(
            async () => {
                const {error} = await reopenMatch({
                    path: {
                        eventId,
                        competitionId: slot.competitionId!,
                        competitionMatchId: slot.matchId!,
                    },
                })
                if (error) {
                    showSlotActionError(error)
                }
                reload()
            },
            {
                content: t('event.schedule.reopenConfirm', {
                    label: slotLabel(slot),
                    time: format(new Date(slot.startTime), t('format.time')),
                }),
                okText: t('event.schedule.reopen'),
            },
        )
    }

    const daySections = groupSlotsByDay(data?.slots ?? [])
    const unplannedSetupMatches = data?.unplannedSetupMatches ?? []

    // 'all' oder ein Datum (YYYY-MM-DD). Die Vorauswahl spart den täglichen Klick: Wer den
    // Zeitplan am Renntag öffnet, will fast immer den heutigen Tag sehen.
    const today = format(new Date(), 'yyyy-MM-dd')
    const effectiveDay =
        selectedDay ?? (daySections.some(section => section.date === today) ? today : 'all')
    const visibleSections =
        effectiveDay === 'all'
            ? daySections
            : daySections.filter(section => section.date === effectiveDay)

    // Aktuelle Verspätung aus den Ist-Starts der Slots — dieselbe Regel wie das
    // Verspätungs-Element der Boards (scheduleDelay.ts): der zuletzt gestartete Lauf zählt.
    // Die Daten liegen im Zeitplan-Tab bereits vor (matchStartedAt), kein eigener Endpoint.
    const delaySeconds = latestStartDelaySeconds(
        (data?.slots ?? []).map(slot => ({
            startTime: slot.startTime,
            startedAt: slot.matchStartedAt,
        })),
    )
    const delay = delaySeconds != null ? delayParts(delaySeconds) : null

    return (
        <Stack spacing={4}>
            <Stack direction={'row'} justifyContent={'space-between'} alignItems={'center'}>
                <Stack direction={'row'} spacing={2} alignItems={'center'}>
                    <Typography variant={'h2'}>{t('event.schedule.tab')}</Typography>
                    {delay && (
                        <Chip
                            size={'small'}
                            color={delay.kind === 'late' ? 'warning' : 'default'}
                            label={
                                delay.kind === 'onTime'
                                    ? t('event.boards.delay.onTime')
                                    : `${delay.kind === 'late' ? '+' : '−'}${delay.minutes} min`
                            }
                            title={t('event.boards.delay.subtitle')}
                        />
                    )}
                </Stack>
                <Stack direction={'row'} spacing={2} alignItems={'center'}>
                    {canEdit && (
                        <>
                            <ScheduleStartlistExportButton eventId={eventId} />
                            <Button variant={'outlined'} onClick={openImportDialog}>
                                {t('event.schedule.import')}
                            </Button>
                            <Button
                                variant={'outlined'}
                                startIcon={<Add />}
                                onClick={openAddDialog}>
                                {t('event.schedule.addSlot')}
                            </Button>
                        </>
                    )}
                    <ScheduleSettingsPopover
                        event={event}
                        reloadEvent={reloadEvent}
                        canEdit={canEdit}
                        shortLabels={shortLabels}
                        toggleShortLabels={toggleShortLabels}
                        openExecutionInNewTab={openExecutionInNewTab}
                        setOpenExecutionInNewTab={setOpenExecutionInNewTab}
                    />
                </Stack>
            </Stack>
            {!data && pending && <Throbber />}
            {data && daySections.length === 0 && (
                <Typography color={'text.secondary'}>{t('event.schedule.noSlots')}</Typography>
            )}
            {/* Ein Tab je Eventtag plus "Alle Eventtage" - erspart das Scrollen durch fremde Tage. */}
            {daySections.length > 1 && (
                <Tabs
                    value={effectiveDay}
                    onChange={(_, value: string) => setSelectedDay(value)}
                    variant={'scrollable'}
                    allowScrollButtonsMobile>
                    <Tab value={'all'} label={t('event.schedule.allDays')} />
                    {daySections.map(section => (
                        <Tab
                            key={section.date}
                            value={section.date}
                            label={format(new Date(section.date), t('format.date'))}
                        />
                    ))}
                </Tabs>
            )}
            {visibleSections.map(section => (
                <Box key={section.date}>
                    <Stack
                        direction={'row'}
                        justifyContent={'space-between'}
                        alignItems={'center'}
                        sx={{mb: 1}}>
                        <Typography variant={'h3'}>
                            {format(new Date(section.date), t('format.date'))}
                        </Typography>
                        {canEdit && (
                            <Button
                                size={'small'}
                                variant={'text'}
                                onClick={() => openShiftDialog(section.slots)}>
                                {t('event.schedule.adjust')}
                            </Button>
                        )}
                    </Stack>
                    <Box sx={{mb: 2}}>
                        <ScheduleTimelineIndicator
                            entries={scheduleSlotsToEntries(section.slots)}
                            now={now}
                            onEntryClick={scrollToSlot}
                        />
                    </Box>
                    <TableContainer>
                        <Table size={'small'}>
                            <TableHead>
                                <TableRow>
                                    <TableCell width={'10%'}>{t('event.schedule.startTime')}</TableCell>
                                    <TableCell width={'40%'}>{t('event.schedule.slot')}</TableCell>
                                    <TableCell width={'20%'}>{t('event.schedule.status')}</TableCell>
                                    <TableCell width={'15%'}>{t('event.schedule.duration')}</TableCell>
                                    {canEdit && <TableCell width={'15%'} />}
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {section.slots.map(slot => {
                                    const chip = stateChipProps(slot, now, t)
                                    // Der Schlüssel steht erst zur Laufzeit fest, deshalb die
                                    // gelockerte Signatur — dasselbe Muster wie in stateChipProps.
                                    const translate = t as (
                                        key: string,
                                        values?: Record<string, string>,
                                    ) => string
                                    const bye = byeExplanation(slot.bye)
                                    return (
                                        <TableRow
                                            key={slot.id}
                                            ref={el => {
                                                if (el) {
                                                    rowRefs.current.set(slot.id, el)
                                                } else {
                                                    rowRefs.current.delete(slot.id)
                                                }
                                            }}
                                            sx={{
                                                backgroundColor:
                                                    highlightedSlotId === slot.id
                                                        ? 'action.selected'
                                                        : undefined,
                                                transition: 'background-color 0.3s ease',
                                            }}>
                                            <TableCell>
                                                {format(new Date(slot.startTime), t('format.time'))}
                                            </TableCell>
                                            <TableCell>
                                                {/* Der Sprung zur Durchführung sitzt immer am rechten
                                                    Rand der Spalte - und zwar auf einem festen Platz,
                                                    der auch dann bleibt, wenn eine Zeile keinen Lauf
                                                    hat. Sonst wandert das Symbol mit der Textlänge
                                                    jeder Zeile mit. */}
                                                <Stack
                                                    direction={'row'}
                                                    spacing={1}
                                                    alignItems={'center'}
                                                    justifyContent={'space-between'}>
                                                    <Box component={'span'}>
                                                        {competitionTag(slot) && (
                                                            <Box
                                                                component={'span'}
                                                                sx={{
                                                                    color: 'text.secondary',
                                                                    mr: 1,
                                                                }}>
                                                                {competitionTag(slot)}
                                                            </Box>
                                                        )}
                                                        {slotLabel(
                                                            slot,
                                                            shortLabels ? 'short' : 'full',
                                                        )}
                                                    </Box>
                                                    <Box sx={{...actionSlotSx, flexShrink: 0}}>
                                                        {slot.matchId && (
                                                            <Tooltip
                                                                title={t(
                                                                    'event.schedule.goToExecution',
                                                                )}>
                                                                <Link
                                                                    to={
                                                                        '/event/$eventId/competition/$competitionId'
                                                                    }
                                                                    params={{
                                                                        eventId,
                                                                        competitionId:
                                                                            slot.competitionId!,
                                                                    }}
                                                                    search={{tab: 'execution'}}
                                                                    // Auf Wunsch je Gerät in
                                                                    // einem neuen Fenster (siehe
                                                                    // Einstellungs-Popover).
                                                                    target={
                                                                        openExecutionInNewTab
                                                                            ? '_blank'
                                                                            : undefined
                                                                    }
                                                                    style={{
                                                                        display: 'inline-flex',
                                                                        color: 'inherit',
                                                                    }}>
                                                                    <IconButton
                                                                        size={'small'}
                                                                        component={'span'}>
                                                                        <OpenInNew
                                                                            fontSize={'small'}
                                                                        />
                                                                    </IconButton>
                                                                </Link>
                                                            </Tooltip>
                                                        )}
                                                    </Box>
                                                </Stack>
                                                {bye && (
                                                    <Typography
                                                        variant={'caption'}
                                                        display={'block'}
                                                        sx={{color: 'text.secondary'}}>
                                                        {translate(bye.key, bye.values) +
                                                            (bye.mustRace
                                                                ? ` – ${translate('event.match.bye.mustRace')}`
                                                                : '')}
                                                    </Typography>
                                                )}
                                            </TableCell>
                                            <TableCell>
                                                <Chip
                                                    size={'small'}
                                                    label={chip.label}
                                                    color={chip.color}
                                                    sx={chip.sx}
                                                />
                                            </TableCell>
                                            <TableCell>
                                                {slot.durationMinutes != null
                                                    ? t('event.schedule.durationValue', {
                                                          minutes: slot.durationMinutes,
                                                      })
                                                    : '-'}
                                            </TableCell>
                                            {canEdit && (
                                                <TableCell>
                                                    {/* Feste Spalten pro Aktion: fehlt eine, bleibt ihr
                                                        Platz leer — so stehen gleiche Symbole über alle
                                                        Zeilen sauber untereinander. */}
                                                    <Stack direction={'row'} spacing={0.5}>
                                                        <Box sx={actionSlotSx}>
                                                            {isEditable(slot) && (
                                                                <Tooltip title={t('common.edit')}>
                                                                    <IconButton
                                                                        size={'small'}
                                                                        onClick={() =>
                                                                            openEditDialog(slot)
                                                                        }>
                                                                        <Edit fontSize={'small'} />
                                                                    </IconButton>
                                                                </Tooltip>
                                                            )}
                                                        </Box>
                                                        {/*
                                                            Die Lebenszyklus-Aktionen standen bis zum 11.08.2026 zusammen
                                                            in EINEM 30-px-Slot; bei einem aktivierten, noch nicht
                                                            gestarteten Lauf sind es aber drei Knöpfe gleichzeitig
                                                            (Läuft festhalten, Deaktivieren, Beenden) — sie überlappten
                                                            sich und die Nachbarzeilen. Jetzt hat jede Aktion ihren
                                                            eigenen festen Slot, wie die übrigen Spalten auch.
                                                        */}
                                                        <Box sx={actionSlotSx}>
                                                            {/*
                                                                Ein Freilos wird nicht gefahren: Aktivieren ergibt dort
                                                                keinen Sinn, das Quittieren (= Beenden, setzt finished_at
                                                                wie der Beenden-Klick im Dashboard) ist die einzige offene
                                                                Handlung - und sie darf nicht an der Aktivierung hängen,
                                                                die ein Freilos nie bekommt.
                                                            */}
                                                            {slot.state === 'LINKED' &&
                                                                !slot.bye &&
                                                                !slot.matchFinishedAt &&
                                                                slot.matchActivatedAt == null && (
                                                                    <Tooltip
                                                                        title={t(
                                                                            'event.schedule.activate',
                                                                        )}>
                                                                        <IconButton
                                                                            size={'small'}
                                                                            onClick={() =>
                                                                                handleActivate(slot)
                                                                            }>
                                                                            <PlayArrow
                                                                                fontSize={'small'}
                                                                            />
                                                                        </IconButton>
                                                                    </Tooltip>
                                                                )}
                                                            {slot.state === 'LINKED' &&
                                                                !slot.bye &&
                                                                !slot.matchFinishedAt &&
                                                                slot.matchActivatedAt != null &&
                                                                !slot.matchStartedAt && (
                                                                    <Tooltip
                                                                        title={t(
                                                                            'event.schedule.markStarted',
                                                                        )}>
                                                                        <IconButton
                                                                            size={'small'}
                                                                            onClick={() =>
                                                                                handleMarkStarted(slot)
                                                                            }>
                                                                            <DirectionsRun
                                                                                fontSize={'small'}
                                                                            />
                                                                        </IconButton>
                                                                    </Tooltip>
                                                                )}
                                                        </Box>
                                                        <Box sx={actionSlotSx}>
                                                            {slot.state === 'LINKED' &&
                                                                !slot.matchFinishedAt &&
                                                                slot.matchActivatedAt != null && (
                                                                    <Tooltip
                                                                        title={t(
                                                                            'event.schedule.deactivate',
                                                                        )}>
                                                                        <IconButton
                                                                            size={'small'}
                                                                            onClick={() =>
                                                                                handleDeactivate(slot)
                                                                            }>
                                                                            <Undo fontSize={'small'} />
                                                                        </IconButton>
                                                                    </Tooltip>
                                                                )}
                                                        </Box>
                                                        <Box sx={actionSlotSx}>
                                                            {slot.state === 'LINKED' &&
                                                                (slot.bye
                                                                    ? !slot.matchFinishedAt
                                                                    : slot.matchActivatedAt != null) && (
                                                                    <Tooltip
                                                                        title={t(
                                                                            slot.bye
                                                                                ? 'event.schedule.acknowledgeBye'
                                                                                : 'event.schedule.finish',
                                                                        )}>
                                                                        <IconButton
                                                                            size={'small'}
                                                                            onClick={() =>
                                                                                handleFinishSlot(slot)
                                                                            }>
                                                                            <Stop fontSize={'small'} />
                                                                        </IconButton>
                                                                    </Tooltip>
                                                                )}
                                                            {slot.state === 'LINKED' &&
                                                                !!slot.matchFinishedAt && (
                                                                    <Tooltip
                                                                        title={t(
                                                                            'event.schedule.reopen',
                                                                        )}>
                                                                        <IconButton
                                                                            size={'small'}
                                                                            onClick={() =>
                                                                                handleReopen(slot)
                                                                            }>
                                                                            <Replay fontSize={'small'} />
                                                                        </IconButton>
                                                                    </Tooltip>
                                                                )}
                                                        </Box>
                                                        <Box sx={actionSlotSx}>
                                                            {slot.state === 'SKIPPED' ? (
                                                                <Tooltip
                                                                    title={t('event.schedule.unskip')}>
                                                                    <IconButton
                                                                        size={'small'}
                                                                        onClick={() =>
                                                                            handleUnskip(slot)
                                                                        }>
                                                                        <EventRepeat
                                                                            fontSize={'small'}
                                                                        />
                                                                    </IconButton>
                                                                </Tooltip>
                                                            ) : (
                                                                isCancellable(slot) && (
                                                                    <Tooltip
                                                                        title={t(
                                                                            slot.setupRoundId
                                                                                ? 'event.schedule.skip'
                                                                                : 'event.schedule.skipFree',
                                                                        )}>
                                                                        <IconButton
                                                                            size={'small'}
                                                                            onClick={() =>
                                                                                handleSkip(slot)
                                                                            }>
                                                                            <EventBusy
                                                                                fontSize={'small'}
                                                                            />
                                                                        </IconButton>
                                                                    </Tooltip>
                                                                )
                                                            )}
                                                        </Box>
                                                        <Box sx={actionSlotSx}>
                                                            <Tooltip title={t('common.delete')}>
                                                                <IconButton
                                                                    size={'small'}
                                                                    onClick={() => handleDelete(slot)}>
                                                                    <Delete fontSize={'small'} />
                                                                </IconButton>
                                                            </Tooltip>
                                                        </Box>
                                                    </Stack>
                                                </TableCell>
                                            )}
                                        </TableRow>
                                    )
                                })}
                            </TableBody>
                        </Table>
                    </TableContainer>
                </Box>
            ))}
            <Box>
                <Typography variant={'h3'} sx={{mb: 1}}>
                    {t('event.schedule.unplanned')}
                </Typography>
                {unplannedSetupMatches.length === 0 ? (
                    <Typography color={'text.secondary'}>
                        {t('event.schedule.noUnplanned')}
                    </Typography>
                ) : (
                    <TableContainer>
                        <Table size={'small'}>
                            <TableHead>
                                <TableRow>
                                    <TableCell width={'25%'}>{t('event.schedule.competition')}</TableCell>
                                    <TableCell width={'20%'}>{t('event.schedule.round')}</TableCell>
                                    <TableCell width={'20%'}>{t('event.schedule.match')}</TableCell>
                                    <TableCell width={'20%'}>{t('event.schedule.status')}</TableCell>
                                    {canEdit && <TableCell width={'15%'} />}
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {unplannedSetupMatches.map(match => {
                                    // Vor allem für die Dauer-Freilose: "Freilos · offen" heißt,
                                    // hier wartet noch eine Quittierung.
                                    const status = unplannedMatchStatus(match)
                                    const chip = status && matchStatusChip(status, null, now)
                                    return (
                                        <TableRow key={match.setupMatchId}>
                                            <TableCell>
                                                {competitionTag(match) && (
                                                    <Box
                                                        component={'span'}
                                                        sx={{color: 'text.secondary', mr: 1}}>
                                                        {competitionTag(match)}
                                                    </Box>
                                                )}
                                                {/* Dieselbe Kürzung wie in der Slot-Spalte: mit
                                                    Kürzel davor sagt der ausgeschriebene Name nichts
                                                    Neues mehr. */}
                                                {(!shortLabels || !competitionTag(match)) &&
                                                    match.competitionName}
                                            </TableCell>
                                            <TableCell>{match.roundName}</TableCell>
                                            <TableCell>{match.matchName ?? '-'}</TableCell>
                                            <TableCell>
                                                {chip && (
                                                    <Chip
                                                        size={'small'}
                                                        // Der Schlüssel steht erst zur Laufzeit
                                                        // fest - dasselbe Muster wie StatusChip.
                                                        label={(
                                                            t as (
                                                                key: string,
                                                                values?: Record<
                                                                    string,
                                                                    string | number
                                                                >,
                                                            ) => string
                                                        )(chip.labelKey, chip.values)}
                                                        color={chip.color}
                                                        sx={
                                                            chip.strikeThrough
                                                                ? {textDecoration: 'line-through'}
                                                                : undefined
                                                        }
                                                    />
                                                )}
                                            </TableCell>
                                            {canEdit && (
                                                <TableCell>
                                                    <Button
                                                        size={'small'}
                                                        variant={'text'}
                                                        onClick={() => openPlanDialog(match)}>
                                                        {t('event.schedule.plan')}
                                                    </Button>
                                                </TableCell>
                                            )}
                                        </TableRow>
                                    )
                                })}
                            </TableBody>
                        </Table>
                    </TableContainer>
                )}
            </Box>
            {canEdit && (
                <ScheduleSlotDialog
                    eventId={eventId}
                    open={dialogOpen}
                    onClose={closeDialog}
                    reloadData={reload}
                    unplannedSetupMatches={unplannedSetupMatches}
                    editingSlot={editingSlot}
                    presetMatch={presetMatch}
                />
            )}
            {canEdit && (
                <ScheduleShiftDialog
                    eventId={eventId}
                    open={shiftDialogOpen}
                    onClose={closeShiftDialog}
                    reloadData={reload}
                    slots={shiftDaySlots}
                />
            )}
            {canEdit && (
                <ScheduleAdvanceDialog
                    eventId={eventId}
                    open={advanceSlot !== undefined}
                    onClose={() => setAdvanceSlot(undefined)}
                    reloadData={reload}
                    skippedSlot={advanceSlot}
                    slots={data?.slots ?? []}
                />
            )}
            {canEdit && (
                <ScheduleImportDialog
                    eventId={eventId}
                    open={importDialogOpen}
                    onClose={closeImportDialog}
                    reloadData={reload}
                    slots={data?.slots ?? []}
                />
            )}
        </Stack>
    )
}

export default EventSchedule

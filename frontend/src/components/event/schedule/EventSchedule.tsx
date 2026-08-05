import {useEffect, useRef, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {
    Box,
    Button,
    Chip,
    ChipProps,
    IconButton,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Tooltip,
    Typography,
} from '@mui/material'
import {
    Add,
    Delete,
    Edit,
    EventBusy,
    EventRepeat,
    OpenInNew,
    PlayArrow,
    PlaylistRemove,
    Stop,
} from '@mui/icons-material'
import {format} from 'date-fns'
import {Link} from '@tanstack/react-router'
import {eventRoute} from '@routes'
import {
    activateScheduleSlot,
    deleteScheduleSlot,
    finishScheduleSlot,
    getEventSchedule,
    skipScheduleRound,
    skipScheduleSlot,
    unskipScheduleSlot,
} from '@api/sdk.gen.ts'
import {EventScheduleSlotDto, UnplannedSetupMatchDto} from '@api/types.gen.ts'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateEventGlobal} from '@authorization/privileges.ts'
import Throbber from '@components/Throbber.tsx'
import {groupSlotsByDay, isEditable, slotLabel, slotsInRound} from './common.ts'
import {scheduleSlotsToEntries} from './timelineIndicator.ts'
import ScheduleSlotDialog from './ScheduleSlotDialog.tsx'
import ScheduleShiftDialog from './ScheduleShiftDialog.tsx'
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

const stateChipProps = (
    slot: EventScheduleSlotDto,
    t: (key: string) => string,
): {label: string; color: ChipProps['color']; sx?: ChipProps['sx']} => {
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

const EventSchedule = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()
    const {confirmAction} = useConfirmation()
    const {eventId} = eventRoute.useParams()

    const canEdit = user.checkPrivilege(updateEventGlobal)

    const [lastRequested, setLastRequested] = useState(Date.now())
    const reload = () => setLastRequested(Date.now())

    const [dialogOpen, setDialogOpen] = useState(false)
    const [editingSlot, setEditingSlot] = useState<EventScheduleSlotDto | undefined>(undefined)
    const [presetMatch, setPresetMatch] = useState<UnplannedSetupMatchDto | undefined>(undefined)

    const [shiftDialogOpen, setShiftDialogOpen] = useState(false)
    const [shiftDaySlots, setShiftDaySlots] = useState<EventScheduleSlotDto[]>([])

    const [importDialogOpen, setImportDialogOpen] = useState(false)

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
                feedback.error(t('entity.delete.error', {entity: t('event.schedule.slot')}))
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
                    feedback.error(t('common.error.unexpected'))
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

    const handleSkipRound = (slot: EventScheduleSlotDto) => {
        const setupRoundId = slot.setupRoundId
        if (!setupRoundId) {
            return
        }
        const affected = slotsInRound(data?.slots ?? [], setupRoundId)
        // Die betroffenen Läufe werden namentlich mit Uhrzeit aufgezählt - eine Zahl allein sagt
        // nicht, was gleich verschwindet.
        const list = affected
            .map(
                s =>
                    `${s.matchName ?? '?'} (${format(new Date(s.startTime), t('format.time'))})`,
            )
            .join(', ')

        confirmAction(
            async () => {
                const {error} = await skipScheduleRound({path: {eventId, setupRoundId}})
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                }
                reload()
            },
            {
                content: t('event.schedule.skipRoundConfirm', {
                    round: slot.roundName ?? '',
                    competition: slot.competitionName ?? '',
                    count: affected.length,
                    list,
                }),
                okText: t('event.schedule.skipRoundOk', {count: affected.length}),
            },
        )
    }

    const handleUnskip = (slot: EventScheduleSlotDto) => {
        confirmAction(
            async () => {
                const {error} = await unskipScheduleSlot({path: {eventId, slotId: slot.id}})
                if (error) {
                    feedback.error(t('common.error.unexpected'))
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
                    feedback.error(t('common.error.unexpected'))
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
                    feedback.error(t('common.error.unexpected'))
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

    const daySections = groupSlotsByDay(data?.slots ?? [])
    const unplannedSetupMatches = data?.unplannedSetupMatches ?? []

    return (
        <Stack spacing={4}>
            <Stack direction={'row'} justifyContent={'space-between'} alignItems={'center'}>
                <Typography variant={'h2'}>{t('event.schedule.tab')}</Typography>
                {canEdit && (
                    <Stack direction={'row'} spacing={2}>
                        <Button variant={'outlined'} onClick={openImportDialog}>
                            {t('event.schedule.import')}
                        </Button>
                        <Button variant={'outlined'} startIcon={<Add />} onClick={openAddDialog}>
                            {t('event.schedule.addSlot')}
                        </Button>
                    </Stack>
                )}
            </Stack>
            {!data && pending && <Throbber />}
            {data && daySections.length === 0 && (
                <Typography color={'text.secondary'}>{t('event.schedule.noSlots')}</Typography>
            )}
            {daySections.map(section => (
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
                                    const chip = stateChipProps(slot, t)
                                    const roundSlotCount = slot.setupRoundId
                                        ? slotsInRound(data?.slots ?? [], slot.setupRoundId).length
                                        : 0
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
                                                <Stack
                                                    direction={'row'}
                                                    spacing={0.5}
                                                    alignItems={'center'}>
                                                    <span>{slotLabel(slot)}</span>
                                                    {slot.matchId && (
                                                        <Tooltip
                                                            title={t('event.schedule.goToExecution')}>
                                                            <Link
                                                                to={
                                                                    '/event/$eventId/competition/$competitionId'
                                                                }
                                                                params={{
                                                                    eventId,
                                                                    competitionId: slot.competitionId!,
                                                                }}
                                                                search={{tab: 'execution'}}
                                                                style={{
                                                                    display: 'inline-flex',
                                                                    color: 'inherit',
                                                                }}>
                                                                <IconButton
                                                                    size={'small'}
                                                                    component={'span'}>
                                                                    <OpenInNew fontSize={'small'} />
                                                                </IconButton>
                                                            </Link>
                                                        </Tooltip>
                                                    )}
                                                </Stack>
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
                                                        <Box sx={actionSlotSx}>
                                                            {slot.state === 'LINKED' &&
                                                                !slot.matchFinishedAt &&
                                                                !slot.matchCurrentlyRunning && (
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
                                                                slot.matchCurrentlyRunning && (
                                                                    <Tooltip
                                                                        title={t(
                                                                            'event.schedule.finish',
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
                                                                <Tooltip
                                                                    title={t(
                                                                        slot.setupRoundId
                                                                            ? 'event.schedule.skip'
                                                                            : 'event.schedule.skipFree',
                                                                    )}>
                                                                    <IconButton
                                                                        size={'small'}
                                                                        onClick={() => handleSkip(slot)}>
                                                                        <EventBusy fontSize={'small'} />
                                                                    </IconButton>
                                                                </Tooltip>
                                                            )}
                                                        </Box>
                                                        <Box sx={actionSlotSx}>
                                                            {/* Nur anzeigen, wenn die Runde mehrere
                                                                Slots hat - bei einer Runde aus einem
                                                                einzigen Lauf (z.B. ein Finale) wäre
                                                                die Aktion identisch mit der links
                                                                daneben und nur verwirrend. */}
                                                            {roundSlotCount > 1 && (
                                                                <Tooltip
                                                                    title={t(
                                                                        'event.schedule.skipRound',
                                                                        {count: roundSlotCount},
                                                                    )}>
                                                                    <IconButton
                                                                        size={'small'}
                                                                        onClick={() =>
                                                                            handleSkipRound(slot)
                                                                        }>
                                                                        <PlaylistRemove
                                                                            fontSize={'small'}
                                                                        />
                                                                    </IconButton>
                                                                </Tooltip>
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
                                    <TableCell width={'30%'}>{t('event.schedule.competition')}</TableCell>
                                    <TableCell width={'25%'}>{t('event.schedule.round')}</TableCell>
                                    <TableCell width={'25%'}>{t('event.schedule.match')}</TableCell>
                                    {canEdit && <TableCell width={'20%'} />}
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {unplannedSetupMatches.map(match => (
                                    <TableRow key={match.setupMatchId}>
                                        <TableCell>{match.competitionName}</TableCell>
                                        <TableCell>{match.roundName}</TableCell>
                                        <TableCell>{match.matchName ?? '-'}</TableCell>
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
                                ))}
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

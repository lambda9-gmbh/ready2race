import {useState} from 'react'
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
import {Add, Delete, Edit, EventBusy, EventRepeat} from '@mui/icons-material'
import {format} from 'date-fns'
import {eventRoute} from '@routes'
import {deleteScheduleSlot, getEventSchedule, skipScheduleSlot, unskipScheduleSlot} from '@api/sdk.gen.ts'
import {EventScheduleSlotDto, UnplannedSetupMatchDto} from '@api/types.gen.ts'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateEventGlobal} from '@authorization/privileges.ts'
import Throbber from '@components/Throbber.tsx'
import {groupSlotsByDay, isEditable, slotLabel} from './common.ts'
import ScheduleSlotDialog from './ScheduleSlotDialog.tsx'
import ScheduleShiftDialog from './ScheduleShiftDialog.tsx'
import ScheduleImportDialog from './ScheduleImportDialog.tsx'

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
        confirmAction(
            async () => {
                const {error} = await skipScheduleSlot({path: {eventId, slotId: slot.id}})
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                }
                reload()
            },
            {
                content: t('event.schedule.skipConfirm', {
                    label: slotLabel(slot),
                    time: format(new Date(slot.startTime), t('format.time')),
                }),
                okText: t('event.schedule.skip'),
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
            {okText: t('event.schedule.unskip')},
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
                                    return (
                                        <TableRow key={slot.id}>
                                            <TableCell>
                                                {format(new Date(slot.startTime), t('format.time'))}
                                            </TableCell>
                                            <TableCell>{slotLabel(slot)}</TableCell>
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
                                                    <Stack direction={'row'} spacing={0.5}>
                                                        {isEditable(slot) && (
                                                            <Tooltip title={t('common.edit')}>
                                                                <IconButton
                                                                    size={'small'}
                                                                    onClick={() => openEditDialog(slot)}>
                                                                    <Edit fontSize={'small'} />
                                                                </IconButton>
                                                            </Tooltip>
                                                        )}
                                                        {slot.state === 'SKIPPED' ? (
                                                            <Tooltip title={t('event.schedule.unskip')}>
                                                                <IconButton
                                                                    size={'small'}
                                                                    onClick={() => handleUnskip(slot)}>
                                                                    <EventRepeat fontSize={'small'} />
                                                                </IconButton>
                                                            </Tooltip>
                                                        ) : (
                                                            <Tooltip title={t('event.schedule.skip')}>
                                                                <IconButton
                                                                    size={'small'}
                                                                    onClick={() => handleSkip(slot)}>
                                                                    <EventBusy fontSize={'small'} />
                                                                </IconButton>
                                                            </Tooltip>
                                                        )}
                                                        <Tooltip title={t('common.delete')}>
                                                            <IconButton
                                                                size={'small'}
                                                                onClick={() => handleDelete(slot)}>
                                                                <Delete fontSize={'small'} />
                                                            </IconButton>
                                                        </Tooltip>
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

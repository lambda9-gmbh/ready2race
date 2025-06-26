import {format, formatISO, getDay, parse, startOfWeek} from 'date-fns'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {
    Calendar,
    dateFnsLocalizer,
    DayPropGetter,
    EventPropGetter,
    SlotInfo,
    View,
} from 'react-big-calendar'
import 'react-big-calendar/lib/css/react-big-calendar.css'
import 'react-big-calendar/lib/addons/dragAndDrop/styles.scss'
import {
    Alert,
    Box,
    Button,
    FormControlLabel,
    FormGroup,
    Stack,
    Switch,
    Typography,
} from '@mui/material'
import {de} from 'date-fns/locale/de'
import {enGB} from 'date-fns/locale/en-GB'
import withDragAndDrop, {EventInteractionArgs} from 'react-big-calendar/lib/addons/dragAndDrop'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import ShiftDialog from '@components/event/shiftplan/ShiftDialog.tsx'
import {getWorkShifts, getWorkTypes} from '@api/sdk.gen.ts'
import {useTranslation} from 'react-i18next'
import {eventIndexRoute} from '@routes'
import {WorkShiftWithAssignedUsersDto, WorkTypeDto} from '@api/types.gen.ts'
import InlineLink from '@components/InlineLink.tsx'
import {Add, CheckCircleOutlined, FilterAlt} from '@mui/icons-material'
import {useUser} from '@contexts/user/UserContext.ts'
import {createEventGlobal} from '@authorization/privileges.ts'

const DragAndDropCalendar = withDragAndDrop<WorkShiftWithAssignedUsersDto, WorkTypeDto>(
    Calendar<WorkShiftWithAssignedUsersDto, WorkTypeDto>,
)

const locales = {
    'de-DE': de,
    'en-GB': enGB,
}

const lang = {
    'en-GB': {
        // use default values
    },
    'de-DE': {
        date: 'Datum',
        time: 'Uhrzeit',
        week: 'Woche',
        day: 'Tag',
        month: 'Monat',
        previous: 'Zurück',
        next: 'Vor',
        today: 'Heute',
        agenda: 'Agenda',
        noEventsInRange: 'Keine Schichten in diesem Zeitraum',
        event: 'Schicht',
        allDay: 'ganztägig',
        showMore: (total: number) => `+${total} weitere`,
    },
}

export const Shiftplan = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()

    const [activeResources, setActiveResources] = useState<Record<string, boolean>>({})
    const [currentView, setCurrentView] = useState<View>('week')
    const [lastRequested, setLastRequested] = useState(Date.now())

    const {eventId} = eventIndexRoute.useParams()

    const dialogProps = useEntityAdministration<WorkShiftWithAssignedUsersDto>(
        t('work.shift.shift'),
    )

    const {data: workTypes} = useFetch(signal => getWorkTypes({signal}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(t('common.load.error.single', {entity: t('work.type.types')}))
            }
        },
    })

    const {data: shiftsData} = useFetch(signal => getWorkShifts({signal, path: {eventId}}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(t('common.load.error.single', {entity: t('work.shift.shifts')}))
            }
        },
        deps: [lastRequested],
    })

    const shifts = useMemo(() => {
        return (
            shiftsData?.data
                .filter(s => activeResources[s.workType])
                .map(s => ({...s, resourceId: s.workType})) || []
        )
    }, [shiftsData?.data, activeResources])

    useEffect(() => {
        setActiveResources(
            workTypes?.data.reduce((acc: Record<string, boolean>, workType) => {
                acc[workType.id] = true
                return acc
            }, {}) || {},
        )
    }, [workTypes])

    const userCanEdit = useMemo(() => user.checkPrivilege(createEventGlobal), [user])

    const filteredResources = useMemo(
        () => workTypes?.data?.filter(r => activeResources[r.id]) || [],
        [activeResources, workTypes],
    )

    const {messages, culture} = useMemo(() => {
        if (user.language === 'de') {
            return {messages: lang['de-DE'], culture: 'de-DE'}
        } else {
            return {messages: lang['en-GB'], culture: 'en-GB'}
        }
    }, [user.language, lang])

    const localizer = dateFnsLocalizer({
        format,
        parse,
        startOfWeek,
        getDay,
        locales,
    })

    const EventAgenda = ({event}: {event: WorkShiftWithAssignedUsersDto}) => {
        return (
            <span>
                <Stack direction={'row'} spacing={2}>
                    <Box width={'20px'}>
                        {event.minUser <= event.assignedUsers.length && (
                            <CheckCircleOutlined fontSize={'small'} />
                        )}
                    </Box>
                    <Typography>{event.workTypeName}:</Typography>
                    <Typography>{event.title}</Typography>
                </Stack>
            </span>
        )
    }

    const EventMonth = ({event}: {event: WorkShiftWithAssignedUsersDto}) => {
        return (
            <span>
                <Stack direction={'row'} spacing={1}>
                    <Typography fontSize={'small'}>
                        {new Date(event.timeFrom).toLocaleTimeString([], {
                            hour: '2-digit',
                            minute: '2-digit',
                        })}
                    </Typography>
                    <Typography fontSize={'small'}>{event.workTypeName}</Typography>
                    {event.minUser <= event.assignedUsers.length && (
                        <CheckCircleOutlined fontSize={'small'} />
                    )}
                </Stack>
            </span>
        )
    }

    const EventDay = ({event}: {event: WorkShiftWithAssignedUsersDto}) => {
        return (
            <span>
                <Stack>
                    <Typography fontSize={'small'}>{event.workTypeName}</Typography>
                    <Typography>{event.title}</Typography>
                </Stack>
                {event.minUser <= event.assignedUsers.length && (
                    <Box position={'absolute'} right={'0px'} bottom={'0px'}>
                        <CheckCircleOutlined fontSize={'small'} />
                    </Box>
                )}
            </span>
        )
    }

    const eventStyleGetter: EventPropGetter<WorkShiftWithAssignedUsersDto> = event => {
        const backgroundColor =
            event.assignedUsers.length >= event.minUser
                ? workTypes?.data.find(r => r.id === event.workType)?.color
                : workTypes?.data.find(r => r.id === event.workType)?.color + '75'
        const style = {
            backgroundColor: backgroundColor,
            opacity: 0.8,
            border: '1px solid white',
        }
        return {
            style: style,
        }
    }

    const dayStyleGetter: DayPropGetter = (_, resourceId) => {
        const backgroundColor = workTypes?.data.find(r => r.id === resourceId)?.color
        const style = {
            backgroundColor: backgroundColor + '25',
        }
        return {
            style: style,
        }
    }

    const {components, defaultDate, scrollToTime} = useMemo(
        () => ({
            components: {
                agenda: {
                    event: EventAgenda,
                },
                month: {
                    event: EventMonth,
                },
                week: {
                    event: EventDay,
                },
                day: {
                    event: EventDay,
                },
            },
            defaultDate: new Date(),
            scrollToTime: new Date(new Date().setHours(8)),
        }),
        [],
    )

    const moveEvent: (args: EventInteractionArgs<WorkShiftWithAssignedUsersDto>) => void =
        useCallback(
            ({event, start, end, resourceId}) => {
                dialogProps.table.openDialog({
                    ...event,
                    timeFrom: formatISO(start).slice(0, 19),
                    timeTo: formatISO(end).slice(0, 19),
                    workType: resourceId?.toString() || event.workType,
                })
            },
            [dialogProps.table],
        )

    const resizeEvent: (args: EventInteractionArgs<WorkShiftWithAssignedUsersDto>) => void =
        useCallback(
            ({event, start, end, resourceId}) => {
                dialogProps.table.openDialog({
                    ...event,
                    timeFrom: formatISO(start).slice(0, 19),
                    timeTo: formatISO(end).slice(0, 19),
                    workType: resourceId?.toString() || event.workType,
                })
            },
            [dialogProps.table],
        )

    const handleSelectSlot: (slotInfo: SlotInfo) => void = useCallback(
        ({start, end, resourceId, action}) => {
            if (currentView != 'month' || action === 'doubleClick') {
                openDialog(start, end, resourceId as string)
            }
        },
        [currentView],
    )

    const openDialog = (start: Date, end: Date, resourceId: string) => {
        dialogProps.table.openDialog({
            id: '',
            minUser: 0,
            timeFrom: formatISO(start).slice(0, 19),
            timeTo: formatISO(end).slice(0, 19),
            workType: resourceId,
            assignedUsers: [],
            event: '',
            eventName: '',
            workTypeName: '',
            title: '',
            createdAt: '',
            updatedAt: '',
        })
    }

    const handleSelectEvent = (event: WorkShiftWithAssignedUsersDto) => {
        dialogProps.table.openDialog(event)
    }

    const handleViewChange: (view: View) => void = view => {
        setCurrentView(view)
    }

    return (
        <Stack spacing={2}>
            <Typography variant={'h2'}>{t('work.shift.plan')}</Typography>
            <Stack direction={'row'} justifyContent={'space-between'} alignItems={'start'}>
                <Alert icon={<FilterAlt />} color={'info'} sx={{flexGrow: 1}}>
                    <Stack>
                        <Stack direction={'row'} spacing={1} alignItems={'center'}>
                            <Typography variant={'h6'}>{t('work.type.types')}</Typography>
                            <InlineLink
                                to={'/config'}
                                search={{tab: 'event-elements'}}
                                hash={'worktypes'}>
                                {t('common.manage')}
                            </InlineLink>
                        </Stack>
                        <FormGroup row>
                            {workTypes?.data.length == 0 && (
                                <Typography>{t('work.type.noneAvailable')}</Typography>
                            )}
                            {workTypes?.data.map(wt => (
                                <FormControlLabel
                                    key={wt.id}
                                    control={
                                        <Switch
                                            style={{color: wt.color}}
                                            checked={activeResources[wt.id] || false}
                                            onChange={(_, checked) =>
                                                setActiveResources({
                                                    ...activeResources,
                                                    [wt.id]: checked,
                                                })
                                            }
                                        />
                                    }
                                    label={wt.name}
                                />
                            ))}
                        </FormGroup>
                    </Stack>
                </Alert>
            </Stack>
            {userCanEdit && (
                <Stack direction={'row'} justifyContent={'flex-end'}>
                    <Button
                        variant={'outlined'}
                        startIcon={<Add />}
                        onClick={() =>
                            openDialog(new Date(), new Date(), workTypes?.data[0]?.id || '')
                        }>
                        {t('entity.add.action', {entity: t('work.shift.shift')})}
                    </Button>
                </Stack>
            )}
            <ShiftDialog {...dialogProps.dialog} reloadData={() => setLastRequested(Date.now())} />
            <Box height={800} maxWidth={'100%'}>
                <DragAndDropCalendar
                    culture={culture}
                    defaultDate={defaultDate}
                    components={components}
                    messages={messages}
                    localizer={localizer}
                    defaultView={currentView}
                    events={shifts}
                    eventPropGetter={eventStyleGetter}
                    dayPropGetter={dayStyleGetter}
                    resources={filteredResources}
                    onEventDrop={moveEvent}
                    onView={handleViewChange}
                    onEventResize={resizeEvent}
                    onSelectSlot={handleSelectSlot}
                    onDoubleClickEvent={handleSelectEvent}
                    step={15}
                    timeslots={4}
                    showMultiDayTimes
                    formats={{
                        eventTimeRangeStartFormat: r => {
                            return `${format(r.start, 'HH:mm')} –`
                        },
                        eventTimeRangeEndFormat: r => {
                            return `- ${format(r.end, 'HH:mm')}`
                        },
                    }}
                    resizable
                    selectable
                    popup
                    scrollToTime={scrollToTime}
                    resourceGroupingLayout
                    resourceIdAccessor={r => r.id}
                    resourceTitleAccessor={r => r.name}
                    startAccessor={s => new Date(s.timeFrom)}
                    endAccessor={s => new Date(s.timeTo)}
                />
            </Box>
        </Stack>
    )
}

import React, {useMemo} from 'react'
import {
    Button,
    Divider,
    List,
    ListItem,
    ListItemText,
    ListSubheader,
    Stack,
    Typography,
} from '@mui/material'
import {CalendarIcon, ClockIcon} from '@mui/x-date-pickers'
import {useTranslation} from 'react-i18next'
import {DashboardWidget} from '@components/dashboard/DashboardWidget.tsx'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getWorkShiftsForUser} from '@api/sdk.gen.ts'
import {Link} from '@tanstack/react-router'
import {formatISO} from 'date-fns'
import {WorkShiftWithAssignedUsersDto} from '@api/types.gen.ts'
import {blue} from '@mui/material/colors'
import {InfoOutlined} from '@mui/icons-material'

type GroupedShiftWithFormatedTime = WorkShiftWithAssignedUsersDto & {
    timeFromFormated: string
    timeToFormated: string
}

type DateGroup = {
    date: string
    items: GroupedShiftWithFormatedTime[]
}

export function ShiftWidget({userId}: {userId: string}) {
    const feedback = useFeedback()
    const {t} = useTranslation()

    const {data: shifts} = useFetch(
        signal =>
            getWorkShiftsForUser({
                signal,
                path: {userId},
                query: {
                    sort: JSON.stringify([{field: 'TIME_FROM', direction: 'ASC'}]),
                    timeFrom: formatISO(Date.now()).slice(0, 19),
                },
            }),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.single', {
                            entity: t('task.task'),
                        }),
                    )
                    console.log(error)
                }
            },
        },
    )

    const getDateRange = (from: Date, to: Date): string[] => {
        const dates: string[] = []
        const current = new Date(from)

        while (current <= to) {
            dates.push(current.toLocaleDateString())
            current.setUTCDate(current.getUTCDate() + 1)
        }

        return dates
    }

    const formatTime = (date: Date): string => {
        return date.toLocaleTimeString([], {
            hour: '2-digit',
            minute: '2-digit',
        })
    }

    const groupedShifts = useMemo(() => {
        // Temporary object to group by date
        const tempGrouped: Record<string, GroupedShiftWithFormatedTime[]> = {}

        for (const item of shifts?.data || []) {
            const fromDate = new Date(item.timeFrom)
            const toDate = new Date(item.timeTo)
            const dateRange = getDateRange(fromDate, toDate)

            for (const date of dateRange) {
                const isFirstDay = date === fromDate.toLocaleDateString()
                const isLastDay = date === toDate.toLocaleDateString()

                const timeFromFormated = isFirstDay ? formatTime(fromDate) : '00:00'
                const timeToFormated = isLastDay ? formatTime(toDate) : '23:59'

                const groupedItem: GroupedShiftWithFormatedTime = {
                    ...item,
                    timeFromFormated,
                    timeToFormated,
                }

                if (!tempGrouped[date]) {
                    tempGrouped[date] = []
                }
                tempGrouped[date].push(groupedItem)
            }
        }

        const groupedByDate: DateGroup[] = Object.entries(tempGrouped).map(([date, items]) => ({
            date,
            items,
        }))

        return groupedByDate
    }, [shifts?.data])

    return (
        <React.Fragment>
            <DashboardWidget
                size={12}
                color={'#9d2a2a38'}
                header={`${t('work.shift.myShifts')} (${shifts?.pagination.total ?? 0})`}
                content={
                    <React.Fragment>
                        <List
                            subheader={<li />}
                            sx={{
                                width: '100%',
                                position: 'relative',
                                overflow: 'auto',
                                maxHeight: 280,
                                '& ul': {padding: 0},
                            }}>
                            {groupedShifts.map(group => (
                                <>
                                    <ListSubheader
                                        key={group.date}
                                        style={{
                                            backgroundColor: blue['50'],
                                        }}>
                                        <Stack direction="row" spacing={2} alignItems={'center'}>
                                            <CalendarIcon />
                                            <Typography
                                                variant={'overline'}
                                                fontSize={'small'}
                                                fontWeight={'bold'}>
                                                {group.date}
                                            </Typography>
                                        </Stack>
                                    </ListSubheader>
                                    {group.items.map((shift, index) => (
                                        <>
                                            {index !== 0 && (
                                                <Divider
                                                    key={'div-' + shift.id}
                                                    variant={'middle'}
                                                />
                                            )}
                                            <ListItem key={shift.id}>
                                                <ListItemText
                                                    primary={
                                                        <Stack
                                                            direction={'row'}
                                                            spacing={2}
                                                            alignItems={'center'}>
                                                            <Stack direction={'row'}>
                                                                <Typography
                                                                    variant={'overline'}
                                                                    fontSize={'small'}
                                                                    fontWeight={'bold'}>
                                                                    {shift.timeFromFormated} -{' '}
                                                                    {shift.timeToFormated}
                                                                </Typography>
                                                            </Stack>
                                                            <Typography>
                                                                {shift.workTypeName}
                                                            </Typography>
                                                            <Typography>|</Typography>
                                                            <Link
                                                                to={'/event/$eventId'}
                                                                search={{
                                                                    tab: 'organization',
                                                                }}
                                                                params={{eventId: shift.event}}>
                                                                <Button>
                                                                    <Typography color={'primary'}>
                                                                        {shift.eventName}
                                                                    </Typography>
                                                                </Button>
                                                            </Link>
                                                            <Stack
                                                                flexGrow={1}
                                                                direction={'row'}
                                                                alignItems={'center'}
                                                                justifyContent={'end'}
                                                                color={'textSecondary'}
                                                                spacing={1}>
                                                                <ClockIcon fontSize={'small'} />
                                                                <Typography
                                                                    fontSize={'small'}
                                                                    color={'textSecondary'}>
                                                                    {new Date(
                                                                        shift.createdAt,
                                                                    ).toLocaleString()}
                                                                </Typography>
                                                            </Stack>
                                                        </Stack>
                                                    }
                                                    secondary={
                                                        shift.remark && (
                                                            <Stack
                                                                spacing={1}
                                                                direction={'row'}
                                                                alignItems={'center'}>
                                                                <InfoOutlined fontSize={'small'} />
                                                                <Typography fontSize={'small'}>
                                                                    {shift.remark}
                                                                </Typography>
                                                            </Stack>
                                                        )
                                                    }
                                                />
                                            </ListItem>
                                        </>
                                    ))}
                                </>
                            ))}
                        </List>
                    </React.Fragment>
                }
            />
        </React.Fragment>
    )
}

import React, {Fragment} from 'react'
import {Button, Divider, List, ListItem, ListItemText, Stack, Typography} from '@mui/material'
import {ClockIcon} from '@mui/x-date-pickers'
import {useTranslation} from 'react-i18next'
import {DashboardWidget} from '@components/dashboard/DashboardWidget.tsx'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getPublicEvents} from '@api/sdk.gen.ts'
import {Link} from '@tanstack/react-router'
import {Event, Forward, LocationOn} from '@mui/icons-material'

export function UpcomingEventsWidget(props: {hideRegistration?: boolean}) {
    const feedback = useFeedback()
    const {t} = useTranslation()

    const {data: events} = useFetch(
        signal =>
            getPublicEvents({
                signal,
                query: {
                    limit: 5,
                    sort: JSON.stringify([
                        {field: 'EVENT_FROM', direction: 'ASC'},
                        {field: 'EVENT_TO', direction: 'ASC'},
                        {field: 'NAME', direction: 'ASC'},
                    ]),
                },
            }),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('common.load.error.single', {entity: t('event.events')}))
                    console.log(error)
                }
            },
        },
    )

    return (
        <React.Fragment>
            <DashboardWidget
                size={12}
                header={t('event.upcoming')}
                content={
                    <React.Fragment>
                        <List>
                            {events?.data?.map((event, index) => (
                                <Fragment key={`event-${event.id}`}>
                                    {index !== 0 && (
                                        <Divider key={`divider-${event.id}`} variant={'middle'} />
                                    )}
                                    <ListItem
                                        key={event.id}
                                        secondaryAction={
                                            !props.hideRegistration &&
                                            ((event.registrationAvailableFrom != null &&
                                                new Date(event.registrationAvailableFrom) <
                                                    new Date()) ||
                                                (event.registrationAvailableTo != null &&
                                                    new Date(event.registrationAvailableTo) >
                                                        new Date())) && (
                                                <Link
                                                    to={'/event/$eventId/register'}
                                                    params={{eventId: event.id}}>
                                                    <Button
                                                        endIcon={<Forward />}
                                                        variant={'contained'}>
                                                        {t('event.registerNow')}
                                                    </Button>
                                                </Link>
                                            )
                                        }>
                                        <ListItemText
                                            disableTypography={true}
                                            primary={
                                                <Stack>
                                                    <Stack
                                                        direction={'row'}
                                                        spacing={2}
                                                        alignItems={'center'}>
                                                        <Link
                                                            to={'/event/$eventId'}
                                                            params={{eventId: event.id}}>
                                                            <Button>
                                                                <Typography
                                                                    color={'primary'}
                                                                    variant={'h6'}>
                                                                    {event.name}
                                                                </Typography>
                                                            </Button>
                                                        </Link>
                                                        <Stack
                                                            direction="row"
                                                            divider={
                                                                <Divider
                                                                    orientation="vertical"
                                                                    flexItem
                                                                />
                                                            }
                                                            spacing={2}>
                                                            {event.eventFrom != null && (
                                                                <Stack
                                                                    direction="row"
                                                                    spacing={0.5}>
                                                                    <Event
                                                                        fontSize={'small'}
                                                                        sx={{
                                                                            color: 'text.secondary',
                                                                        }}
                                                                    />
                                                                    <Typography
                                                                        color={'text.secondary'}>
                                                                        {new Date(
                                                                            event.eventFrom,
                                                                        ).toLocaleDateString()}
                                                                        {event.eventTo !==
                                                                            event.eventFrom &&
                                                                            ` - ${new Date(
                                                                                event.eventTo!!,
                                                                            ).toLocaleDateString()}`}
                                                                    </Typography>
                                                                </Stack>
                                                            )}
                                                            {event.location && (
                                                                <Stack
                                                                    direction="row"
                                                                    spacing={0.5}>
                                                                    <LocationOn
                                                                        fontSize={'small'}
                                                                        sx={{
                                                                            color: 'text.secondary',
                                                                        }}
                                                                    />
                                                                    <Typography
                                                                        color={'text.secondary'}>
                                                                        {event.location}
                                                                    </Typography>
                                                                </Stack>
                                                            )}
                                                            <Typography color={'text.secondary'}>
                                                                {event.competitionCount}{' '}
                                                                {t(
                                                                    'event.competition.competitions',
                                                                )}
                                                            </Typography>
                                                        </Stack>
                                                    </Stack>
                                                    {event.description && (
                                                        <Stack p={1}>
                                                            <Typography fontWeight={'light'}>
                                                                {event.description}
                                                            </Typography>
                                                        </Stack>
                                                    )}
                                                </Stack>
                                            }
                                            secondary={
                                                <Stack
                                                    fontSize={'1em'}
                                                    direction={'row'}
                                                    alignItems={'center'}
                                                    p={1}
                                                    spacing={1}>
                                                    <ClockIcon fontSize={'small'} />
                                                    <Typography
                                                        variant={'body2'}
                                                        color={'text.secondary'}>
                                                        {t('event.registrationAvailable.timespan')}:
                                                    </Typography>
                                                    {event.registrationAvailableFrom != null && (
                                                        <Typography
                                                            variant={'body2'}
                                                            color={'text.secondary'}>
                                                            {t('event.registrationAvailable.from')}{' '}
                                                            {new Date(
                                                                event.registrationAvailableFrom,
                                                            ).toLocaleString()}
                                                        </Typography>
                                                    )}
                                                    {event.registrationAvailableTo != null && (
                                                        <Typography
                                                            variant={'body2'}
                                                            color={'text.secondary'}>
                                                            {t('event.registrationAvailable.to')}{' '}
                                                            {new Date(
                                                                event.registrationAvailableTo,
                                                            ).toLocaleString()}
                                                        </Typography>
                                                    )}
                                                    {event.registrationAvailableFrom == null &&
                                                        event.registrationAvailableTo == null && (
                                                            <Typography
                                                                variant={'body2'}
                                                                color={'text.secondary'}>
                                                                {t(
                                                                    'event.registrationAvailable.unknown',
                                                                )}
                                                            </Typography>
                                                        )}
                                                </Stack>
                                            }
                                        />
                                    </ListItem>
                                </Fragment>
                            ))}
                        </List>
                    </React.Fragment>
                }
            />
        </React.Fragment>
    )
}

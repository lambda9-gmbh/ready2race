import React, {Fragment, useState} from 'react'
import {Box, Button, Divider, List, ListItem, ListItemText, Stack, Typography} from '@mui/material'
import {ClockIcon} from '@mui/x-date-pickers'
import {Assignment, Groups, Message} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import {EventRegistrationMessageDialog} from '@components/dashboard/EventRegistrationMessageDialog.tsx'
import {DashboardWidget} from '@components/dashboard/DashboardWidget.tsx'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getEventRegistrations} from '@api/sdk.gen.ts'
import {Link} from '@tanstack/react-router'

export function EventRegistrationsWidget() {
    const [messageDialogOpen, setMessageDialogOpen] = useState(false)
    const [message, setMessage] = useState<string | undefined>()
    const feedback = useFeedback()
    const {t} = useTranslation()

    const {data: registrations} = useFetch(
        signal =>
            getEventRegistrations({
                signal,
                query: {
                    sort: JSON.stringify([{field: 'CREATED_AT', direction: 'DESC'}]),
                    limit: 10,
                },
            }),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.single', {
                            entity: t('event.registration.registrations'),
                        }),
                    )
                    console.log(error)
                }
            },
        },
    )

    const showNachricht = (msg?: string) => {
        setMessage(msg)
        setMessageDialogOpen(true)
    }

    return (
        <React.Fragment>
            <DashboardWidget
                size={12}
                header={t('event.registration.mostRecent')}
                content={
                    <React.Fragment>
                        <List>
                            {registrations?.data?.map((registration, index) => (
                                <Fragment key={registration.id}>
                                    {index !== 0 && <Divider variant={'middle'} />}
                                    <ListItem>
                                        <ListItemText
                                            primary={
                                                <Stack
                                                    direction={'row'}
                                                    spacing={2}
                                                    alignItems={'center'}>
                                                    <Link
                                                        to={'/club/$clubId'}
                                                        params={{clubId: registration.clubId}}>
                                                        <Button color={'primary'}>
                                                            <Typography>
                                                                {registration.clubName}
                                                            </Typography>
                                                        </Button>
                                                    </Link>
                                                    <Typography>|</Typography>
                                                    <Link
                                                        to={'/event/$eventId'}
                                                        params={{eventId: registration.eventId}}>
                                                        <Button>
                                                            <Typography color={'primary'}>
                                                                {registration.eventName}
                                                            </Typography>
                                                        </Button>
                                                    </Link>
                                                    <Stack
                                                        direction={'row'}
                                                        spacing={1}
                                                        color={'text.secondary'}>
                                                        <Groups />
                                                        <Typography>
                                                            {registration.participantCount}
                                                        </Typography>
                                                    </Stack>
                                                    <Stack
                                                        direction={'row'}
                                                        spacing={1}
                                                        color={'text.secondary'}>
                                                        <Assignment />
                                                        <Typography>
                                                            {
                                                                registration.competitionRegistrationCount
                                                            }
                                                        </Typography>
                                                    </Stack>
                                                </Stack>
                                            }
                                            secondary={
                                                <Stack
                                                    direction={'row'}
                                                    alignItems={'center'}
                                                    spacing={3}>
                                                    <Stack
                                                        direction={'row'}
                                                        alignItems={'center'}
                                                        spacing={1}>
                                                        <ClockIcon fontSize={'small'} />
                                                        <Box>
                                                            {new Date(
                                                                registration.createdAt,
                                                            ).toLocaleString()}
                                                        </Box>
                                                    </Stack>
                                                    {registration.message && (
                                                        <Stack
                                                            direction={'row'}
                                                            alignItems={'center'}
                                                            spacing={0}>
                                                            <Message fontSize={'small'} />
                                                            <Button
                                                                variant={'text'}
                                                                onClick={() =>
                                                                    showNachricht(
                                                                        registration.message,
                                                                    )
                                                                }>
                                                                {t(
                                                                    'dashboard.registration.showMessage',
                                                                )}
                                                            </Button>
                                                        </Stack>
                                                    )}
                                                </Stack>
                                            }
                                        />
                                    </ListItem>
                                </Fragment>
                            ))}
                        </List>
                        <EventRegistrationMessageDialog
                            open={messageDialogOpen}
                            onClose={() => setMessageDialogOpen(false)}
                            content={message}
                        />
                    </React.Fragment>
                }
            />
        </React.Fragment>
    )
}

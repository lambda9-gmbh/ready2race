import React, {Fragment, useState} from 'react'
import {Box, Button, Divider, List, ListItem, ListItemText, Stack, Typography} from '@mui/material'
import {ClockIcon} from '@mui/x-date-pickers'
import {Assignment, Groups, Message, HowToReg} from '@mui/icons-material'
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
                        {registrations?.data && registrations.data.length === 0 ? (
                            <Stack
                                alignItems={'center'}
                                justifyContent={'center'}
                                spacing={2}
                                py={4}>
                                <HowToReg sx={{fontSize: 48, color: 'text.secondary'}} />
                                <Typography color={'text.secondary'} textAlign={'center'}>
                                    No recent registrations
                                </Typography>
                            </Stack>
                        ) : (
                            <List>
                                {registrations?.data?.map((registration, index) => (
                                    <Fragment key={registration.id}>
                                        {index !== 0 && <Divider variant={'middle'} />}
                                        <ListItem>
                                            <ListItemText
                                                primary={
                                                    <Box
                                                        sx={{
                                                            display: 'flex',
                                                            flexWrap: 'wrap',
                                                            gap: 1,
                                                            alignItems: 'center',
                                                        }}>
                                                        <Link
                                                            to={'/club/$clubId'}
                                                            params={{clubId: registration.clubId}}>
                                                            <Button
                                                                color={'primary'}
                                                                size={'small'}>
                                                                <Typography>
                                                                    {registration.clubName}
                                                                </Typography>
                                                            </Button>
                                                        </Link>
                                                        <Box
                                                            sx={{
                                                                display: {xs: 'none', md: 'block'},
                                                            }}>
                                                            <Typography>|</Typography>
                                                        </Box>
                                                        <Link
                                                            to={'/event/$eventId'}
                                                            params={{
                                                                eventId: registration.eventId,
                                                            }}>
                                                            <Button size={'small'}>
                                                                <Typography color={'primary'}>
                                                                    {registration.eventName}
                                                                </Typography>
                                                            </Button>
                                                        </Link>
                                                        <Stack
                                                            direction={'row'}
                                                            spacing={1}
                                                            color={'text.secondary'}>
                                                            <Groups fontSize={'small'} />
                                                            <Typography fontSize={'small'}>
                                                                {registration.participantCount}
                                                            </Typography>
                                                        </Stack>
                                                        <Stack
                                                            direction={'row'}
                                                            spacing={1}
                                                            color={'text.secondary'}>
                                                            <Assignment fontSize={'small'} />
                                                            <Typography fontSize={'small'}>
                                                                {
                                                                    registration.competitionRegistrationCount
                                                                }
                                                            </Typography>
                                                        </Stack>
                                                    </Box>
                                                }
                                                secondary={
                                                    <Box
                                                        sx={{
                                                            display: 'flex',
                                                            flexWrap: 'wrap',
                                                            gap: 1,
                                                            alignItems: 'center',
                                                            mt: 1,
                                                        }}>
                                                        <Stack
                                                            direction={'row'}
                                                            alignItems={'center'}
                                                            spacing={1}>
                                                            <ClockIcon fontSize={'small'} />
                                                            <Typography fontSize={'small'}>
                                                                {new Date(
                                                                    registration.createdAt,
                                                                ).toLocaleString()}
                                                            </Typography>
                                                        </Stack>
                                                        {registration.message && (
                                                            <Stack
                                                                direction={'row'}
                                                                alignItems={'center'}
                                                                spacing={0}>
                                                                <Message fontSize={'small'} />
                                                                <Button
                                                                    variant={'text'}
                                                                    size={'small'}
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
                                                    </Box>
                                                }
                                            />
                                        </ListItem>
                                    </Fragment>
                                ))}
                            </List>
                        )}
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

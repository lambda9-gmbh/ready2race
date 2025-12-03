import React, {Fragment, useState} from 'react'
import {Box, Button, Divider, List, ListItem, Stack, Typography} from '@mui/material'
import {ClockIcon} from '@mui/x-date-pickers'
import {Assignment, Groups, Message, HowToReg} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import {EventRegistrationMessageDialog} from '@components/dashboard/EventRegistrationMessageDialog.tsx'
import {DashboardWidget} from '@components/dashboard/DashboardWidget.tsx'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getEventRegistrations} from '@api/sdk.gen.ts'
import {Link} from '@tanstack/react-router'
import {format} from 'date-fns'

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
                                    {t('dashboard.registrations.empty')}
                                </Typography>
                            </Stack>
                        ) : (
                            <List>
                                {registrations?.data?.map((registration, index) => (
                                    <Fragment key={registration.id}>
                                        {index !== 0 && <Divider variant={'middle'} />}
                                        <ListItem sx={{py: {xs: 2, md: 2.5}, px: {xs: 1, md: 2}}}>
                                            <Stack spacing={1.5} width={1} color={'text.secondary'}>
                                                <Stack
                                                    direction={{xs: 'column', md: 'row'}}
                                                    spacing={{xs: 1, md: 1.5}}
                                                    alignItems={{xs: 'flex-start', md: 'center'}}
                                                    flexWrap="wrap">
                                                    <Stack
                                                        direction="row"
                                                        spacing={1}
                                                        alignItems="center"
                                                        flexWrap="wrap">
                                                        <Link
                                                            to={'/club/$clubId'}
                                                            params={{clubId: registration.clubId}}>
                                                            <Button size={'small'}>
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
                                                    </Stack>
                                                    <Stack
                                                        direction="row"
                                                        spacing={2}
                                                        color={'text.secondary'}>
                                                        <Stack
                                                            direction={'row'}
                                                            spacing={0.75}
                                                            alignItems={'center'}>
                                                            <Groups fontSize={'medium'} />
                                                            <Typography>
                                                                {registration.participantCount}
                                                            </Typography>
                                                        </Stack>
                                                        <Stack
                                                            direction={'row'}
                                                            spacing={0.75}
                                                            alignItems={'center'}>
                                                            <Assignment fontSize={'medium'} />
                                                            <Typography>
                                                                {
                                                                    registration.competitionRegistrationCount
                                                                }
                                                            </Typography>
                                                        </Stack>
                                                    </Stack>
                                                </Stack>
                                                <Stack
                                                    direction={{xs: 'column', sm: 'row'}}
                                                    spacing={{xs: 1, sm: 2}}
                                                    alignItems={{xs: 'flex-start', sm: 'center'}}
                                                    color={'text.secondary'}>
                                                    <Stack
                                                        direction={'row'}
                                                        alignItems={'center'}
                                                        spacing={0.75}>
                                                        <ClockIcon fontSize={'medium'} />
                                                        <Typography>
                                                            {format(
                                                                new Date(registration.createdAt),
                                                                t('format.datetime'),
                                                            )}
                                                        </Typography>
                                                    </Stack>
                                                    {registration.message && (
                                                        <Stack
                                                            direction={'row'}
                                                            alignItems={'center'}
                                                            spacing={0.5}>
                                                            <Message fontSize={'medium'} />
                                                            <Button
                                                                variant={'text'}
                                                                size={'medium'}
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
                                            </Stack>
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

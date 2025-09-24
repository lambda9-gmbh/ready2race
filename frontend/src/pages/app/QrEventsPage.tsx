import {Box, Button, Stack, Typography} from '@mui/material'
import {router} from '@routes'
import {useTranslation} from 'react-i18next'
import {useAppSession} from '@contexts/app/AppSessionContext'
import {useEffect} from "react";

const QrEventsPage = () => {
    const {t} = useTranslation()
    const navigate = router.navigate
    const {events} = useAppSession()

    useEffect(() => {
        if (events.length === 1) {
            navigate({to: '/app/$eventId/function', params: {eventId: events[0].id}})
        }
    }, [events, navigate])

    return (
        <Box sx={{width: 1, maxWidth: 600}}>
            <Stack spacing={2} sx={{width: 1}}>
                <Typography variant="h4" textAlign="center" gutterBottom>
                    {t('qrEvents.title')}
                </Typography>
                {events?.map(event => (
                    <Button
                        key={event.id}
                        onClick={() =>
                            navigate({
                                to: '/app/$eventId/function',
                                params: {eventId: event.id},
                            })
                        }
                        fullWidth
                        variant="contained"
                        color="primary">
                        {event.name}
                    </Button>
                ))}
            </Stack>
        </Box>
    )
}

export default QrEventsPage

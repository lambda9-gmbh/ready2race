import {Button, Grid2, Stack, Typography} from '@mui/material'
import {Trans, useTranslation} from 'react-i18next'
import {UpcomingEventsWidget} from '@components/dashboard/UpcomingEventsWidget.tsx'
import {Link} from '@tanstack/react-router'
import {ArrowForward} from '@mui/icons-material'

const LandingPage = () => {
    const {t} = useTranslation()

    return (
        <Grid2 container spacing={2} p={1} height={'100%'} alignContent={'flex-start'}>
            <Grid2 size={{xs: 12}}>
                <Stack direction={'row'} justifyContent={'space-between'}>
                    <Typography variant={'h5'}>{t('event.events')}</Typography>
                    <Link to={'.'}>
                        <Button variant={'text'} endIcon={<ArrowForward />}>
                            <Trans i18nKey={'landing.liveEventsLink'} />
                        </Button>
                    </Link>
                </Stack>
            </Grid2>
            <UpcomingEventsWidget hideRegistration={true} />
        </Grid2>
    )
}

export default LandingPage

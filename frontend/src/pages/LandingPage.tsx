import {Grid2, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {UpcomingEventsWidget} from '@components/dashboard/UpcomingEventsWidget.tsx'

const LandingPage = () => {
    const {t} = useTranslation()

    return (
        <Grid2 container spacing={2} p={1} height={'100%'} alignContent={'flex-start'}>
            <Grid2 size={{xs: 12}}>
                <Typography variant={'h5'}>{t('event.events')}</Typography>
            </Grid2>
            <UpcomingEventsWidget hideRegistration={true} />
        </Grid2>
    )
}

export default LandingPage

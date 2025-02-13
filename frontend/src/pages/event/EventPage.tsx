import {EventDayDto, getEvent, RaceDto} from '../../api'
import {Box, Typography} from '@mui/material'
import {useEntityAdministration, useFeedback, useFetch} from '../../utils/hooks.ts'
import {eventRoute} from '../../routes.tsx'
import {useTranslation} from 'react-i18next'
import RaceTable from '../../components/event/race/RaceTable.tsx'
import RaceDialog from '../../components/event/race/RaceDialog.tsx'
import Throbber from '../../components/Throbber.tsx'
import EventDayDialog from '../../components/event/eventDay/EventDayDialog.tsx'
import EventDayTable from '../../components/event/eventDay/EventDayTable.tsx'

const EventPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()

    const {data} = useFetch(
        signal => getEvent({signal, path: {eventId: eventId}}),
        {
            onResponse: result => {
                if (result.error) {
                    feedback.error(t('common.load.error.single', {entity: t('event.event')}))
                    console.log(result.error)
                }
            },
            deps:
                [eventId],
        },
    )

    const raceAdministrationProps = useEntityAdministration<RaceDto>(t('event.race.race'))
    const eventDayAdministrationProps = useEntityAdministration<EventDayDto>(t('event.eventDay.eventDay'))

    return (
        <Box sx={{display: 'flex', flexDirection: 'column'}}>
            {(data && (
                <>
                    <Typography variant="h3">{data.name}</Typography>
                    <Box sx={{mt: 4}}>
                        <Typography variant="h4">{t('event.race.races')}</Typography>
                        <RaceTable {...raceAdministrationProps.table} />
                        <RaceDialog {...raceAdministrationProps.dialog} />
                    </Box>
                    <Box sx={{mt: 4}}>
                        <Typography variant="h4">{t('event.eventDay.eventDays')}</Typography>
                        <EventDayTable {...eventDayAdministrationProps.table} />
                        <EventDayDialog {...eventDayAdministrationProps.dialog} />
                    </Box>
                </>
            )) || <Throbber />}
        </Box>
    )
}

export default EventPage

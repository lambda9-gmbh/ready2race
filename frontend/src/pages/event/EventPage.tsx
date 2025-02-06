import {EventDayDto, getEvent, RaceDto} from '../../api'
import {Box, Typography} from '@mui/material'
import {useEntityAdministration, useFeedback, useFetch} from '../../utils/hooks.ts'
import {eventRoute} from '../../routes.tsx'
import {useTranslation} from 'react-i18next'
import RaceTable from "../../components/event/race/RaceTable.tsx";
import RaceDialog from "../../components/event/race/RaceDialog.tsx";

const EventPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()

    const {data, pending} = useFetch(
        signal => getEvent({signal, path: {eventId: eventId}}),
        {
            onResponse: result => {
                console.log('Event response:')
                if (result.error) {
                    feedback.error(t('common.load.error', {entity: t('event.event')}))
                    console.log(result.error)
                } else if (result.data) {
                    console.log(result.data)
                }
            },
        },
        [eventId],
    )

    const raceAdministrationProps = useEntityAdministration<RaceDto>()
    const eventDayAdministrationProps = useEntityAdministration<EventDayDto>()

    return (
        <Box sx={{display: 'flex', flexDirection: 'column'}}>
            {data && (
                <Box>
                    <Typography>{data.properties.name}</Typography>
                    <Box>
                        <RaceTable {...raceAdministrationProps}/>
                        <RaceDialog {...raceAdministrationProps}/>
                    </Box>
                </Box>
            )}
        </Box>
    )
}

export default EventPage

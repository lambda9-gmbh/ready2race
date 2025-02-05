import {getEvent} from '../../api'
import {Box} from '@mui/material'
import {useFeedback, useFetch} from '../../utils/hooks.ts'
import {eventRoute} from '../../routes.tsx'
import {useTranslation} from 'react-i18next'

const EventPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()

    const {data, pending} = useFetch(
        signal => getEvent({signal, path: {eventId: eventId}}),
        {
            onResponse: result => {
                console.log("Event response:")
                if(result.error){
                    feedback.error(t('common.load.error', {entity: t('event.event')}))
                    console.log(result.error)
                } else if(result.data){
                    console.log(result.data)
                }
            }
        },
        [eventId],
    )

    return <Box sx={{display: 'flex', flexDirection: 'column'}}></Box>
}

export default EventPage

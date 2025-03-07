import {Box} from '@mui/material'
import {useEntityAdministration} from '@utils/hooks.ts'
import EventTable from '@components/event/EventTable.tsx'
import EventDialog from '@components/event/EventDialog.tsx'
import {useTranslation} from 'react-i18next'
import {EventDto} from '@api/types.gen.ts'

const EventsPage = () => {
    const {t} = useTranslation()

    const administrationProps = useEntityAdministration<EventDto>(t('event.event'))

    return (
        <Box>
            <EventTable {...administrationProps.table} title={t('event.events')} />
            <EventDialog {...administrationProps.dialog} />
        </Box>
    )
}

export default EventsPage

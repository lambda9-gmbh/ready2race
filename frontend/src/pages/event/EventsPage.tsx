import {EventDto} from '../../api'
import {Box} from '@mui/material'
import {useEntityAdministration} from '../../utils/hooks.ts'
import EventTable from "../../components/event/EventTable.tsx";
import EventDialog from "../../components/event/EventDialog.tsx";
import {useTranslation} from "react-i18next";

const EventsPage = () => {
    const {t} = useTranslation()

    const administrationProps = useEntityAdministration<EventDto>(t('event.event'))

    return (
        <Box>
            <EventTable {...administrationProps.table}/>
            <EventDialog {...administrationProps.dialog}/>
        </Box>
    )
}

export default EventsPage

import {EventDto} from '../../api'
import {Box} from '@mui/material'
import {useEntityAdministration} from '../../utils/hooks.ts'
import EventTable from "../../components/event/EventTable.tsx";
import EventDialog from "../../components/event/EventDialog.tsx";

const EventsPage = () => {

    const administrationProps = useEntityAdministration<EventDto>()

    return (
        <Box>
            <EventTable {...administrationProps}/>
            <EventDialog {...administrationProps}/>
        </Box>
    )
}

export default EventsPage

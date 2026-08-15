import FinalizeRegistrations from '@components/event/competition/excecution/FinalizeRegistrations.tsx'
import EventRegistrationTable from '@components/eventRegistration/EventRegistrationTable.tsx'
import RegistrationMailDialog from '@components/eventRegistration/RegistrationMailDialog.tsx'
import {Box, Button, Stack} from '@mui/material'
import {Email} from '@mui/icons-material'
import {useState} from 'react'
import {useEntityAdministration} from '@utils/hooks.ts'
import {EventDto, EventRegistrationViewDto} from '@api/types.gen.ts'
import {eventRoute} from '@routes'
import {useTranslation} from 'react-i18next'

type Props = {
    registrationsFinalized: boolean
    eventDto: EventDto
}
const EventRegistrations = (props: Props) => {
    const {t} = useTranslation()

    const {eventId} = eventRoute.useParams()

    const [mailDialogOpen, setMailDialogOpen] = useState(false)

    const eventRegistrationProps = useEntityAdministration<EventRegistrationViewDto>(
        t('event.registration.registration'),
        {entityCreate: false, entityUpdate: false},
    )

    return (
        <Stack spacing={4}>
            {!props.eventDto.challengeEvent && (
                <FinalizeRegistrations registrationsFinalized={props.registrationsFinalized} />
            )}
            <Box>
                <Button startIcon={<Email />} onClick={() => setMailDialogOpen(true)}>
                    {t('event.registration.mail.open')}
                </Button>
            </Box>
            <EventRegistrationTable
                {...eventRegistrationProps.table}
                title={t('event.registration.registrations')}
                eventId={eventId}
            />
            <RegistrationMailDialog
                open={mailDialogOpen}
                onClose={() => setMailDialogOpen(false)}
                eventId={eventId}
            />
        </Stack>
    )
}

export default EventRegistrations

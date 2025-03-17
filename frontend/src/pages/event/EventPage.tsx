import {Box, Button, Stack, Typography} from '@mui/material'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import {eventRoute} from '@routes'
import {useTranslation} from 'react-i18next'
import CompetitionTable from '@components/event/competition/CompetitionTable.tsx'
import CompetitionDialog from '@components/event/competition/CompetitionDialog.tsx'
import Throbber from '@components/Throbber.tsx'
import EventDayDialog from '@components/event/eventDay/EventDayDialog.tsx'
import EventDayTable from '@components/event/eventDay/EventDayTable.tsx'
import {getEvent} from '@api/sdk.gen.ts'
import {CompetitionDto, EventDayDto, EventDocumentDto} from '@api/types.gen.ts'
import DocumentTable from '@components/event/document/DocumentTable.tsx'
import DocumentDialog from '@components/event/document/DocumentDialog.tsx'
import {Forward} from '@mui/icons-material'
import {Link} from '@tanstack/react-router'

const EventPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()

    const {data, pending} = useFetch(signal => getEvent({signal, path: {eventId: eventId}}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(t('common.load.error.single', {entity: t('event.event')}))
            }
        },
        deps: [eventId],
    })

    const competitionAdministrationProps = useEntityAdministration<CompetitionDto>(
        t('event.competition.competition'),
    )
    const eventDayAdministrationProps = useEntityAdministration<EventDayDto>(
        t('event.eventDay.eventDay'),
    )
    const documentAdministrationProps = useEntityAdministration<EventDocumentDto>(
        t('event.document.document'),
    )

    return (
        <Box sx={{display: 'flex', flexDirection: 'column'}}>
            {data ? (
                <Stack spacing={4}>
                    <Stack direction={'row'} justifyContent={'space-between'} alignItems={'center'}>
                        <Typography variant="h1">{data.name}</Typography>
                        <Link to={'/event/$eventId/register'} params={{eventId}}>
                            <Button endIcon={<Forward />} variant={'contained'}>
                                {t('event.registerNow')}
                            </Button>
                        </Link>
                    </Stack>
                    <CompetitionTable
                        {...competitionAdministrationProps.table}
                        title={t('event.competition.competitions')}
                    />
                    <CompetitionDialog {...competitionAdministrationProps.dialog} />
                    <EventDayTable
                        {...eventDayAdministrationProps.table}
                        title={t('event.eventDay.eventDays')}
                    />
                    <EventDayDialog {...eventDayAdministrationProps.dialog} />
                    <DocumentTable
                        {...documentAdministrationProps.table}
                        title={t('event.document.documents')}
                    />
                    <DocumentDialog {...documentAdministrationProps.dialog} />
                </Stack>
            ) : (
                pending && <Throbber />
            )}
        </Box>
    )
}

export default EventPage

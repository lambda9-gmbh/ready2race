import {Card, List, ListItem, Typography} from '@mui/material'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getCompetitionPlaces} from '@api/sdk.gen.ts'
import {competitionRoute, eventRoute} from '@routes'
import {useTranslation} from 'react-i18next'
import Throbber from '@components/Throbber.tsx'

const CompetitionPlaces = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()
    const {data: placesData, pending: placesPending} = useFetch(
        signal =>
            getCompetitionPlaces({
                signal,
                path: {eventId: eventId, competitionId: competitionId},
            }),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.competition.places.places'),
                        }),
                    )
                }
            },
            deps: [eventId, competitionId],
        },
    )

    return placesData ? (
        placesData.length > 0 ? (
            <List>
                {placesData?.map(place => (
                    <ListItem>
                        <Card sx={{p: 2}}>
                            <Typography>
                                {place.place}: {place.clubName + ' ' + place.teamName}
                                {place.deregistered
                                    ? ` ${t('event.competition.registration.deregister.deregistered')}` +
                                      (place.deregistrationReason
                                          ? ` (${place.deregistrationReason})`
                                          : '')
                                    : ''}
                            </Typography>
                        </Card>
                    </ListItem>
                ))}
            </List>
        ) : (
            <Typography>{t('event.competition.places.noPlaces')}</Typography>
        )
    ) : (
        placesPending && <Throbber />
    )
}

export default CompetitionPlaces

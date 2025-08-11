import React, {Fragment} from 'react'
import {Divider, List} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {DashboardWidget} from '@components/dashboard/DashboardWidget.tsx'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import UpcomingEventEntry from '@components/dashboard/UpcomingEventEntry.tsx'
import {getPublicEvents} from '@api/sdk.gen.ts'

export function UpcomingEventsWidget(props: {hideRegistration?: boolean}) {
    const feedback = useFeedback()
    const {t} = useTranslation()

    const {data: events} = useFetch(
        signal =>
            getPublicEvents({
                signal,
                query: {
                    limit: 5,
                    sort: JSON.stringify([
                        {field: 'EVENT_FROM', direction: 'ASC'},
                        {field: 'EVENT_TO', direction: 'ASC'},
                        {field: 'NAME', direction: 'ASC'},
                    ]),
                },
            }),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('common.load.error.single', {entity: t('event.events')}))
                }
            },
        },
    )

    return (
        <React.Fragment>
            <DashboardWidget
                size={12}
                header={t('event.upcoming')}
                content={
                    <React.Fragment>
                        <List>
                            {events?.data?.map((event, index) => (
                                <Fragment key={`event-${event.id}`}>
                                    {index !== 0 && <Divider variant={'middle'} />}
                                    <UpcomingEventEntry
                                        event={event}
                                        hideRegistration={props.hideRegistration}
                                    />
                                </Fragment>
                            ))}
                        </List>
                    </React.Fragment>
                }
            />
        </React.Fragment>
    )
}

import {Button, Stack, Typography} from '@mui/material'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getEvents} from '@api/sdk.gen.ts'
import {useTranslation} from 'react-i18next'
import Throbber from '@components/Throbber.tsx'
import {Link} from '@tanstack/react-router'

const SelectResultsEventPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {data, pending} = useFetch(signal => getEvents({signal}), {
        onResponse: response => {
            if (response.error) {
                feedback.error(t('common.load.error.multiple.short', {entity: t('event.event')}))
            }
        },
        deps: [],
    })

    return (
        <Stack spacing={2} sx={{m: 4}}>
            <Typography variant={'h4'} align={'center'}>
                Events
            </Typography>
            {pending ? (
                <Throbber />
            ) : (
                data?.data
                    .sort((a, b) => (a.name > b.name ? 1 : -1))
                    .map(event => (
                        <Link
                            key={event.id}
                            to={'/results/event/$eventId'}
                            params={{eventId: event.id}}
                            style={{width: '100%'}}>
                            <Button variant={'outlined'} fullWidth>
                                {event.name}
                            </Button>
                        </Link>
                    ))
            )}
        </Stack>
    )
}
export default SelectResultsEventPage

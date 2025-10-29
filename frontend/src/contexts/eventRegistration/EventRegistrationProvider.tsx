import {PropsWithChildren} from 'react'
import {
    EventRegistration,
    EventRegistrationContext,
} from '@contexts/eventRegistration/EventRegistrationContext.ts'
import {EventRegistrationInfoDto} from '@api/types.gen.ts'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getRatingCategories} from '@api/sdk.gen.ts'
import {useTranslation} from 'react-i18next'

type Props = {
    info: EventRegistrationInfoDto | null
}

const EventRegistrationProvider = ({info, children}: PropsWithChildren<Props>) => {
    const feedback = useFeedback()
    const {t} = useTranslation()

    const {data: ratingCategoriesData} = useFetch(signal => getRatingCategories({signal}), {
        deps: [],
        onResponse: ({error}) => {
            if (error) {
                feedback.error(
                    t('common.load.error.multiple.short', {
                        entity: t('configuration.ratingCategory.ratingCategories'),
                    }),
                )
            }
        },
    })

    const eventRegistrationValue: EventRegistration = {
        info,
        ratingCategories: ratingCategoriesData?.data ?? [],
    }

    return (
        <EventRegistrationContext.Provider value={eventRegistrationValue}>
            {children}
        </EventRegistrationContext.Provider>
    )
}

export default EventRegistrationProvider

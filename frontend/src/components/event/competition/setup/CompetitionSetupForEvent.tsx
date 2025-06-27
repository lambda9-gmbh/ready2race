import {competitionRoute, eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useState} from 'react'
import {useForm} from 'react-hook-form-mui'
import {getCompetitionSetup, updateCompetitionSetup} from '@api/sdk.gen.ts'
import {
    CompetitionSetupForm,
    mapCompetitionSetupDtoToForm,
    mapFormToCompetitionSetupDto,
} from '@components/event/competition/setup/common.ts'
import CompetitionSetup from '@components/event/competition/setup/CompetitionSetup.tsx'
import {useTranslation} from 'react-i18next'

const CompetitionSetupForEvent = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const [reloadData, setReloadData] = useState(false)

    const formContext = useForm<CompetitionSetupForm>()

    useFetch(
        signal =>
            getCompetitionSetup({signal, path: {eventId: eventId, competitionId: competitionId}}),
        {
            onResponse: ({data}) => {
                if (data) {
                    formContext.reset(mapCompetitionSetupDtoToForm(data))
                } else {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.competition.setup.setup'),
                        }),
                    )
                }
            },
            deps: [eventId, competitionId, reloadData],
        },
    )

    const handleSubmit = async (formData: CompetitionSetupForm) => {
        setSubmitting(true)
        const {error} = await updateCompetitionSetup({
            path: {eventId: eventId, competitionId: competitionId},
            body: mapFormToCompetitionSetupDto(formData),
        })
        setSubmitting(false)

        if (error) {
            feedback.error(t('event.competition.setup.save.error'))
        } else {
            feedback.success(t('event.competition.setup.save.success'))
        }
        setReloadData(!reloadData)
    }

    return (
        <CompetitionSetup
            formContext={formContext}
            handleFormSubmission={true}
            handleSubmit={handleSubmit}
            submitting={submitting}
        />
    )
}

export default CompetitionSetupForEvent

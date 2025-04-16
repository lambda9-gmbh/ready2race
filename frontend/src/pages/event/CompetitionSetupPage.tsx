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

const CompetitionSetupPage = () => {
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const [reloadDataTrigger, setReloadDataTrigger] = useState(false)

    const formContext = useForm<CompetitionSetupForm>()

    useFetch(
        signal =>
            getCompetitionSetup({signal, path: {eventId: eventId, competitionId: competitionId}}),
        {
            onResponse: ({data}) => {
                if (data) {
                    formContext.reset(mapCompetitionSetupDtoToForm(data))
                } else {
                    feedback.error('[todo] error!')
                }
            },
            deps: [eventId, competitionId, reloadDataTrigger],
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
            feedback.error('[todo] Error!')
        } else {
            feedback.success('[todo] Saved!')
            setReloadDataTrigger(!reloadDataTrigger)
        }
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

export default CompetitionSetupPage

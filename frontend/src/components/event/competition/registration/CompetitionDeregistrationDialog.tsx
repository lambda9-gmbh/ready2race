import BaseDialog from '@components/BaseDialog.tsx'
import {Button, DialogActions, DialogContent, DialogTitle} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useFeedback} from '@utils/hooks.ts'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {deregisterCompetitionRegistration} from '@api/sdk.gen.ts'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {useState} from 'react'
import {competitionRoute, eventRoute} from '@routes'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'

type Props = {
    open: boolean
    onClose: () => void
    reloadData: () => void
    competitionRegistration: {
        id: string
        teamName: string
    } | null
}
type Form = {
    reason: string
}

const CompetitionDeregistrationDialog = (props: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const handleClose = () => {
        props.onClose()
    }

    const formContext = useForm<Form>()

    const [submitting, setSubmitting] = useState(false)

    const onSubmit = async (formData: Form) => {
        setSubmitting(true)
        const {error} = await deregisterCompetitionRegistration({
            path: {
                eventId: eventId,
                competitionId: competitionId,
                competitionRegistrationId: props.competitionRegistration!.id,
            },
            body: {
                reason: takeIfNotEmpty(formData.reason),
            },
        })
        setSubmitting(false)

        if (error) {
            feedback.error(t('event.competition.registration.deregister.error'))
        } else {
            feedback.success(t('event.competition.registration.deregister.success'))
            props.onClose()
            formContext.reset()
            props.reloadData()
        }
    }

    return (
        <BaseDialog open={props.open} onClose={handleClose}>
            <DialogTitle>
                {t('event.competition.registration.deregister.deregister')}{' '}
                {props.competitionRegistration?.teamName ?? ''}
            </DialogTitle>
            <FormContainer formContext={formContext} onSuccess={onSubmit}>
                <DialogContent>
                    <FormInputText
                        name={'reason'}
                        label={t('event.competition.registration.deregister.reason')}
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleClose}>{t('common.close')}</Button>
                    <SubmitButton submitting={submitting}>{t('common.save')}</SubmitButton>
                </DialogActions>
            </FormContainer>
        </BaseDialog>
    )
}

export default CompetitionDeregistrationDialog

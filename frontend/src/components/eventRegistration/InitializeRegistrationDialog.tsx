import BaseDialog from '@components/BaseDialog.tsx'
import {Button, DialogActions, DialogContent, DialogTitle} from '@mui/material'
import {Trans, useTranslation} from 'react-i18next'
import {EventRegistrationConfirmDocumentsForm} from '@components/eventRegistration/EventRegistrationConfirmDocumentsForm.tsx'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {addEventRegistration, getRegistrationDocuments} from '@api/sdk.gen.ts'
import {FormContainer} from 'react-hook-form-mui'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {useState} from 'react'

type Props = {
    eventId: string
    open: boolean
    onClose: () => void
    onSuccess: () => void
}

const InitializeRegistrationDialog = ({eventId, open, onClose, onSuccess}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const [submitting, setSubmitting] = useState(false)

    const {data} = useFetch(signal =>
        getRegistrationDocuments({
            signal,
            path: {
                eventId,
            },
        }),
    )

    const handleSubmit = async () => {
        setSubmitting(true)
        const {error} = await addEventRegistration({
            path: {eventId},
            body: {
                participants: [],
                competitionRegistrations: [],
            },
        })
        setSubmitting(false)

        if (error) {
            feedback.error(t('common.error.unexpected'))
        } else {
            onClose()
            onSuccess()
        }
    }

    return (
        <BaseDialog open={open} onClose={onClose}>
            <DialogTitle>
                <Trans i18nKey={'event.registration.initialize.header'} />
            </DialogTitle>
            <FormContainer onSuccess={handleSubmit}>
                <DialogContent>
                    <EventRegistrationConfirmDocumentsForm
                        eventId={eventId}
                        documentTypes={data ?? []}
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={onClose} disabled={submitting}>
                        <Trans i18nKey={'common.cancel'} />
                    </Button>
                    <SubmitButton submitting={submitting}>
                        <Trans i18nKey={'event.registration.initialize.submit'} />
                    </SubmitButton>
                </DialogActions>
            </FormContainer>
        </BaseDialog>
    )
}

export default InitializeRegistrationDialog

import BaseDialog from '@components/BaseDialog.tsx'
import {Button, DialogActions, DialogContent, DialogTitle, Stack, Typography} from '@mui/material'
import {Trans, useTranslation} from 'react-i18next'
import {EventRegistrationConfirmDocumentsForm} from '@components/eventRegistration/EventRegistrationConfirmDocumentsForm.tsx'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {
    addEventRegistration,
    getRegistrationDocuments,
    acceptEventRegistrationDocuments,
} from '@api/sdk.gen.ts'
import {FormContainer} from 'react-hook-form-mui'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {useState} from 'react'

type Props = {
    eventId: string
    open: boolean
    onClose: () => void
    onSuccess: () => void
    registrationInitialized: boolean
}

const InitializeRegistrationDialog = ({eventId, open, onClose, onSuccess, ...props}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const [submitting, setSubmitting] = useState(false)

    const {data: documents} = useFetch(
        signal =>
            getRegistrationDocuments({
                signal,
                path: {
                    eventId,
                },
            }),
        {
            preCondition: () => open,
            deps: [open],
        },
    )

    const handleSubmit = async () => {
        setSubmitting(true)

        if (!props.registrationInitialized) {
            // Scenario 1: Create new registration
            const {error: createError} = await addEventRegistration({
                path: {eventId},
                body: {
                    participants: [],
                    competitionRegistrations: [],
                },
            })

            if (createError) {
                setSubmitting(false)
                feedback.error(t('common.error.unexpected'))
                return
            }
        } else {
            // Scenario 2: Accept event documents
            const {error: acceptError} = await acceptEventRegistrationDocuments({
                path: {eventId},
            })

            if (acceptError) {
                setSubmitting(false)
                feedback.error(t('common.error.unexpected'))
                return
            }
        }

        setSubmitting(false)
        onClose()
        onSuccess()
    }

    return (
        <BaseDialog open={open} onClose={onClose}>
            <DialogTitle>
                <Trans i18nKey={'event.registration.initialize.header'} />
            </DialogTitle>
            <FormContainer onSuccess={handleSubmit}>
                <DialogContent>
                    <Stack spacing={4}>
                        {!props.registrationInitialized ? (
                            <>
                                <Typography>
                                    <Trans i18nKey={'event.registration.initialize.info.1'} />
                                </Typography>
                                <Typography>
                                    <Trans i18nKey={'event.registration.initialize.info.2'} />
                                </Typography>
                            </>
                        ) : (
                            <Typography>
                                <Trans i18nKey={'event.registration.initialize.info.2'} />
                            </Typography>
                        )}
                    </Stack>
                    {documents && (
                        <EventRegistrationConfirmDocumentsForm
                            eventId={eventId}
                            documentTypes={documents}
                        />
                    )}
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

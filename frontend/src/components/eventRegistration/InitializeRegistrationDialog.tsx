import BaseDialog from '@components/BaseDialog.tsx'
import {DialogActions, DialogContent, DialogTitle} from '@mui/material'
import {Trans} from 'react-i18next'
import {EventRegistrationConfirmDocumentsForm} from '@components/eventRegistration/EventRegistrationConfirmDocumentsForm.tsx'
import {useFetch} from '@utils/hooks.ts'
import {getRegistrationDocuments} from '@api/sdk.gen.ts'
import {FormContainer} from 'react-hook-form-mui'

type Props = {
    eventId: string
    open: boolean
    onClose: () => void
}

const InitializeRegistrationDialog = ({eventId, open, onClose}: Props) => {
    const {data} = useFetch(signal =>
        getRegistrationDocuments({
            signal,
            path: {
                eventId,
            },
        }),
    )

    return (
        <BaseDialog open={open} onClose={onClose}>
            <DialogTitle>
                <Trans i18nKey={'event.registration.initialize.header'} />
            </DialogTitle>
            <FormContainer onSuccess={() => null}>
                <DialogContent>
                    <EventRegistrationConfirmDocumentsForm
                        eventId={eventId}
                        documentTypes={data ?? []}
                    />
                </DialogContent>
                <DialogActions></DialogActions>
            </FormContainer>
        </BaseDialog>
    )
}

export default InitializeRegistrationDialog

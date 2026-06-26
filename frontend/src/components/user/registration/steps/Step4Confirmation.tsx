import {Stack} from '@mui/material'
import FormInputCaptcha from '@components/form/input/FormInputCaptcha.tsx'
import {EventRegistrationConfirmDocumentsForm} from '@components/eventRegistration/EventRegistrationConfirmDocumentsForm.tsx'
import {ApiError, CaptchaDto, GetRegistrationDocumentsResponse} from '@api/types.gen.ts'
import {UseFetchReturn} from '@utils/hooks.ts'
import {AutocompleteOption} from '@utils/types.ts'

interface Step4ConfirmationProps {
    watchEvent: AutocompleteOption | null
    registrationDocuments?: GetRegistrationDocumentsResponse
    captcha: UseFetchReturn<CaptchaDto, ApiError>
}

export const Step4Confirmation = ({
    watchEvent,
    registrationDocuments,
    captcha,
}: Step4ConfirmationProps) => {
    return (
        <Stack spacing={3}>
            {watchEvent && registrationDocuments && registrationDocuments.length > 0 && (
                <EventRegistrationConfirmDocumentsForm
                    eventId={watchEvent.id}
                    documentTypes={registrationDocuments}
                />
            )}

            <FormInputCaptcha captchaProps={captcha} />
        </Stack>
    )
}

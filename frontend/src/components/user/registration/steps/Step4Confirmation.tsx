import {Stack} from '@mui/material'
import FormInputCaptcha from '@components/form/input/FormInputCaptcha.tsx'
import {EventRegistrationConfirmDocumentsForm} from '@components/eventRegistration/EventRegistrationConfirmDocumentsForm.tsx'
import {ApiError, CaptchaDto, GetRegistrationDocumentsResponse} from '@api/types.gen.ts'
import {UseFetchReturn} from '@utils/hooks.ts'
import {AutocompleteOption} from '@utils/types.ts'

interface Step4ConfirmationProps {
    watchIsParticipant: boolean
    watchIsChallengeManager: boolean
    watchEvent: AutocompleteOption | null
    registrationDocuments?: GetRegistrationDocumentsResponse
    captcha: UseFetchReturn<CaptchaDto, ApiError>
}

export const Step4Confirmation = ({
    watchIsParticipant,
    watchIsChallengeManager,
    watchEvent,
    registrationDocuments,
    captcha,
}: Step4ConfirmationProps) => {
    return (
        <Stack spacing={3}>
            {watchIsParticipant &&
                watchEvent &&
                registrationDocuments &&
                registrationDocuments.length > 0 && (
                    <EventRegistrationConfirmDocumentsForm
                        eventId={watchEvent.id}
                        documentTypes={registrationDocuments}
                    />
                )}

            {watchIsChallengeManager && <FormInputCaptcha captchaProps={captcha} />}
        </Stack>
    )
}
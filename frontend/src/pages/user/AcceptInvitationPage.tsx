import SimpleFormLayout from "@components/SimpleFormLayout.tsx";
import {useTranslation} from "react-i18next";
import {useState} from "react";
import {NewPassword, PasswordFormPart} from "@components/form/NewPassword.tsx";
import {FormContainer, useForm} from "react-hook-form-mui";
import {Box, Button, Stack, Typography} from "@mui/material";
import {Link} from "@tanstack/react-router";
import {SubmitButton} from "@components/form/SubmitButton.tsx";
import {acceptUserInvitation} from "@api/sdk.gen.ts";
import {invitationTokenRoute} from "@routes";

type Form = PasswordFormPart

const defaultValues: Form = {
    password: '',
    confirmPassword: '',
}

const AcceptInvitationPage = () => {

    const {t} = useTranslation()

    const {invitationToken} = invitationTokenRoute.useParams()

    const [submitting, setSubmitting] = useState(false)
    const [done, setDone] = useState(false)

    const formContext = useForm<Form>({values: defaultValues})

    const handleSubmit = async (formData: Form) => {
        setSubmitting(true)

        const {error} = await acceptUserInvitation({
            body: {
                token: invitationToken,
                password: formData.password
            }
        })

        setSubmitting(false)

        if (error === undefined) {
            setDone(true)
        }

        //todo: @Incomplete error-handling
    }

    return (
        <SimpleFormLayout maxWidth={500}>
            {!done ? (
                <>
                    <Box sx={{mb: 4}}>
                        <Typography variant="h1" textAlign="center" sx={{mb:4}}>
                            {t('user.invitation.accept')}
                        </Typography>
                        <Typography>
                            {t('user.invitation.hint')}
                        </Typography>
                    </Box>
                    <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                        <Stack spacing={4}>
                            <NewPassword formContext={formContext} />
                            <SubmitButton
                                label={t('user.invitation.submitPassword')}
                                submitting={submitting}
                            />
                        </Stack>
                    </FormContainer>
                </>
            ) : (
                <Box sx={{display: 'flex', flexDirection: 'column', alignItems: 'center'}}>
                    <Typography textAlign="center" sx={{mb: 4}}>
                        {t('user.invitation.userCreated.text')}
                    </Typography>
                    <Link to="/login">
                        <Button variant="contained">
                            {t('user.invitation.userCreated.toLogin')}
                        </Button>
                    </Link>
                </Box>
            )}
        </SimpleFormLayout>
    )
}

export default AcceptInvitationPage
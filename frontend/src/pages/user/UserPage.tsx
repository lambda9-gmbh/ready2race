import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getUser} from '@api/sdk.gen.ts'
import {userRoute} from '@routes'
import {useTranslation} from 'react-i18next'
import {Box, Paper, Stack, Typography} from '@mui/material'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {useState} from 'react'
import Throbber from '@components/Throbber.tsx'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {SubmitButton} from "@components/form/SubmitButton.tsx";

type Form = {
    firstname: string
    lastname: string
}

const UserPage = () => {

    // TODO: Should this page be available for the admin? Currently there is no view for him so the page can't load

    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const {userId} = userRoute.useParams()

    const defaultValues: Form = {
        firstname: '',
        lastname: '',
    }

    const formContext = useForm<Form>({values: defaultValues})

    const {data: userData, pending} = useFetch(signal => getUser({signal, path: {userId: userId}}), {
        onResponse: ({data, error}) => {
            if (error) {
                feedback.error(t('user.page.error.load'))
                console.log(error)
            } else {
                formContext.reset({firstname: data.firstname, lastname: data.lastname})
            }
        },
        deps: [userId],
    })

    const handleSubmit = async (formData: Form) => {
        setSubmitting(true)
        // TODO: updateUser()
        setSubmitting(false)
    }

    return (
        <Box sx={{maxWidth: 600}}>
            <Paper sx={{padding: 4}}>
                {(userData && (
                    <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                        <Stack spacing={4}>
                            <FormInputText
                                name={'firstname'}
                                label={t('user.firstname')}
                                required
                            />
                            <FormInputText name={'lastname'} label={t('user.lastname')} required />
                            <Typography>{userData.email}</Typography>
                            {/* TODO: Edit Rights */}
                            <SubmitButton label={t('common.save')} submitting={submitting} disabled={!formContext.formState.isDirty}/>
                        </Stack>
                    </FormContainer>
                )) ||
                    (pending && <Throbber />)}
            </Paper>
        </Box>
    )
}

export default UserPage

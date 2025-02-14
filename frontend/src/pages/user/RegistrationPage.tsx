import {Box, Card, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {FormContainer, TextFieldElement, useForm} from 'react-hook-form-mui'
import {registerUser} from '../../api'
import {useState} from 'react'
import {useFeedback} from '../../utils/hooks.ts'
import {FormInputText} from '../../components/form/input/FormInputText.tsx'
import {SubmitButton} from '../../components/form/SubmitButton.tsx'

type Form = {
    email: string
    password: string
    confirmPassword: string
    firstname: string
    lastname: string
}

const RegistrationPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const formContext = useForm<Form>()

    const handleSubmit = async (formData: Form) => {
        setSubmitting(true)
        const result = await registerUser({
            body: {
                email: formData.email,
                password: formData.password,
                firstname: formData.firstname,
                lastname: formData.lastname,
                language: 'de', // todo, read from i18n
                callbackUrl: location.origin + location.pathname,
            },
        })
        setSubmitting(false)

        if (result) {
            if (result.error) {
                if (result.error.status.value === 409) {
                    formContext.setError('email', {
                        type: 'validate',
                        message:
                            t('user.registration.email.inUse.statement') +
                            ' ' +
                            t('user.registration.email.inUse.callToAction'),
                    })
                    feedback.error(t('user.registration.email.inUse.statement'))
                } else {
                    feedback.error(t('user.registration.error'))
                }
                console.log(result.error)
            } else {
                // todo: Show Text that mail was sent
            }
        }
    }

    return (
        <Box sx={{display: 'flex'}}>
            <Card
                sx={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignSelf: 'center',
                    margin: 'auto',
                    maxWidth: 450,
                    padding: 4,
                }}>
                <Box sx={{mb: 4}}>
                    <Typography variant="h4">{t('user.registration.registration')}</Typography>
                </Box>
                <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                    <Stack spacing={4}>
                        <FormInputText name={'email'} label={t('user.email')} required />
                        <Stack direction="row" spacing={2}>
                            <TextFieldElement
                                name={'password'}
                                label={t('user.password')}
                                required
                                type="password"
                                rules={{
                                    required: t('common.form.required'),
                                    minLength: {
                                        value: 10,
                                        message: t('user.registration.password.tooShort', {
                                            min: 10,
                                        }),
                                    },
                                    validate: (val, vals) => {
                                        if (val !== vals['confirmPassword']) {
                                            formContext.setError('confirmPassword', {
                                                type: 'validate',
                                                message: t(
                                                    'user.registration.password.notMatching',
                                                ),
                                            })
                                            return t('user.registration.password.notMatching')
                                        } else {
                                            formContext.clearErrors('confirmPassword')
                                        }
                                    },
                                }}
                            />
                            <TextFieldElement
                                name="confirmPassword"
                                label={t('user.registration.password.confirm')}
                                required
                                type="password"
                                rules={{
                                    required: t('common.form.required'),
                                    validate: (val, vals) => {
                                        if (val !== vals['password']) {
                                            formContext.setError('password', {
                                                type: 'validate',
                                                message: t(
                                                    'user.registration.password.notMatching',
                                                ),
                                            })
                                            return t('user.registration.password.notMatching')
                                        } else {
                                            formContext.clearErrors('password')
                                        }
                                    },
                                }}
                                sx={{maxWidth: 250}}
                            />
                        </Stack>
                        <Stack spacing={2} direction="row">
                            <FormInputText
                                name={'firstname'}
                                label={t('user.firstname')}
                                required
                            />
                            <FormInputText name={'lastname'} label={t('user.lastname')} required />
                        </Stack>
                        <SubmitButton
                            label={t('user.registration.register')}
                            submitting={submitting}
                        />
                    </Stack>
                </FormContainer>
            </Card>
        </Box>
    )
}

export default RegistrationPage

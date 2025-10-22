import {useTranslation} from 'react-i18next'
import {Stack, Typography} from '@mui/material'
import {FormContainer, useForm} from "react-hook-form-mui";
import {FormInputText} from "@components/form/input/FormInputText.tsx";
import FormInputNumber from "@components/form/input/FormInputNumber.tsx";
import FormInputPassword from "@components/form/input/FormInputPassword.tsx";
import {FormInputToggleButtonGroup} from "@components/form/input/FormInputToggleButtonGroup.tsx";
import {SmtpConfigOverrideDto, smtpStrategy} from "@api/types.gen.ts";
import FormInputEmail from "@components/form/input/FormInputEmail.tsx";
import {SubmitButton} from "@components/form/SubmitButton.tsx";
import {useState} from "react";
import {setSmtpOverride, getSmtpConfig, deleteSmtpOverride} from "@api/sdk.gen.ts";
import {useFeedback, useFetch} from "@utils/hooks.ts";
import {useConfirmation} from "@contexts/confirmation/ConfirmationContext.ts";
import LoadingButton from "@components/form/LoadingButton.tsx";
import {takeIfNotEmpty} from "@utils/ApiUtils.ts";


type Form = SmtpConfigOverrideDto

const AdministrationPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const formContext = useForm<Form>()
    const smtpStrategyArray: smtpStrategy[] = ['SMTP', 'SMTP_TLS', 'SMTPS']
    const smtpStrategyOptions = smtpStrategyArray.map(strategy => ({
        id: strategy,
        label: strategy
    }));
    const [submitting, setSubmitting] = useState(true)
    const [resetting, setResetting] = useState(true)
    const {confirmAction} = useConfirmation()

    const currentConfig = useFetch(signal => getSmtpConfig({signal}), {
        onResponse: response => {
            if (response.data) {
                setSubmitting(false)
                setResetting(false)
                formContext.reset({
                    host: response.data.host,
                    port: response.data.port,
                    username: response.data.username,
                    smtpStrategy: response.data.smtpStrategy,
                    fromAddress: response.data.fromAddress,
                    fromName: response.data.fromName ?? "",
                    localhost: response.data.localhost ?? "",
                    replyTo: response.data.replyTo ?? "",
                    }
                )
            }
            if (response.error) {
                feedback.error(t('administration.smtp.errors.loading'))
            }
        },
        deps: [],
    })

    const handleSubmit = async (formData: Form) => {
        setSubmitting(true)
        confirmAction(
            async () => {

                const {data, error, response} = await setSmtpOverride({
                    body: {
                        host: formData.host,
                        port: formData.port,
                        username: formData.username,
                        password: formData.password,
                        smtpStrategy: formData.smtpStrategy,
                        fromAddress: formData.fromAddress,
                        fromName: takeIfNotEmpty(formData.fromName),
                        localhost: takeIfNotEmpty(formData.localhost),
                        replyTo: takeIfNotEmpty(formData.replyTo),
                    },
                })
                    setSubmitting(false)
                if (response.ok && (data !== undefined) ) {
                    currentConfig.reload()
                } else if (error) {
                    if (error.status.value === 500) {
                        feedback.error(t('common.error.unexpected'))
                    } else {
                        feedback.error(t('administration.smtp.errors.submit'))
                    }
                }
            },
            {
                title: t('common.confirmation.title'),
                content: t('administration.smtp.confirmation.content'),
                okText: t('administration.smtp.submit'),
                cancelText: t('common.cancel'),
                cancelAction: () => setSubmitting(false)
            }
        )
    }

    const handleReset = async () => {
        setResetting(true)
        confirmAction(
            async () => {

                const {error, response} = await deleteSmtpOverride()
                setResetting(false)

                if (response.ok) {
                    currentConfig.reload()
                } else if (error) {
                    if (error.status.value === 500) {
                        feedback.error(t('common.error.unexpected'))
                    } else {
                        feedback.error(t('administration.smtp.errors.submit'))
                    }
                }
            },
            {
                title: t('common.confirmation.title'),
                content: t('administration.smtp.confirmation.content'),
                okText: t('administration.smtp.reset'),
                cancelText: t('common.cancel'),
                cancelAction: () => setResetting(false)
            }
        )
    }



    return ( currentConfig.data && (
        <Stack spacing={4}>
            <Typography variant={'h1'}>{t('administration.smtp.title')}</Typography>
            <FormContainer formContext={formContext} onSuccess={handleSubmit}>
            <Stack spacing={4} maxWidth="70%">
                <FormInputText name={'host'} label={t('administration.smtp.host')} required />
                <FormInputNumber name={'port'} label={t('administration.smtp.port')} required min={1} max={65535} />
                <FormInputText name={'username'} label={t('administration.smtp.username')} required />
                <FormInputPassword name={'password'} label={t('administration.smtp.password')} required />
                <FormInputToggleButtonGroup name={'smtpStrategy'} label={t('administration.smtp.smtpStrategy')} options={smtpStrategyOptions} required exclusive enforceAtLeastOneSelected />
                <FormInputEmail name={'fromAddress'} label={t('administration.smtp.fromAddress')} required />
                <FormInputText name={'fromName'} label={t('administration.smtp.fromName')} />
                <FormInputText name={'localhost'} label={t('administration.smtp.localhost')} />
                <FormInputEmail name={'replyTo'} label={t('administration.smtp.replyTo')} />
                <SubmitButton submitting={submitting}>{t('administration.smtp.submit')}</SubmitButton>
                <LoadingButton variant={"outlined"} pending={resetting} onClick={handleReset}>{t('administration.smtp.reset')}</LoadingButton>
            </Stack>
            </FormContainer>
        </Stack>
    ))
}

export default AdministrationPage

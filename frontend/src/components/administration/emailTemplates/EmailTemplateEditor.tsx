import {Alert, Box, DialogActions, DialogContent, DialogTitle, Stack} from '@mui/material'
import {PropsWithChildren, useEffect, useState} from 'react'
import BaseDialog from '@components/BaseDialog.tsx'
import {EmailLanguage, EmailTemplateDto} from '@api/types.gen.ts'
import {EmailTemplatePreview} from '@components/administration/emailTemplates/EmailTemplatePreview.tsx'
import {useTranslation} from 'react-i18next'
import {EmailTemplateTemplatePlaceholderTable} from '@components/administration/emailTemplates/EmailTemplatePlaceholderTable.tsx'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {setEmailTemplate} from '@api/sdk.gen.ts'
import {useFeedback} from '@utils/hooks.ts'

type Props = {
    open: boolean
    onClose: () => void
    template: EmailTemplateDto
    lng: string
}
type Form = EmailTemplateDto

export function EmailTemplateEditor({open, onClose, template, lng}: PropsWithChildren<Props>) {
    const [submitting, setSubmitting] = useState(true)

    const {t} = useTranslation()
    const feedback = useFeedback()
    const formContext = useForm<Form>({
        defaultValues: template,
    })
    useEffect(() => {
        setSubmitting(false)
    }, [template])

    const handleSubmit = async (formData: Form) => {
        setSubmitting(true)
        const {error, response} = await setEmailTemplate({
            body: {
                subject: formData.subject,
                body: formData.body,
                bodyIsHtml: false,
            },
            query: {
                key: template.key,
                language: lng as EmailLanguage,
            },
        })
        setSubmitting(false)
        if (response.ok) {
            feedback.success(
                t('administration.emailTemplates.response.changeSuccessful', {
                    key: t(`administration.emailTemplates.templateKeys.${template.key}`),
                }),
            )
            onClose()
        } else if (error) {
            if (error.status.value === 500) {
                feedback.error(t('common.error.unexpected'))
            } else {
                feedback.success(
                    t('administration.emailTemplates.response.changeFailed', {
                        key: t(`administration.emailTemplates.templateKeys.${template.key}`),
                    }),
                )
            }
        }
    }

    return (
        <BaseDialog open={open} onClose={onClose} maxWidth={'lg'}>
            <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                <DialogTitle>
                    {t(`administration.emailTemplates.templateKeys.${template.key}`)}
                </DialogTitle>
                <DialogContent>
                    <Alert
                        severity={'info'}
                        sx={{
                            mb: 1,
                            '& .MuiAlert-message': {
                                width: '100%',
                            },
                        }}>
                        <Box
                            sx={{
                                mb: 1,
                                display: 'flex',
                                flexDirection: 'row',
                                flexWrap: 'wrap',
                                alignItems: 'space-between',
                                gap: 2,
                                width: '100%',
                            }}>
                            <Box
                                sx={{
                                    mb: 1,
                                    flex: '1 40%',
                                    display: 'flex',
                                    flexDirection: 'column',
                                }}>
                                <EmailTemplateTemplatePlaceholderTable
                                    title={t('administration.emailTemplates.requiredPlaceholders')}
                                    placeholders={template.requiredPlaceholders}
                                />
                            </Box>
                            <Box
                                sx={{
                                    flex: '1 40%',
                                    display: 'flex',
                                    flexDirection: 'column',
                                }}>
                                <EmailTemplateTemplatePlaceholderTable
                                    title={t('administration.emailTemplates.optionalPlaceholders')}
                                    placeholders={template.optionalPlaceholders}
                                />
                            </Box>
                        </Box>
                    </Alert>

                    <Stack direction={'row'} spacing={2}>
                        <Stack width={'50%'} direction={'column'} spacing={1}>
                            <FormInputText
                                name={'subject'}
                                required
                                sx={{
                                    '& textarea': {
                                        resize: 'none',
                                        fontFamily: 'monospace',
                                    },
                                }}
                                onKeyDown={e => {
                                    if (e.key === 'Enter') {
                                        e.preventDefault()
                                    }
                                }}
                            />
                            <FormInputText
                                name={'body'}
                                required
                                multiline
                                minRows={5}
                                sx={{
                                    '& textarea': {
                                        resize: 'none',
                                        fontFamily: 'monospace',
                                    },
                                }}
                            />
                        </Stack>
                        <Box
                            sx={{
                                display: 'flex',
                                width: '50%',
                            }}>
                            <EmailTemplatePreview
                                body={formContext.watch('body')}
                                subject={formContext.watch('subject')}
                                placeholders={
                                    template.requiredPlaceholders?.concat(
                                        template.optionalPlaceholders,
                                    ) ?? []
                                }
                            />
                        </Box>
                    </Stack>
                </DialogContent>
                <DialogActions sx={{pb: 2, pr: 3}}>
                    <SubmitButton submitting={submitting}>
                        {t('administration.smtp.submit')}
                    </SubmitButton>
                </DialogActions>
            </FormContainer>
        </BaseDialog>
    )
}

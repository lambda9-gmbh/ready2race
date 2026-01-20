import {
    Accordion,
    AccordionActions,
    AccordionDetails,
    AccordionSummary,
    Box,
    Button,
    MenuItem,
    Select,
    SelectChangeEvent,
    Typography,
} from '@mui/material'
import {useEffect, useState} from 'react'
import {useUser} from '@contexts/user/UserContext.ts'
import {EmailLanguage, EmailTemplateDto} from '@api/types.gen.ts'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {deleteEmailTemplate, getEmailTemplates} from '@api/sdk.gen.ts'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import BorderColorOutlinedIcon from '@mui/icons-material/BorderColorOutlined'
import {EmailTemplatePreview} from '@components/administration/emailTemplates/EmailTemplatePreview.tsx'
import {useTranslation} from 'react-i18next'
import {EmailTemplateEditor} from '@components/administration/emailTemplates/EmailTemplateEditor.tsx'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import RestoreOutlinedIcon from '@mui/icons-material/RestoreOutlined'
import LoadingButton from '@components/form/LoadingButton.tsx'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'

export function EmailTemplates() {
    const {t} = useTranslation()
    const {confirmAction} = useConfirmation()
    const lngsRec: Record<EmailLanguage, null> = {
        DE: null,
        EN: null,
        DA: null,
    }
    const lngs = Object.entries(lngsRec).map(([key]) => key)
    const userLng = useUser().language
    const feedback = useFeedback()
    const [lng, setLng] = useState(userLng.toUpperCase())
    const [selectedTemplate, setSelectedTemplate] = useState<EmailTemplateDto | null>(null)
    const [resetting, setResetting] = useState(true)
    const handleLngChange = (event: SelectChangeEvent) => {
        setLng(event.target.value)
    }

    const {data: templates, reload: ReloadTemplates} = useFetch(
        signal => getEmailTemplates({signal, query: {language: lng as EmailLanguage}}),
        {deps: [lng]},
    )

    useEffect(() => {
        setResetting(false)
    }, [templates])

    const handleEditorClose = () => {
        setSelectedTemplate(null)
        ReloadTemplates()
    }

    const handleReset = async (template: EmailTemplateDto) => {
        setResetting(true)
        confirmAction(
            async () => {
                const {error, response} = await deleteEmailTemplate({
                    query: {
                        key: template.key,
                        language: lng as EmailLanguage,
                    },
                })
                setResetting(false)
                if (response.status === 204) {
                    feedback.success(
                        t('administration.emailTemplates.response.resetSuccessful', {
                            key: t(`administration.emailTemplates.templateKeys.${template.key}`),
                        }),
                    )
                } else if (error) {
                    if (error.status.value === 500) {
                        feedback.error(t('common.error.unexpected'))
                    } else {
                        feedback.success(
                            t('administration.emailTemplates.response.changeFailed', {
                                key: t(
                                    `administration.emailTemplates.templateKeys.${template.key}`,
                                ),
                            }),
                        )
                    }
                }
                ReloadTemplates()
                setResetting(false)
            },
            {
                title: t('common.confirmation.title'),
                content: t('administration.emailTemplates.resetTemplateConfirmationText'),
                okText: t('administration.emailTemplates.resetTemplate'),
                cancelText: t('common.cancel'),
                cancelAction: () => setResetting(false),
            },
        )
    }

    return (
        <>
            {selectedTemplate && (
                <EmailTemplateEditor
                    template={selectedTemplate}
                    lng={lng}
                    open={true}
                    onClose={() => handleEditorClose()}
                />
            )}
            <Box sx={{display: 'flex', justifyContent: 'end'}}>
                <FormInputLabel label={t('common.language.menu')} required>
                    <Box sx={{display: 'flex', justifyContent: 'end'}}>
                        <Select value={lng} onChange={handleLngChange}>
                            {lngs.map(language => (
                                <MenuItem value={language} key={language}>
                                    {language}
                                </MenuItem>
                            ))}
                        </Select>
                    </Box>
                </FormInputLabel>
            </Box>
            <Box
                sx={{
                    mt: 2,
                }}>
                {templates &&
                    templates.map(template => {
                        const combinedPlaceholders = template?.requiredPlaceholders.concat(
                            template?.optionalPlaceholders,
                        )
                        return (
                            <Accordion
                                sx={{
                                    height: 'fit-content',
                                }}
                                key={template.key}>
                                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                                    <Typography
                                        gutterBottom
                                        sx={{fontSize: 16, fontWeight: 'bold'}}>
                                        {t(
                                            `administration.emailTemplates.templateKeys.${template.key}`,
                                        )}
                                    </Typography>
                                </AccordionSummary>
                                <AccordionDetails>
                                    <EmailTemplatePreview
                                        body={template.body}
                                        subject={template.subject}
                                        placeholders={combinedPlaceholders}
                                    />
                                </AccordionDetails>
                                <AccordionActions>
                                    <LoadingButton
                                        variant={'text'}
                                        pending={resetting}
                                        onClick={() => handleReset(template)}
                                        startIcon={<RestoreOutlinedIcon />}>
                                        {t('administration.emailTemplates.resetTemplate')}
                                    </LoadingButton>
                                    <Button
                                        size="small"
                                        startIcon={<BorderColorOutlinedIcon />}
                                        onClick={() => setSelectedTemplate(template)}>
                                        {t('common.edit')}
                                    </Button>
                                </AccordionActions>
                            </Accordion>
                        )
                    })}
            </Box>
        </>
    )
}

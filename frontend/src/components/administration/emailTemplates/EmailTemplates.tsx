import {
    Box,
    Button,
    Card,
    CardActions,
    CardContent,
    MenuItem,
    Select,
    SelectChangeEvent,
    Typography,
} from '@mui/material'
import {useState} from 'react'
import {useUser} from '@contexts/user/UserContext.ts'
import {EmailLanguage, EmailTemplateDto} from '@api/types.gen.ts'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {getEmailTemplates} from '@api/sdk.gen.ts'
import {useFetch} from '@utils/hooks.ts'
import BorderColorOutlinedIcon from '@mui/icons-material/BorderColorOutlined'
import {EmailTemplatePreview} from '@components/administration/emailTemplates/EmailTemplatePreview.tsx'
import {useTranslation} from 'react-i18next'
import {EmailTemplateEditor} from '@components/administration/emailTemplates/EmailTemplateEditor.tsx'

export function EmailTemplates() {
    const {t} = useTranslation()
    const lngsRec: Record<EmailLanguage, null> = {
        DE: null,
        EN: null,
    }
    const lngs = Object.entries(lngsRec).map(([key]) => key)
    const userLng = useUser().language
    const [lng, setLng] = useState(userLng.toUpperCase())
    const [selectedTemplate, setSelectedTemplate] = useState<EmailTemplateDto | null>(null)
    const handleLngChange = (event: SelectChangeEvent) => {
        setLng(event.target.value)
    }
    const {data: templates, reload: ReloadTemplates} = useFetch(
        signal => getEmailTemplates({signal, query: {language: lng as EmailLanguage}}),
        {deps: [lng]},
    )
    const handleEditorClose = () => {
        setSelectedTemplate(null)
        ReloadTemplates()
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
                    display: 'flex',
                    flexDirection: 'row',
                    flexWrap: 'wrap',
                    gap: 2,
                    mt: 2,
                }}>
                {templates &&
                    templates.map(template => {
                        const combinedPlaceholders = template?.requiredPlaceholders.concat(
                            template?.optionalPlaceholders,
                        )
                        return (
                            <Card
                                sx={{
                                    width: 'auto',
                                    flex: '1 45%',
                                    minWidth: '400px',
                                    height: 'fit-content',
                                }}
                                key={template.key}>
                                <CardContent>
                                    <Typography
                                        gutterBottom
                                        sx={{fontSize: 16, fontWeight: 'bold'}}>
                                        {t(
                                            `administration.emailTemplates.templateKeys.${template.key}`,
                                        )}
                                    </Typography>
                                    <EmailTemplatePreview
                                        body={template.body}
                                        subject={template.subject}
                                        placeholders={combinedPlaceholders}
                                    />
                                </CardContent>
                                <CardActions>
                                    <Button
                                        size="small"
                                        startIcon={<BorderColorOutlinedIcon />}
                                        onClick={() => setSelectedTemplate(template)}>
                                        {t('common.edit')}
                                    </Button>
                                </CardActions>
                            </Card>
                        )
                    })}
            </Box>
        </>
    )
}

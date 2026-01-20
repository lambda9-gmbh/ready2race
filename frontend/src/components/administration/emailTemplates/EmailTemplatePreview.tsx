import {Box, Divider, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {EmailTemplaplatePlaceholder} from '@api/types.gen.ts'

type Props = {
    body: string
    subject: string
    placeholders: EmailTemplaplatePlaceholder[]
}

export function EmailTemplatePreview({body, subject, placeholders}: Props) {
    const {t} = useTranslation()
    const regex = new RegExp(`##(${placeholders.join('|')})##`, 'g')

    const processedSubject = subject.replace(
        regex,
        (match, key: EmailTemplaplatePlaceholder) =>
            t(`administration.emailTemplates.placeholders.${key}`) ?? match,
    )
    const processedBody = body.replace(
        regex,
        (match, key: EmailTemplaplatePlaceholder) =>
            t(`administration.emailTemplates.placeholders.${key}`) ?? match,
    )

    return (
        <Box
            sx={{
                width: '100%',
                border: '1px solid',
                borderColor: 'divider',
                p: 1,
                borderRadius: 1,
                boxShadow: 'inset 0 0 5px rgba(0,0,0,0.05)',
            }}>
            <Typography
                sx={{
                    mb: 1.5,
                    whiteSpace: 'pre-line',
                    fontFamily: 'monospace',
                }}>
                {processedSubject}
            </Typography>
            <Divider sx={{mb: 1}} />
            <Typography sx={{whiteSpace: 'pre-wrap', fontFamily: 'monospace'}} variant="body2">
                {processedBody}
            </Typography>
        </Box>
    )
}

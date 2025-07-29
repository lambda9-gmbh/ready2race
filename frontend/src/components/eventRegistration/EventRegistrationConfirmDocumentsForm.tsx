import {Box, Button, Divider, Link, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {EventRegistrationDocumentTypeDto} from '@api/types.gen.ts'
import {useMemo, useRef} from 'react'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'
import {eventRegisterIndexRoute} from '@routes'
import {useFeedback} from '@utils/hooks.ts'
import {downloadDocument} from '@api/sdk.gen.ts'
import {FileDownload} from '@mui/icons-material'

export const EventRegistrationConfirmDocumentsForm = (props: {
    documentTypes: EventRegistrationDocumentTypeDto[]
}) => {
    const {t} = useTranslation()
    const {eventId} = eventRegisterIndexRoute.useParams()
    const feedback = useFeedback()

    const requiredDocs = useMemo(
        () => props.documentTypes.filter(d => d.confirmationRequired),
        [props.documentTypes],
    )

    const additionalDocs = useMemo(
        () => props.documentTypes.filter(d => !d.confirmationRequired),
        [props.documentTypes],
    )

    const downloadRef = useRef<HTMLAnchorElement>(null)

    const download = async (documentId: string, name: string) => {
        const {data, error} = await downloadDocument({
            path: {
                eventId,
                eventDocumentId: documentId,
            },
        })
        const anchor = downloadRef.current

        if (error) {
            feedback.error(t('event.document.download.error'))
        } else if (data !== undefined && anchor) {
            anchor.href = URL.createObjectURL(data)
            anchor.download = name
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    return (
        <Box pt={2} pb={2}>
            <Link ref={downloadRef} display={'none'}></Link>
            {requiredDocs.length > 0 && (
                <Stack spacing={1}>
                    <Typography variant={'h6'}>
                        {t('event.registration.acceptDocuments')}
                    </Typography>
                    {requiredDocs.map(type => (
                        <Stack key={type.id}>
                            <FormInputCheckbox
                                name={type.id}
                                label={type.name}
                                required={true}
                                horizontal={true}
                            />
                            {type.description && <Typography>{type.description}</Typography>}
                            {type.files.map(f => (
                                <Stack direction={'row'} alignItems={'center'}>
                                    <Button
                                        startIcon={<FileDownload />}
                                        onClick={() => download(f.id, f.name)}
                                        size={'small'}
                                        variant={'text'}>
                                        {f.name}
                                    </Button>
                                </Stack>
                            ))}
                        </Stack>
                    ))}
                </Stack>
            )}
            {additionalDocs.length > 0 && requiredDocs.length > 0 && (
                <Divider sx={{mt: 2, mb: 2}} />
            )}
            {additionalDocs.length > 0 && (
                <Stack spacing={1}>
                    <Typography variant={'h6'}>{t('event.registration.infoDocuments')}</Typography>
                    {additionalDocs.map(type => (
                        <Stack key={type.id}>
                            <Typography>{type.name}</Typography>
                            {type.description && <Typography>{type.description}</Typography>}
                            {type.files.map(f => (
                                <Box key={f.id}>
                                    <Button
                                        startIcon={<FileDownload />}
                                        onClick={() => download(f.id, f.name)}
                                        size={'small'}
                                        variant={'text'}>
                                        {f.name}
                                    </Button>
                                </Box>
                            ))}
                        </Stack>
                    ))}
                </Stack>
            )}
        </Box>
    )
}

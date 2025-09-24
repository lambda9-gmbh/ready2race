import {memo} from 'react'
import {
    Alert,
    AlertTitle,
    Box,
    Card,
    CardContent,
    LinearProgress,
    Stack,
    Typography,
} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {format} from 'date-fns'
import Throbber from '@components/Throbber.tsx'
import {WebDAVExportStatusDto} from '@api/types.gen.ts'
import {Info} from '@mui/icons-material'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'

interface ExportStatusCardProps {
    exportStatus: WebDAVExportStatusDto
}

const ExportStatusCard = memo(({exportStatus}: ExportStatusCardProps) => {
    const {t} = useTranslation()

    const isComplete =
        exportStatus.filesExported +
            exportStatus.filesWithError +
            exportStatus.dataExported +
            exportStatus.dataWithError ===
        exportStatus.totalFilesToExport + exportStatus.totalDataToExport
    const hasErrors = exportStatus.filesWithError > 0 || exportStatus.dataWithError > 0

    const totalFilesExported = exportStatus.filesExported + exportStatus.dataExported
    const totalFilesToExport = exportStatus.totalFilesToExport + exportStatus.totalDataToExport

    return (
        <Card>
            <CardContent>
                {isComplete ? (
                    hasErrors ? (
                        <Alert severity={'error'}>
                            <AlertTitle>{t('webDAV.export.status.error.title')}</AlertTitle>
                            <Stack direction={'row'} spacing={1}>
                                {t('webDAV.export.status.error.body', {
                                    exported: totalFilesExported,
                                    total: totalFilesToExport,
                                })}
                                <HtmlTooltip
                                    placement={'bottom'}
                                    title={
                                        <Stack>
                                            {exportStatus.filesWithError > 0 && (
                                                <Typography>
                                                    {t('webDAV.export.status.error.info.files', {
                                                        errors: exportStatus.filesWithError,
                                                    })}
                                                </Typography>
                                            )}
                                            {exportStatus.dataWithError > 0 && (
                                                <Typography>
                                                    {t('webDAV.export.status.error.info.data', {
                                                        errors: exportStatus.dataWithError,
                                                    })}
                                                </Typography>
                                            )}
                                        </Stack>
                                    }>
                                    <Info color={'info'} fontSize={'small'} />
                                </HtmlTooltip>
                            </Stack>
                        </Alert>
                    ) : (
                        <Alert severity={'success'}>
                            <AlertTitle>{t('webDAV.export.status.success.title')}</AlertTitle>
                            {t('webDAV.export.status.success.body', {
                                count: totalFilesExported,
                            })}
                        </Alert>
                    )
                ) : (
                    <Box sx={{width: 1}}>
                        <LinearProgress
                            variant="determinate"
                            value={(totalFilesExported / totalFilesToExport) * 100}
                        />
                        <Alert severity={'info'} icon={<Throbber />}>
                            <AlertTitle>{t('webDAV.export.status.pending.title')}</AlertTitle>
                            {t('webDAV.export.status.pending.body', {
                                exported: totalFilesExported,
                                total: totalFilesToExport,
                            })}
                        </Alert>
                    </Box>
                )}
                <Box
                    sx={{
                        display: 'flex',
                        flexWrap: 'wrap',
                        gap: 2,
                        mt: 2,
                        mx: 2,
                    }}>
                    <Typography variant={'h3'}>{exportStatus.exportFolderName}</Typography>
                    <Box sx={{flex: 1, textAlign: 'right'}}>
                        <Typography variant={'subtitle2'}>
                            {format(
                                new Date(exportStatus.exportInitializedAt),
                                t('format.datetime'),
                            )}
                        </Typography>
                        {exportStatus.exportInitializedBy && (
                            <Typography variant={'subtitle2'}>
                                {exportStatus.exportInitializedBy.firstname}{' '}
                                {exportStatus.exportInitializedBy.lastname}
                            </Typography>
                        )}
                    </Box>
                </Box>
            </CardContent>
        </Card>
    )
})

ExportStatusCard.displayName = 'ExportStatusCard'

export default ExportStatusCard

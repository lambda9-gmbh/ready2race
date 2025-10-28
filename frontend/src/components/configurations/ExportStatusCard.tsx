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
import {WebDAVExportStatusDto, WebDAVImportStatusDto} from '@api/types.gen.ts'
import {Info} from '@mui/icons-material'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'

type ExportStatusCardProps =
    | {
          isExport: true
          status: WebDAVExportStatusDto
      }
    | {
          isExport: false
          status: WebDAVImportStatusDto
      }

const ExportStatusCard = memo(({isExport, status}: ExportStatusCardProps) => {
    const {t} = useTranslation()
    const keyPrefix = isExport ? 'export' : 'import'

    const isComplete = isExport
        ? status.filesExported +
              status.filesWithError +
              status.dataExported +
              status.dataWithError ===
          status.totalFilesToExport + status.totalDataToExport
        : status.dataImported + status.dataWithError === status.totalDataToImport

    const hasErrors = isExport
        ? status.filesWithError > 0 || status.dataWithError > 0
        : status.dataWithError > 0

    const totalFilesExported = isExport
        ? status.filesExported + status.dataExported
        : status.dataImported
    const totalFilesToExport = isExport
        ? status.totalFilesToExport + status.totalDataToExport
        : status.totalDataToImport

    const folderName = isExport ? status.exportFolderName : status.importFolderName
    const initializedAt = format(
        new Date(isExport ? status.exportInitializedAt : status.importInitializedAt),
        t('format.datetime'),
    )
    const initializedBy = isExport ? status.exportInitializedBy : status.importInitializedBy

    return (
        <Card>
            <CardContent>
                {isComplete ? (
                    hasErrors ? (
                        <Alert severity={'error'}>
                            <AlertTitle>{t(`webDAV.${keyPrefix}.status.error.title`)}</AlertTitle>
                            <Stack direction={'row'} spacing={1}>
                                {t(`webDAV.${keyPrefix}.status.error.body`, {
                                    files: totalFilesExported,
                                    total: totalFilesToExport,
                                })}
                                {isExport && (
                                    <HtmlTooltip
                                        placement={'bottom'}
                                        title={
                                            <Stack>
                                                {status.filesWithError > 0 && (
                                                    <Typography>
                                                        {t(
                                                            `webDAV.export.status.error.info.files`,
                                                            {
                                                                errors: status.filesWithError,
                                                            },
                                                        )}
                                                    </Typography>
                                                )}
                                                {status.dataWithError > 0 && (
                                                    <Typography>
                                                        {t(`webDAV.export.status.error.info.data`, {
                                                            errors: status.dataWithError,
                                                        })}
                                                    </Typography>
                                                )}
                                            </Stack>
                                        }>
                                        <Info color={'info'} fontSize={'small'} />
                                    </HtmlTooltip>
                                )}
                            </Stack>
                        </Alert>
                    ) : (
                        <Alert severity={'success'}>
                            <AlertTitle>{t(`webDAV.${keyPrefix}.status.success.title`)}</AlertTitle>
                            {t(`webDAV.${keyPrefix}.status.success.body`, {
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
                            <AlertTitle>{t(`webDAV.${keyPrefix}.status.pending.title`)}</AlertTitle>
                            {t(`webDAV.${keyPrefix}.status.pending.body`, {
                                files: totalFilesExported,
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
                    <Typography variant={'h3'}>{folderName}</Typography>
                    <Box sx={{flex: 1, textAlign: 'right'}}>
                        <Typography variant={'subtitle2'}>{initializedAt}</Typography>
                        {initializedBy && (
                            <Typography variant={'subtitle2'}>
                                {initializedBy.firstname} {initializedBy.lastname}
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

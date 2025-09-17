import { memo } from 'react'
import {
    Alert,
    AlertTitle,
    Box,
    Card,
    CardContent,
    LinearProgress,
    List,
    ListItemText,
    Typography,
} from '@mui/material'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import Throbber from '@components/Throbber.tsx'
import { WebDAVExportType } from '@api/types.gen.ts'

interface ExportStatusCardProps {
    exportStatus: {
        processId: string
        filesExported: number
        filesWithError: number
        totalFilesToExport: number
        exportFolderName: string
        events: string[]
        exportTypes: WebDAVExportType[]
        exportInitializedAt: string
        exportInitializedBy?: {
            firstname: string
            lastname: string
        }
    }
    webDavExportTypeNames: Map<WebDAVExportType, string>
}

const ExportStatusCard = memo(({ exportStatus, webDavExportTypeNames }: ExportStatusCardProps) => {
    const { t } = useTranslation()
    
    const isComplete = exportStatus.filesExported + exportStatus.filesWithError === exportStatus.totalFilesToExport
    const hasErrors = exportStatus.filesWithError > 0

    return (
        <Card>
            <CardContent>
                {isComplete ? (
                    hasErrors ? (
                        <Alert severity={'error'}>
                            <AlertTitle>
                                {t('webDAV.export.status.error.title')}
                            </AlertTitle>
                            {t('webDAV.export.status.error.body', {
                                exported: exportStatus.filesExported,
                                total: exportStatus.totalFilesToExport,
                            })}
                        </Alert>
                    ) : (
                        <Alert severity={'success'}>
                            <AlertTitle>
                                {t('webDAV.export.status.success.title')}
                            </AlertTitle>
                            {t('webDAV.export.status.success.body', {
                                count: exportStatus.filesExported,
                            })}
                        </Alert>
                    )
                ) : (
                    <Box sx={{width: 1}}>
                        <LinearProgress
                            variant="determinate"
                            value={
                                (exportStatus.filesExported /
                                    exportStatus.totalFilesToExport) *
                                100
                            }
                        />
                        <Alert severity={'info'} icon={<Throbber />}>
                            <AlertTitle>
                                {t('webDAV.export.status.pending.title')}
                            </AlertTitle>
                            {t('webDAV.export.status.pending.body', {
                                exported: exportStatus.filesExported,
                                total: exportStatus.totalFilesToExport,
                            })}
                        </Alert>
                    </Box>
                )}
                <Box
                    sx={{
                        display: 'flex',
                        flexWrap: 'wrap',
                        gap: 2,
                        mt: 1,
                        mx: 2,
                    }}>
                    <Box sx={{flex: 1}}>
                        <Typography variant={'h3'}>
                            {exportStatus.exportFolderName}
                        </Typography>

                        <List>
                            {exportStatus.events.map(event => (
                                <ListItemText
                                    key={exportStatus.processId + event}>
                                    {event}
                                </ListItemText>
                            ))}
                        </List>
                        <List>
                            {exportStatus.exportTypes.map(type => (
                                <ListItemText
                                    key={exportStatus.processId + type}>
                                    {webDavExportTypeNames.get(type) ?? '-'}
                                </ListItemText>
                            ))}
                        </List>
                    </Box>
                    <Box sx={{textAlign: 'right'}}>
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
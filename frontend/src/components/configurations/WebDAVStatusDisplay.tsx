import {Alert, Box, Stack} from '@mui/material'
import WebDAVStatusCard from '@components/configurations/ExportStatusCard.tsx'
import Throbber from '@components/Throbber.tsx'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getWebDavExportStatus, getWebDavImportStatus} from '@api/sdk.gen.ts'
import {useTranslation} from 'react-i18next'
import {useState} from 'react'

type Props = {
    reloadExportStatus: boolean
    reloadImportStatus: boolean
}

const WebDAVStatusDisplay = ({reloadExportStatus, reloadImportStatus}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [reloadExportFrequently, setReloadExportFrequently] = useState(false)

    const {data: exportStatusData, pending: exportStatusPending} = useFetch(
        signal => getWebDavExportStatus({signal}),
        {
            onResponse: ({data, error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.single', {entity: t('webDAV.export.status.status')}),
                    )
                } else {
                    if (
                        data.some(
                            process =>
                                process.totalFilesToExport !==
                                    process.filesExported + process.filesWithError ||
                                process.totalDataToExport !==
                                    process.dataExported + process.dataWithError,
                        )
                    ) {
                        setReloadExportFrequently(true)
                    } else {
                        setReloadExportFrequently(false)
                    }
                }
            },
            deps: [reloadExportFrequently, reloadExportStatus],
            autoReloadInterval: reloadExportFrequently ? 1000 : 6000,
        },
    )

    const [reloadImportFrequently, setReloadImportFrequently] = useState(false)

    const {data: importStatusData, pending: importStatusPending} = useFetch(
        signal => getWebDavImportStatus({signal}),
        {
            onResponse: ({data, error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.single', {entity: t('webDAV.import.status.status')}),
                    )
                } else {
                    if (
                        data.some(
                            process =>
                                process.totalDataToImport !==
                                process.dataImported + process.dataWithError,
                        )
                    ) {
                        setReloadImportFrequently(true)
                    } else {
                        setReloadImportFrequently(false)
                    }
                }
            },
            deps: [reloadImportFrequently, reloadImportStatus],
            autoReloadInterval: reloadImportFrequently ? 1000 : 6000,
        },
    )

    return (
        <Box sx={{display: 'flex', gap: 2, justifyContent: 'space-between', flexWrap: 'wrap'}}>
            {exportStatusData ? (
                <Stack spacing={2} sx={{maxWidth: 600, flex: 1}}>
                    {exportStatusData
                        .sort((a, b) => (a.exportInitializedAt > b.exportInitializedAt ? -1 : 1))
                        .map(exportStatus => (
                            <WebDAVStatusCard
                                key={exportStatus.processId}
                                status={exportStatus}
                                isExport={true}
                            />
                        ))}
                </Stack>
            ) : exportStatusPending ? (
                <Throbber />
            ) : (
                <Alert severity={'error'}>
                    {t('common.load.error.single', {
                        entity: t('webDAV.export.status.status'),
                    })}
                </Alert>
            )}
            {importStatusData ? (
                <Stack spacing={2} sx={{maxWidth: 600, flex: 1}}>
                    {importStatusData
                        .sort((a, b) => (a.importInitializedAt > b.importInitializedAt ? -1 : 1))
                        .map(importStatus => (
                            <WebDAVStatusCard
                                key={importStatus.processId}
                                status={importStatus}
                                isExport={false}
                            />
                        ))}
                </Stack>
            ) : importStatusPending ? (
                <Throbber />
            ) : (
                <Alert severity={'error'}>
                    {t('common.load.error.single', {
                        entity: t('webDAV.import.status.status'),
                    })}
                </Alert>
            )}
        </Box>
    )
}
export default WebDAVStatusDisplay

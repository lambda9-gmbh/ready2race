import {Alert, Stack} from '@mui/material'
import ExportStatusCard from '@components/configurations/ExportStatusCard.tsx'
import Throbber from '@components/Throbber.tsx'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getWebDavExportStatus} from '@api/sdk.gen.ts'
import {useTranslation} from 'react-i18next'
import {useState} from 'react'

type Props = {
    reloadExportStatus: boolean
}

const ExportStatusDisplay = ({reloadExportStatus}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [reloadFrequently, setReloadFrequently] = useState(false)

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
                                process.filesExported + process.filesWithError,
                        )
                    ) {
                        setReloadFrequently(true)
                    } else {
                        setReloadFrequently(false)
                    }
                }
            },
            deps: [reloadFrequently, reloadExportStatus],
            autoReloadInterval: reloadFrequently ? 1000 : 6000,
        },
    )

    return (
        <Stack spacing={2}>
            {exportStatusData ? (
                <Stack spacing={2}>
                    {exportStatusData
                        .sort((a, b) => (a.exportInitializedAt > b.exportInitializedAt ? -1 : 1))
                        .map(exportStatus => (
                            <ExportStatusCard
                                key={exportStatus.processId}
                                exportStatus={exportStatus}
                            />
                        ))}
                </Stack>
            ) : exportStatusPending ? (
                <Throbber />
            ) : (
                <Alert severity={'error'}>
                    {t('common.load.error.single', {entity: t('webDAV.export.status.status')})}
                </Alert>
            )}
        </Stack>
    )
}
export default ExportStatusDisplay

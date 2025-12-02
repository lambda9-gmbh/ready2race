import {useTranslation} from 'react-i18next'
import {useFeedback} from '@utils/hooks.ts'
import {ClubImportRequest} from '@api/types.gen.ts'
import {importClubs} from '@api/sdk.gen.ts'
import CsvImportWizard from '@components/csv/CsvImportWizard'
import {CsvImportWizardConfig, CsvImportWizardResult} from '@components/csv/types'

type Props = {
    open: boolean
    onClose: () => void
    reloadClubs: () => void
}

const ClubImportDialog = ({open, onClose, reloadClubs}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const wizardConfig: CsvImportWizardConfig = {
        title: t('club.import'),
        fieldMappings: [
            {
                key: 'colName',
                label: t('club.name'),
                required: true,
            },
        ],
        defaultSeparator: ',',
        defaultCharset: 'UTF-8',
    }

    const handleComplete = async (result: CsvImportWizardResult) => {
        const request: ClubImportRequest = {
            separator: result.config.separator,
            charset: result.config.charset,
            noHeader: !result.config.hasHeader,
            colName: result.columnMappings.colName as string,
        }

        const {error} = await importClubs({
            body: {
                request,
                files: [result.config.file],
            },
        })

        if (error) {
            if (error.status.value === 400) {
                if (error.errorCode === 'FILE_ERROR') {
                    feedback.error(t('common.error.upload.FILE_ERROR'))
                } else if (error.message === 'Unsupported file type') {
                    // TODO: replace with error code!
                    feedback.error(t('common.error.upload.unsupportedType'))
                } else {
                    feedback.error(t('common.error.unexpected'))
                }
                throw error
            } else if (error.status.value === 422) {
                const details = 'details' in error && error.details
                switch (error.errorCode) {
                    case 'SPREADSHEET_NO_HEADERS':
                        feedback.error(t('common.error.upload.NO_HEADERS'))
                        break
                    case 'SPREADSHEET_MALFORMED':
                        feedback.error(t('common.error.upload.SPREADSHEET_MALFORMED'))
                        break
                    case 'SPREADSHEET_COLUMN_UNKNOWN':
                        feedback.error(
                            t('common.error.upload.COLUMN_UNKNOWN', details as {expected: string}),
                        )
                        break
                    case 'SPREADSHEET_CELL_BLANK':
                        feedback.error(
                            t(
                                'common.error.upload.CELL_BLANK',
                                details as {row: number; column: string},
                            ),
                        )
                        break
                    case 'SPREADSHEET_WRONG_CELL_TYPE':
                        feedback.error(
                            t(
                                'common.error.upload.WRONG_CELL_TYPE',
                                details as {
                                    row: number
                                    column: string
                                    actual: string
                                    expected: string
                                },
                            ),
                        )
                        break
                    case 'SPREADSHEET_UNPARSABLE_STRING':
                        feedback.error(
                            t(
                                'common.error.upload.UNPARSABLE_STRING',
                                details as {
                                    row: number
                                    column: string
                                    value: string
                                },
                            ),
                        )
                        break
                    default:
                        feedback.error(t('common.error.unexpected'))
                        break
                }
                throw error
            }
        } else {
            onClose()
            reloadClubs()
        }
    }

    return <CsvImportWizard open={open} onClose={onClose} config={wizardConfig} onComplete={handleComplete} />
}

export default ClubImportDialog

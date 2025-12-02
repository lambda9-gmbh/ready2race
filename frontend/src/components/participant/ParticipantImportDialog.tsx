import {useTranslation} from 'react-i18next'
import {importClubParticipants} from '@api/sdk.gen.ts'
import {ParticipantImportRequest} from '@api/types.gen.ts'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {clubIndexRoute} from '@routes'
import {useFeedback} from '@utils/hooks.ts'
import CsvImportWizard from '@components/csv/CsvImportWizard'
import {CsvImportWizardConfig, CsvImportWizardResult} from '@components/csv/types'

type Props = {
    open: boolean
    onClose: () => void
    reloadParticipants: () => void
}

const ParticipantImportDialog = ({open, onClose, reloadParticipants}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const {clubId} = clubIndexRoute.useParams()

    const wizardConfig: CsvImportWizardConfig = {
        title: t('club.participant.import'),
        fieldMappings: [
            {
                key: 'colFirstname',
                label: t('club.participant.upload.dialog.col.firstname'),
                required: true,
                defaultColumnName: t('entity.firstname'),
            },
            {
                key: 'colLastname',
                label: t('club.participant.upload.dialog.col.lastname'),
                required: true,
                defaultColumnName: t('entity.lastname'),
            },
            {
                key: 'colYear',
                label: t('club.participant.upload.dialog.col.year'),
                required: true,
            },
            {
                key: 'colGender',
                label: t('club.participant.upload.dialog.col.gender'),
                required: true,
                defaultColumnName: t('entity.gender'),
            },
            {
                key: 'colEmail',
                label: t('club.participant.upload.dialog.col.email'),
                required: false,
            },
            {
                key: 'colExternalClubname',
                label: t('club.participant.upload.dialog.col.external'),
                required: false,
            },
        ],
        valueMappings: [
            {
                key: 'valueGenderMale',
                label: t('club.participant.upload.dialog.value.gender.male'),
                required: true,
                defaultValue: 'M',
            },
            {
                key: 'valueGenderFemale',
                label: t('club.participant.upload.dialog.value.gender.female'),
                required: true,
                defaultValue: 'F',
            },
            {
                key: 'valueGenderDiverse',
                label: t('club.participant.upload.dialog.value.gender.diverse'),
                required: true,
                defaultValue: 'D',
            },
        ],
        defaultSeparator: ',',
        defaultCharset: 'UTF-8',
    }

    const handleComplete = async (result: CsvImportWizardResult) => {
        const request: ParticipantImportRequest = {
            separator: result.config.separator,
            charset: result.config.charset,
            noHeader: !result.config.hasHeader,
            colFirstname: result.columnMappings.colFirstname as string,
            colLastname: result.columnMappings.colLastname as string,
            colYear: result.columnMappings.colYear as string,
            colGender: result.columnMappings.colGender as string,
            colExternalClubname: takeIfNotEmpty(result.columnMappings.colExternalClubname as string),
            colEmail: takeIfNotEmpty(result.columnMappings.colEmail as string),
            valueGenderMale: result.valueMappings.valueGenderMale as string,
            valueGenderFemale: result.valueMappings.valueGenderFemale as string,
            valueGenderDiverse: result.valueMappings.valueGenderDiverse as string,
        }

        const {error} = await importClubParticipants({
            path: {
                clubId,
            },
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
            reloadParticipants()
        }
    }

    return <CsvImportWizard open={open} onClose={onClose} config={wizardConfig} onComplete={handleComplete} />
}

export default ParticipantImportDialog

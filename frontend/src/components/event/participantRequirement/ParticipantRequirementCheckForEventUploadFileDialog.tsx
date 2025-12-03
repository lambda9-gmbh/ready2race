import {useTranslation} from 'react-i18next'
import {
    checkParticipantRequirementsForEvent,
    getActiveParticipantRequirementsForEvent,
} from '@api/sdk.gen.ts'
import {useMemo, useState} from 'react'
import {
    Alert,
    Button,
    DialogActions,
    DialogContent,
    DialogTitle,
    Stack,
    Autocomplete,
    TextField,
} from '@mui/material'
import {AutocompleteOption} from '@utils/types.ts'
import {eventRoute} from '@routes'
import {Info} from '@mui/icons-material'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import BaseDialog from '@components/BaseDialog.tsx'
import {Trans} from 'react-i18next'
import CsvImportWizard from '@components/csv/CsvImportWizard'
import {CsvImportWizardConfig, CsvImportWizardResult} from '@components/csv/types'

type Props = {
    open: boolean
    onClose: () => void
    onSuccess?: () => void
}

const ParticipantRequirementCheckForEventUploadFileDialog = (props: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const {eventId} = eventRoute.useParams()

    const [selectedRequirementId, setSelectedRequirementId] = useState<string>('')
    const [showWizard, setShowWizard] = useState(false)

    const {data: requirementsData, pending: requirementsPending} = useFetch(
        signal =>
            getActiveParticipantRequirementsForEvent({
                signal,
                path: {eventId},
                query: {sort: JSON.stringify([{field: 'NAME', direction: 'ASC'}])},
            }),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('participantRequirement.participantRequirement'),
                        }),
                    )
                }
            },
        },
    )

    const requirements: AutocompleteOption[] = useMemo(
        () =>
            requirementsData?.data.map(dto => ({
                id: dto.id,
                label: dto.name,
            })) ?? [],
        [requirementsData?.data],
    )

    const handleOpenWizard = () => {
        if (!selectedRequirementId) {
            feedback.error(t('common.form.required'))
            return
        }
        setShowWizard(true)
    }

    const handleCloseWizard = () => {
        setShowWizard(false)
    }

    const wizardConfig: CsvImportWizardConfig = {
        title: t('event.participantRequirement.checkUpload'),
        fieldMappings: [
            {
                key: 'firstnameColName',
                label: t('event.participantRequirement.firstnameColName'),
                required: true,
                defaultColumnName: t('entity.firstname'),
            },
            {
                key: 'lastnameColName',
                label: t('event.participantRequirement.lastnameColName'),
                required: true,
                defaultColumnName: t('entity.lastname'),
            },
            {
                key: 'yearsColName',
                label: t('event.participantRequirement.yearsColName'),
                required: false,
            },
            {
                key: 'clubColName',
                label: t('event.participantRequirement.clubColName'),
                required: false,
            },
            {
                key: 'requirementColName',
                label: t('event.participantRequirement.requirementColName'),
                required: false,
            },
        ],
        valueMappings: [
            {
                key: 'requirementIsValidValue',
                label: t('event.participantRequirement.requirementIsValidValue'),
                required: false,
            },
        ],
        defaultSeparator: ';',
        defaultCharset: 'UTF-8',
    }

    const handleComplete = async (result: CsvImportWizardResult) => {
        const config = {
            requirementId: selectedRequirementId,
            separator: result.config.separator,
            noHeader: !result.config.hasHeader,
            charset: result.config.charset,
            firstnameColName: result.columnMappings.firstnameColName as string,
            lastnameColName: result.columnMappings.lastnameColName as string,
            yearsColName: result.columnMappings.yearsColName as string | undefined,
            clubColName: result.columnMappings.clubColName as string | undefined,
            requirementColName: result.columnMappings.requirementColName as string | undefined,
            requirementIsValidValue: result.valueMappings.requirementIsValidValue as
                | string
                | undefined,
        }

        const {error} = await checkParticipantRequirementsForEvent({
            path: {eventId},
            body: {
                config,
                files: [result.config.file],
            },
        })

        if (error) {
            feedback.error(t('common.error.unexpected'))
            throw error
        } else {
            setShowWizard(false)
            props.onClose()
            if (props.onSuccess) {
                props.onSuccess()
            }
        }
    }

    return (
        <>
            <BaseDialog open={props.open && !showWizard} onClose={props.onClose}>
                <DialogTitle>{t('event.participantRequirement.checkUpload')}</DialogTitle>
                <DialogContent>
                    <Stack spacing={4}>
                        <Autocomplete
                            loading={requirementsPending}
                            options={requirements}
                            value={requirements.find(r => r?.id === selectedRequirementId) || null}
                            onChange={(_, value: AutocompleteOption | null) => {
                                setSelectedRequirementId(value?.id ?? '')
                            }}
                            getOptionLabel={option => option?.label ?? ''}
                            isOptionEqualToValue={(option, value) => option?.id === value?.id}
                            renderInput={params => (
                                <TextField
                                    {...params}
                                    label={t('participantRequirement.participantRequirement')}
                                    required
                                />
                            )}
                        />
                        <Alert icon={<Info />} severity={'info'}>
                            {t('event.participantRequirement.info')}
                        </Alert>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={props.onClose}>
                        <Trans i18nKey={'common.cancel'} />
                    </Button>
                    <Button
                        onClick={handleOpenWizard}
                        variant="contained"
                        color="primary"
                        disabled={!selectedRequirementId}>
                        <Trans i18nKey={'common.next'} />
                    </Button>
                </DialogActions>
            </BaseDialog>

            {showWizard && (
                <CsvImportWizard
                    open={showWizard}
                    onClose={handleCloseWizard}
                    config={wizardConfig}
                    onComplete={handleComplete}
                />
            )}
        </>
    )
}

export default ParticipantRequirementCheckForEventUploadFileDialog

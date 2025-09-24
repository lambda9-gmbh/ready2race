import {memo, useState} from 'react'
import {Button, DialogActions, DialogContent, DialogTitle, Typography, Box} from '@mui/material'
import Grid2 from '@mui/material/Grid2'
import BaseDialog from '@components/BaseDialog.tsx'
import {useTranslation} from 'react-i18next'
import {WebDAVExportType} from '@api/types.gen.ts'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {
    getWebDavImportOptionFolders,
    getWebDavImportOptionTypes,
    importDataFromWebDav,
} from '@api/sdk.gen.ts'
import {FormContainer, UseFormReturn} from 'react-hook-form-mui'
import {WebDAVImportForm} from '@components/configurations/WebDavExportImport.tsx'
import Throbber from '@components/Throbber.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'
import {FormInputSelect} from '@components/form/input/FormInputSelect.tsx'

type ImportDialogProps = {
    dialogOpen: boolean
    onClose: () => void
    importFormContext: UseFormReturn<WebDAVImportForm>
    webDavExportTypeNames: Map<WebDAVExportType, string>
}

const ImportDialog = memo(
    ({dialogOpen, webDavExportTypeNames, importFormContext, ...props}: ImportDialogProps) => {
        const {t} = useTranslation()
        const feedback = useFeedback()

        const [submitting, setSubmitting] = useState<boolean>(false)

        const {data: foldersData, pending: foldersPending} = useFetch(
            signal => getWebDavImportOptionFolders({signal}),
            {
                onResponse: ({error}) => {
                    if (error) {
                        feedback.error(t('webDAV.import.error.loadFolders'))
                    }
                },
                deps: [],
            },
        )

        const folderWatch = importFormContext.watch('selectedFolder')

        const {data: optionsData, pending: optionsPending} = useFetch(
            signal => getWebDavImportOptionTypes({signal, path: {folderName: folderWatch}}),
            {
                onResponse: ({data, error}) => {
                    if (error) {
                        feedback.error(t('webDAV.import.error.loadOptions'))
                    } else {
                        importFormContext.reset({
                            selectedFolder: importFormContext.getValues('selectedFolder'),
                            checkedResources: data.data.map(_ => false),
                            availableEvents: data.events.map(event => ({
                                eventFolderName: event.eventFolderName,
                                checked: false,
                                availableCompetitions: event.competitions.map(competition => ({
                                    competitionFolderName: competition.competitionFolderName,
                                    checked: false,
                                })),
                            })),
                        })
                    }
                },
                deps: [folderWatch],
                preCondition: () => folderWatch !== '',
            },
        )

        const onClose = () => {
            importFormContext.reset()
            props.onClose()
        }

        const onSubmit = async (formData: WebDAVImportForm) => {
            if (!optionsData || optionsData.data.length !== formData.checkedResources.length) {
                feedback.error(t('common.error.unexpected'))
                return
            }
            const selectedTypes = formData.checkedResources
                .map((checked, index) => ({
                    value: optionsData.data[index],
                    checked: checked,
                }))
                .filter(resource => resource.checked)

            if (selectedTypes.length === 0) {
                // todo: make this a react hook form validation
                feedback.error(t('webDAV.import.error.noSelection'))
                return
            }

            setSubmitting(true)
            const {error} = await importDataFromWebDav({
                body: {
                    folderName: formData.selectedFolder,
                    selectedData: selectedTypes.map(resource => resource.value),
                    selectedEvents: formData.availableEvents
                        .filter(event => event.checked)
                        .map(event => ({
                            eventFolderName: event.eventFolderName,
                            competitionFolderNames: event.availableCompetitions
                                .filter(comp => comp.checked)
                                .map(comp => comp.competitionFolderName),
                        })),
                },
            })
            setSubmitting(false)

            if (error) {
                feedback.error(t('webDAV.import.error.failed'))
            } else {
                feedback.success(t('webDAV.import.success'))
                onClose()
            }
        }

        return (
            <BaseDialog open={dialogOpen} onClose={onClose} maxWidth={'sm'}>
                <DialogTitle>{t('webDAV.import.import')}</DialogTitle>
                <FormContainer formContext={importFormContext} onSuccess={onSubmit}>
                    <DialogContent sx={{display: 'flex', flexDirection: 'column'}}>
                        {foldersPending ? (
                            <Throbber />
                        ) : (
                            <>
                                <FormInputSelect
                                    name={'selectedFolder'}
                                    required
                                    options={foldersData?.map(folder => ({
                                        id: folder,
                                        label: folder,
                                    }))}
                                />
                                {folderWatch && (
                                    <Box
                                        sx={{
                                            display: 'flex',
                                            flexDirection: 'column',
                                            gap: 2,
                                        }}>
                                        {optionsPending ? (
                                            <Throbber />
                                        ) : (
                                            <>
                                                <Typography variant="subtitle1" sx={{mt: 2, mb: 1}}>
                                                    {t('webDAV.import.selectData')}
                                                </Typography>
                                                <Grid2 container spacing={1}>
                                                    {optionsData?.data.map((importType, index) => (
                                                        <Grid2
                                                            key={importType}
                                                            size={{xs: 12, sm: 6}}>
                                                            <FormInputCheckbox
                                                                name={`checkedResources.${index}`}
                                                                label={webDavExportTypeNames.get(
                                                                    importType,
                                                                )}
                                                            />
                                                        </Grid2>
                                                    ))}
                                                </Grid2>
                                            </>
                                        )}
                                    </Box>
                                )}
                            </>
                        )}
                    </DialogContent>
                    <DialogActions>
                        <Button onClick={onClose}>{t('common.cancel')}</Button>
                        <SubmitButton submitting={submitting}>
                            {t('webDAV.import.confirm')}
                        </SubmitButton>
                    </DialogActions>
                </FormContainer>
            </BaseDialog>
        )
    },
)

export default ImportDialog

import AssignDocumentTemplate from '@components/documentTemplate/AssignDocumentTemplate.tsx'
import AssignBankAccount from '@components/bankAccount/AssignBankAccount.tsx'
import AssignContactInformation from '@components/contactInformation/AssignContactInformation.tsx'
import {Box, Button, DialogActions, DialogContent, DialogTitle, Stack} from '@mui/material'
import {useState} from 'react'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {
    assignCompetitionsToEventDay,
    assignDaysToCompetition,
    exportDataByWebDav, getEventDays, getEvents,
} from '@api/sdk.gen.ts'
import {WebDAVExportType} from '@api/types.gen'
import {FormContainer, MultiSelectElement, useForm} from 'react-hook-form-mui'
import BaseDialog from '@components/BaseDialog.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {paginationParameters} from "@utils/ApiUtils.ts";
import {AutocompleteOption} from '@utils/types'
import {eventDayName} from "@components/event/common.ts";

type ExportForm = {
    name: string
    selectedEvents: string[]
    selectedResources: WebDAVExportType[]
}

const GlobalConfigurationsTab = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {data: eventsData, pending: eventsPending} = useFetch(
        signal =>
            getEvents({signal}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.events'),
                        }),
                    )
                }
            },
            deps: [],
        },
    )

    const eventOptions: AutocompleteOption[] = eventsData?.data.map(value => ({
        id: value.id,
        label: value.name,
    })) ?? []

    const formContext = useForm<ExportForm>()

    const [submitting, setSubmitting] = useState(false)

    const [dialogOpen, setDialogOpen] = useState(false)

    const openDialog = () => {
        setDialogOpen(true)
    }

    const closeDialog = () => {
        setDialogOpen(false)
    }

    const onSubmit = async (formData: ExportForm) => {
        setSubmitting(true)
        const {error} = await exportDataByWebDav({
            body: {
                name: formData.name,
                selectedResources: formData.selectedResources,
                events: formData.selectedEvents,
            },
        })
        setSubmitting(false)

        if (error) {
            if (error.status.value === 409) {
                feedback.error('[todo] Error')
            } else if (error.status.value === 502) {
                feedback.error('[todo] Error')
            } else {
                feedback.error('[todo] Error')
            }
        } else {
            closeDialog()
            feedback.success('[todo] Successfully exported')
        }
        formContext.reset()
    }

    return (
        <Stack spacing={2}>
            <Box>
                <Button variant={'outlined'} onClick={openDialog}>[todo] Export data</Button>
                <BaseDialog open={dialogOpen} onClose={closeDialog} maxWidth={'xs'}>
                    <DialogTitle>{'[todo] Export data'}</DialogTitle>
                    <DialogContent>
                        <FormContainer formContext={formContext} onSuccess={onSubmit}>
                            <Stack spacing={2}>
                            <FormInputText
                                name={'name'}
                                required
                                label={'[todo] Export folder name'}
                            />
                            <MultiSelectElement
                                name={'selectedEvents'}
                                options={eventOptions}
                                showCheckbox
                                showChips
                                formControlProps={{sx: {width: 1}}}
                            /></Stack>
                        </FormContainer>
                    </DialogContent>
                    <DialogActions>
                        <Button onClick={closeDialog} disabled={submitting}>
                            {t('common.cancel')}
                        </Button>
                        <SubmitButton submitting={submitting}>{'[todo] Export'}</SubmitButton>
                    </DialogActions>
                </BaseDialog>
            </Box>
            <AssignDocumentTemplate />
            <AssignBankAccount />
            <AssignContactInformation />
        </Stack>
    )
}

export default GlobalConfigurationsTab

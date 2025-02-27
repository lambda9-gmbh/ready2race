import {PropsWithChildren, useEffect, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {FieldValues, FormContainer, UseFormReturn} from 'react-hook-form-mui'
import {Button, Dialog, DialogActions, DialogContent, DialogTitle} from '@mui/material'
import {RequestResult} from '@hey-api/client-fetch'
import {BaseEntityDialogProps} from '@utils/types.ts'
import {useFeedback} from '@utils/hooks.ts'
import {SubmitButton} from './form/SubmitButton.tsx'
import DialogCloseButton from './DialogCloseButton.tsx'
import {GridValidRowModel} from '@mui/x-data-grid'

type EntityDialogProps<
    Entity extends GridValidRowModel,
    Form extends FieldValues,
    AddError,
    UpdateError,
> = BaseEntityDialogProps<Entity> & ExtendedEntityDialogProps<Entity, Form, AddError, UpdateError>

type ExtendedEntityDialogProps<
    Entity extends GridValidRowModel,
    Form extends FieldValues,
    AddError,
    UpdateError,
> = {
    formContext: UseFormReturn<Form>
    onOpen: () => void
    addAction?: (formData: Form) => RequestResult<any, AddError, false>
    editAction?: (formData: Form, entity: Entity) => RequestResult<void, UpdateError, false>
}

//todo: add semantic tabs
const EntityDialog = <
    Entity extends GridValidRowModel,
    Form extends FieldValues,
    AddError,
    UpdateError,
>({
    entityName,
    dialogIsOpen,
    closeDialog,
    reloadData,
    entity,
    formContext,
    onOpen,
    addAction,
    editAction,
    children,
}: PropsWithChildren<EntityDialogProps<Entity, Form, AddError, UpdateError>>) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const handleClose = () => {
        closeDialog()
    }

    const onSubmit = async (formData: Form) => {
        setSubmitting(true)
        const requestResult = entity
            ? await editAction?.(formData, entity)
            : await addAction?.(formData)
        setSubmitting(false)

        if (requestResult) {
            if (requestResult.error) {
                // todo better error display with specific error types
                console.error(requestResult.error)
                if (entity) {
                    feedback.error(t('entity.edit.error', {entity: entityName}))
                } else {
                    feedback.error(t('entity.add.error', {entity: entityName}))
                }
            } else {
                handleClose()
                reloadData()
                if (entity) {
                    feedback.success(t('entity.edit.success', {entity: entityName}))
                } else {
                    feedback.success(t('entity.add.success', {entity: entityName}))
                }
            }
        }
    }

    useEffect(() => {
        if (dialogIsOpen) {
            onOpen()
        }
    }, [dialogIsOpen, onOpen])

    return (
        <Dialog open={dialogIsOpen} fullWidth={true} maxWidth={'sm'} className="ready2race">
            <FormContainer formContext={formContext} onSuccess={data => onSubmit(data)}>
                <DialogTitle>
                    {t(`entity.${entity ? 'edit' : 'add'}.action`, {entity: entityName})}
                </DialogTitle>
                <DialogCloseButton onClose={handleClose} />
                <DialogContent>{children}</DialogContent>
                <DialogActions>
                    <Button onClick={handleClose} disabled={submitting}>
                        {t('common.cancel')}
                    </Button>
                    <SubmitButton label={t('common.save')} submitting={submitting} />
                </DialogActions>
            </FormContainer>
        </Dialog>
    )
}

export default EntityDialog

import {PropsWithChildren, useEffect, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {FieldValues, FormContainer, UseFormReturn} from 'react-hook-form-mui'
import {Button, Dialog, DialogActions, DialogContent, DialogTitle} from '@mui/material'
import {RequestResult} from '@hey-api/client-fetch'
import {BaseEntityDialogProps} from '../utils/types.ts'
import {useFeedback} from '../utils/hooks.ts'
import {SubmitButton} from './form/SubmitButton.tsx'
import DialogCloseButton from './DialogCloseButton.tsx'
import {GridValidRowModel} from '@mui/x-data-grid'

type EntityDialogProps<
    Entity extends GridValidRowModel,
    Form extends FieldValues,
> = BaseEntityDialogProps<Entity> & ExtendedEntityDialogProps<Entity, Form>

type ExtendedEntityDialogProps<Entity extends GridValidRowModel, Form extends FieldValues> = {
    formContext: UseFormReturn<Form>
    onOpen: () => void
    addAction?: (formData: Form) => RequestResult<any, string, false> // todo: specific error type
    editAction?: (formData: Form, entity: Entity) => RequestResult<void, string, false> // todo: specific error type
}

const EntityDialog = <Entity extends GridValidRowModel, Form extends FieldValues>({
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
}: PropsWithChildren<EntityDialogProps<Entity, Form>>) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const handleClose = () => {
        closeDialog()
    }

    const onSubmit = async (formData: Form) => {
        setSubmitting(true)
        let requestResult = entity
            ? await editAction?.(formData, entity)
            : await addAction?.(formData)
        setSubmitting(false)

        if (requestResult) {
            if (requestResult.error) {
                // todo better error display with specific error types
                console.log(requestResult.error)
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
        <Dialog open={dialogIsOpen} fullWidth={true} maxWidth={'sm'}>
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

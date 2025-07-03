import {BaseSyntheticEvent, PropsWithChildren, useEffect, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {FieldValues, FormContainer, UseFormReturn} from 'react-hook-form-mui'
import {Button, DialogActions, DialogContent, DialogProps, DialogTitle} from '@mui/material'
import {RequestResult} from '@hey-api/client-fetch'
import {BaseEntityDialogProps} from '@utils/types.ts'
import {useFeedback} from '@utils/hooks.ts'
import {SubmitButton} from './form/SubmitButton.tsx'
import {GridValidRowModel} from '@mui/x-data-grid'
import {ApiError} from '@api/types.gen.ts'
import BaseDialog from '@components/BaseDialog.tsx'

type EntityDialogProps<
    Entity extends GridValidRowModel,
    Form extends FieldValues,
    AddError extends ApiError,
    UpdateError extends ApiError,
> = BaseEntityDialogProps<Entity> &
    ExtendedEntityDialogProps<Entity, Form, AddError, UpdateError> &
    Partial<DialogProps>

type ExtendedEntityDialogProps<
    Entity extends GridValidRowModel,
    Form extends FieldValues,
    AddError extends ApiError,
    UpdateError extends ApiError,
> = {
    formContext: UseFormReturn<Form>
    onOpen: () => void
    addAction?: (formData: Form) => RequestResult<any, AddError, false>
    editAction?: (formData: Form, entity: Entity) => RequestResult<void, UpdateError, false>
    onAddError?: (error: AddError) => void
    onEditError?: (error: UpdateError) => void
    title?: string
    showSaveAndNew?: boolean
    disableSave?: boolean
}

//todo: add semantic tabs
const EntityDialog = <
    Entity extends GridValidRowModel,
    Form extends FieldValues,
    AddError extends ApiError,
    UpdateError extends ApiError,
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
    onAddError,
    onEditError,
    children,
    title,
    showSaveAndNew,
    disableSave,
    ...props
}: PropsWithChildren<EntityDialogProps<Entity, Form, AddError, UpdateError>>) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const handleClose = () => {
        closeDialog()
    }

    const handleErrorGeneric = (_: AddError | UpdateError) => {
        if (entity) {
            feedback.error(t('entity.edit.error', {entity: entityName}))
        } else {
            feedback.error(t('entity.add.error', {entity: entityName}))
        }
    }

    const onSubmit = async (formData: Form, event: BaseSyntheticEvent | undefined) => {
        const addNew =
            showSaveAndNew &&
            entity == null &&
            (event?.nativeEvent as SubmitEvent)?.submitter?.id === 'saveAndNew'

        if (entity) {
            if (!editAction) throw Error('Missing edit action')
        } else {
            if (!addAction) throw Error('Missing add action')
        }
        setSubmitting(true)
        const addResult = entity === undefined ? await addAction?.(formData) : undefined
        const editResult = entity !== undefined ? await editAction?.(formData, entity) : undefined
        setSubmitting(false)

        if (addResult?.error) {
            onAddError ? onAddError(addResult.error) : handleErrorGeneric(addResult.error)
        } else if (editResult?.error) {
            onEditError ? onEditError(editResult.error) : handleErrorGeneric(editResult.error)
        } else {
            if (addNew) {
                onOpen()
            } else {
                handleClose()
            }
            reloadData()
            if (entity) {
                feedback.success(t('entity.edit.success', {entity: entityName}))
            } else {
                feedback.success(t('entity.add.success', {entity: entityName}))
            }
        }
    }

    useEffect(() => {
        if (dialogIsOpen) {
            onOpen()
        }
    }, [dialogIsOpen, onOpen])

    return (
        <BaseDialog
            {...props}
            open={dialogIsOpen}
            onClose={handleClose}
            maxWidth={props.maxWidth ?? 'sm'}>
            <DialogTitle>
                {title ?? t(`entity.${entity ? 'edit' : 'add'}.action`, {entity: entityName})}
            </DialogTitle>
            <FormContainer
                FormProps={{style: {display: 'contents'}}}
                formContext={formContext}
                onSuccess={(data, event) => onSubmit(data, event)}>
                <DialogContent dividers={true}>{children}</DialogContent>
                <DialogActions>
                    <Button onClick={handleClose} disabled={submitting}>
                        {t('common.cancel')}
                    </Button>
                    {showSaveAndNew && entity == null && (
                        <SubmitButton id={'saveAndNew'} submitting={submitting}>
                            {t('common.saveAndNew')}
                        </SubmitButton>
                    )}
                    <SubmitButton disabled={disableSave} submitting={submitting}>
                        {t('common.save')}
                    </SubmitButton>
                </DialogActions>
            </FormContainer>
        </BaseDialog>
    )
}

export default EntityDialog

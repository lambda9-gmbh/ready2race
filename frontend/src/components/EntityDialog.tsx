import {PropsWithChildren, useEffect, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {FieldValues, FormContainer, UseFormReturn} from 'react-hook-form-mui'
import {Button, Dialog, DialogActions, DialogContent, DialogTitle} from '@mui/material'
import {RequestResult} from '@hey-api/client-fetch'
import {BaseEntityDialogProps, PartialRequired} from '../utils/types.ts'
import {useFeedback} from '../utils/hooks.ts'
import {SubmitButton} from './form/SubmitButton.tsx'
import DialogCloseButton from './DialogCloseButton.tsx'

type EntityDialogProps<
    E extends object | undefined,
    F extends FieldValues = FieldValues,
    R = void,
> = E extends object
    ?
          | (BaseEntityDialogProps<E> & ExtendedEntityDialogProps<E, F, R>)
          | (PartialRequired<BaseEntityDialogProps<E>, 'entity'> &
                Omit<ExtendedEntityDialogProps<E, F, R>, 'addAction'>)
    : BaseEntityDialogProps<E> & Omit<ExtendedEntityDialogProps<E, F, R>, 'editAction'>

type ExtendedEntityDialogProps<
    E extends object | undefined,
    F extends FieldValues = FieldValues,
    R = void,
> = {
    formContext: UseFormReturn<F>
    onOpen: () => void
    title: (action: 'add' | 'edit') => string
    addAction?: (formData: F) => RequestResult<R, string, false> // todo: specific error type
    editAction: (formData: F, entity: E) => RequestResult<void, string, false> // todo: specific error type
    onSuccess?: (res: R | void) => void
    entityName?: string
}

const EntityDialog = <E extends object | undefined, F extends FieldValues = FieldValues, R = void>(
    props: PropsWithChildren<EntityDialogProps<E, F, R>>,
) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const handleClose = () => {
        props.closeDialog()
    }

    const entityTitle = props.entityName ?? t('entity.entity')

    const onSubmit = async (formData: F) => {
        setSubmitting(true)
        let requestResult = props.entity
            ? await props.editAction(formData, props.entity)
            : props.addAction && (await props.addAction(formData))
        setSubmitting(false)

        if (requestResult) {
            if (requestResult.error) {
                // todo better error display with specific error types
                console.log(requestResult.error)
                if (props.entity) {
                    feedback.error(t('entity.edit.error', {entity: entityTitle}))
                } else {
                    feedback.error(t('entity.add.error', {entity: entityTitle}))
                }
            } else {
                handleClose()
                props.onSuccess?.(requestResult.data)
                props.reloadData()
                if (props.entity) {
                    feedback.success(t('entity.edit.success', {entity: entityTitle}))
                } else {
                    feedback.success(t('entity.add.success', {entity: entityTitle}))
                }
            }
        }
    }

    useEffect(() => {
        if (props.dialogIsOpen) {
            props.onOpen()
        }
    }, [props.dialogIsOpen, props.onOpen]);

    return (
        <Dialog open={props.dialogIsOpen} fullWidth={true} maxWidth={'sm'}>
            <FormContainer formContext={props.formContext} onSuccess={data => onSubmit(data)} >
                <DialogTitle>{props.title(props.entity ? 'edit' : 'add')}</DialogTitle>
                <DialogCloseButton onClose={handleClose} />
                <DialogContent>
                    {props.children}
                </DialogContent>
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
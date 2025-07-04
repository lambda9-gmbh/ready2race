import {useTranslation} from 'react-i18next'
import {PropsWithChildren, useState} from 'react'
import {Confirmation, ConfirmationContext, ConfirmationOptions} from './ConfirmationContext'
import {Button, DialogActions, DialogContent, DialogTitle} from '@mui/material'
import BaseDialog from "@components/BaseDialog.tsx";

type ConfirmationState =
    | {
          open: false
          action: null
          options?: ConfirmationOptions
      }
    | {
          open: true
          action: () => void
          options?: ConfirmationOptions
      }

const closedConfirmation = (options?: ConfirmationOptions): ConfirmationState => ({
    open: false,
    action: null,
    options: options, // to avoid changing content right before closing dialog
})

export const ConfirmationProvider = (props: PropsWithChildren<{}>) => {
    const {t} = useTranslation()
    const [confirmation, setConfirmation] = useState<ConfirmationState>(closedConfirmation)

    const handleCancel = () => {
        if (confirmation.open) {
            confirmation.options?.cancelAction?.()
        }
        setConfirmation(closedConfirmation(confirmation.options))
    }

    const handleConfirm = () => {
        if (confirmation.open) {
            confirmation.action()
        }
        setConfirmation(closedConfirmation(confirmation.options))
    }

    const confirmationValue: Confirmation = {
        confirmAction: (action, options) =>
            setConfirmation({
                open: true,
                action,
                options,
            }),
    }

    return (
        <ConfirmationContext.Provider value={confirmationValue}>
            {props.children}
            <BaseDialog
                open={confirmation.open}
                onClose={handleCancel}
                maxWidth={'sm'}>
                <DialogTitle>
                    {confirmation.options?.title ?? t('common.confirmation.title')}
                </DialogTitle>
                <DialogContent dividers>
                    {confirmation.options?.content ?? t('common.confirmation.content')}
                </DialogContent>
                <DialogActions>
                    <Button variant={'outlined'} onClick={handleCancel}>
                        {confirmation.options?.cancelText ?? t('common.cancel')}
                    </Button>
                    <Button variant={'contained'} onClick={handleConfirm}>
                        {confirmation.options?.okText ?? t('common.ok')}
                    </Button>
                </DialogActions>
            </BaseDialog>
        </ConfirmationContext.Provider>
    )
}

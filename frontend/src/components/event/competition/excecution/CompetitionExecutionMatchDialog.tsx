import {Button, DialogActions, DialogContent, DialogTitle, Stack, Typography} from '@mui/material'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {PropsWithChildren} from 'react'
import {useTranslation} from 'react-i18next'

type Props = PropsWithChildren<{
    enterResults: boolean
    title: string
    fieldArrayError: string | null
    submitting: boolean
    closeDialog: () => void
    saveAndNext: boolean
}>
const CompetitionExecutionMatchDialog = ({submitting, ...props}: Props) => {
    const {t} = useTranslation()
    return (
        <>
            <DialogTitle>{props.title}</DialogTitle>
            <DialogContent dividers={true}>
                <Stack spacing={2}>
                    {props.fieldArrayError && (
                        <Typography color={'error'}>{props.fieldArrayError}</Typography>
                    )}
                    {props.children}
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={props.closeDialog} disabled={submitting}>
                    {t('common.cancel')}
                </Button>
                {props.saveAndNext && (
                    <SubmitButton id={'saveAndNext'} submitting={submitting}>
                        {t('common.saveAndNext')}
                    </SubmitButton>
                )}
                <SubmitButton submitting={submitting}>{t('common.save')}</SubmitButton>
            </DialogActions>
        </>
    )
}
export default CompetitionExecutionMatchDialog

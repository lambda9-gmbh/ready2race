import {Button, DialogActions, DialogContent, Divider, Stack, Typography} from '@mui/material'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {CompetitionMatchDto} from '@api/types.gen.ts'
import {PropsWithChildren} from 'react'
import {useTranslation} from 'react-i18next'

type Props = PropsWithChildren<{
    enterResults: boolean
    title: string
    selectedMatchDto: CompetitionMatchDto
    fieldArrayError: string | null
    submitting: boolean
    closeDialog: () => void
    saveAndNext: boolean
}>
const CompetitionExecutionMatchDialog = ({selectedMatchDto, submitting, ...props}: Props) => {
    const {t} = useTranslation()
    return (
        <>
            <DialogContent>
                <Typography variant={'h2'}>{props.title}</Typography>
                <Divider sx={{my: 4}} />
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

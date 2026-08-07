import {Alert, Button, DialogActions, DialogContent, DialogTitle, Stack} from '@mui/material'
import {FormContainer, useForm} from 'react-hook-form-mui'
import BaseDialog from '@components/BaseDialog.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {useEffect, useState} from 'react'
import {Trans, useTranslation} from 'react-i18next'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {getRaceClockerConfig, updateRaceClockerConfig} from '@api/sdk.gen.ts'
import {useFeedback} from '@utils/hooks.ts'

type Props = {
    open: boolean
    eventId: string
    competitionId: string
    onClose: () => void
}

type Form = {
    timeTrialResultsUrl: string
    heatsResultsUrl: string
}

const defaultValues: Form = {
    timeTrialResultsUrl: '',
    heatsResultsUrl: '',
}

/**
 * A competition needs two RaceClocker races: the qualification round runs as an individual-start race
 * (only those have a countdown), everything else as a wave-start race. Which of the two URLs is used
 * for a given heat is derived from the round, so nothing has to be assigned here.
 */
const RaceClockerConfigDialog = ({open, eventId, competitionId, onClose}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const formContext = useForm<Form>()
    const [submitting, setSubmitting] = useState(false)

    useEffect(() => {
        if (open) {
            formContext.reset(defaultValues)
            getRaceClockerConfig({path: {eventId, competitionId}}).then(({data, error}) => {
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                } else if (data) {
                    formContext.reset({
                        timeTrialResultsUrl: data.timeTrialResultsUrl ?? '',
                        heatsResultsUrl: data.heatsResultsUrl ?? '',
                    })
                }
            })
        }
    }, [open])

    return (
        <BaseDialog open={open} onClose={onClose} maxWidth={'sm'}>
            <DialogTitle>
                <Trans i18nKey={'event.competition.execution.raceclocker.config.title'} />
            </DialogTitle>
            <FormContainer
                formContext={formContext}
                onSuccess={async (data: Form) => {
                    setSubmitting(true)
                    const {error} = await updateRaceClockerConfig({
                        path: {eventId, competitionId},
                        body: {
                            timeTrialResultsUrl: data.timeTrialResultsUrl || null,
                            heatsResultsUrl: data.heatsResultsUrl || null,
                        },
                    })
                    setSubmitting(false)

                    if (error) {
                        feedback.error(
                            error.status.value === 422
                                ? t('event.competition.execution.raceclocker.config.invalid')
                                : t('common.error.unexpected'),
                        )
                    } else {
                        feedback.success(
                            t('event.competition.execution.raceclocker.config.saved'),
                        )
                        onClose()
                    }
                }}>
                <DialogContent>
                    <Stack spacing={4}>
                        <Alert variant={'outlined'} severity={'info'}>
                            <Trans i18nKey={'event.competition.execution.raceclocker.config.hint'} />
                        </Alert>
                        <FormInputText
                            name={'timeTrialResultsUrl'}
                            label={t('event.competition.execution.raceclocker.config.timeTrialUrl')}
                        />
                        <FormInputText
                            name={'heatsResultsUrl'}
                            label={t('event.competition.execution.raceclocker.config.heatsUrl')}
                        />
                        {/* The RaceClocker presets export without a header row, because RaceClocker
                            imports one as a participant. Its column mapper then shows positions
                            instead of names, which is easy to get wrong unnoticed. */}
                        <Alert variant={'outlined'} severity={'info'}>
                            <Trans
                                i18nKey={'event.competition.execution.raceclocker.config.importHint'}
                            />
                        </Alert>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={onClose} disabled={submitting}>
                        <Trans i18nKey={'common.cancel'} />
                    </Button>
                    <SubmitButton submitting={submitting}>
                        <Trans i18nKey={'common.save'} />
                    </SubmitButton>
                </DialogActions>
            </FormContainer>
        </BaseDialog>
    )
}

export default RaceClockerConfigDialog

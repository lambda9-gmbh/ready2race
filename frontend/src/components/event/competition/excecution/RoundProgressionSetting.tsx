import {Box, Stack, Typography} from '@mui/material'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {useTranslation} from 'react-i18next'
import {useState} from 'react'
import {competitionRoute, eventRoute} from '@routes'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getRoundProgressionConfig, updateRoundProgressionConfig} from '@api/sdk.gen.ts'
import {FormInputRadioButtonGroup} from '@components/form/input/FormInputRadioButtonGroup.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {choiceFromDto, requestFromChoice, RoundProgressionChoice} from './roundProgressionForm.ts'

type RoundProgressionForm = {
    choice: RoundProgressionChoice
}

const choiceLabelKeys = {
    INHERIT: 'event.autoCreateFollowingRounds.INHERIT',
    ENABLED: 'event.autoCreateFollowingRounds.ENABLED',
    DISABLED: 'event.autoCreateFollowingRounds.DISABLED',
} as const satisfies Record<RoundProgressionChoice, string>

/**
 * Übersteuert für diesen Wettkampf, ob nach einer beendeten Runde die Paarungen der nächsten Runde
 * automatisch entstehen. Bewusst hier neben dem Knopf für die nächste Runde und nicht in einer
 * eigenen Karte: die Einstellung betrifft genau das, was daneben angestoßen wird.
 *
 * Erben (`INHERIT`) ist der Normalfall - die Veranstaltung entscheidet dann für alle ihre
 * Wettkämpfe auf einmal (siehe `EventDialog.tsx`). Was dabei tatsächlich gilt, kommt fertig
 * gerechnet vom Backend (`effective` in [RoundProgressionConfigDto]) und steht als Hinweistext
 * darunter, damit „Veranstaltung folgen" nicht rätseln lässt, was das gerade bedeutet.
 */
const RoundProgressionSetting = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const [submitting, setSubmitting] = useState(false)
    const [eventDefault, setEventDefault] = useState(false)

    const formContext = useForm<RoundProgressionForm>({defaultValues: {choice: 'INHERIT'}})

    useFetch(signal => getRoundProgressionConfig({signal, path: {eventId, competitionId}}), {
        onResponse: ({data, error}) => {
            if (error) {
                feedback.error(t('common.error.unexpected'))
            } else if (data) {
                formContext.reset({choice: choiceFromDto(data)})
                setEventDefault(data.eventAutoCreateFollowingRounds)
            }
        },
        deps: [eventId, competitionId],
    })

    return (
        <Box>
            <FormContainer
                formContext={formContext}
                onSuccess={async (data: RoundProgressionForm) => {
                    setSubmitting(true)
                    const {error} = await updateRoundProgressionConfig({
                        path: {eventId, competitionId},
                        body: requestFromChoice(data.choice),
                    })
                    setSubmitting(false)

                    if (error) {
                        feedback.error(t('common.error.unexpected'))
                    } else {
                        feedback.success(t('event.competition.execution.roundProgression.saved'))
                    }
                }}>
                <Stack direction={'row'} spacing={2} alignItems={'center'} flexWrap={'wrap'}>
                    <FormInputRadioButtonGroup
                        name={'choice'}
                        label={t('event.autoCreateFollowingRounds.label')}
                        row
                        options={(['INHERIT', 'ENABLED', 'DISABLED'] as const).map(choice => ({
                            id: choice,
                            label: t(choiceLabelKeys[choice]),
                        }))}
                    />
                    <SubmitButton submitting={submitting}>{t('common.save')}</SubmitButton>
                </Stack>
                <Typography variant={'body2'} color={'text.secondary'}>
                    {t('event.autoCreateFollowingRounds.inherited', {
                        value: t(choiceLabelKeys[eventDefault ? 'ENABLED' : 'DISABLED']),
                    })}
                </Typography>
            </FormContainer>
        </Box>
    )
}

export default RoundProgressionSetting

import {Box, Stack, Typography} from '@mui/material'
import {FormContainer, useForm, useWatch} from 'react-hook-form-mui'
import {useTranslation} from 'react-i18next'
import {useState} from 'react'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {
    CompetitionScopeProps,
    useCompetitionScope,
} from '@components/event/competition/excecution/competitionScope.ts'
import {getRoundProgressionConfig, updateRoundProgressionConfig} from '@api/sdk.gen.ts'
import {FormInputRadioButtonGroup} from '@components/form/input/FormInputRadioButtonGroup.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {
    choiceFromDto,
    effectiveFromChoice,
    requestFromChoice,
    RoundProgressionChoice,
} from './roundProgressionForm.ts'

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
 * automatisch entstehen. Bewusst im Zahnrad-Popover neben dem Knopf für die nächste Runde: die
 * Einstellung betrifft genau das, was daneben angestoßen wird — aber sie ist ein Ausnahmefall-
 * Override, und als offenes Formular thronte sie über der ganzen Durchführung (Nutzer-Feedback
 * aus dem Veranstaltungs-Modus, 12.08.2026).
 *
 * Erben (`INHERIT`) ist der Normalfall - die Veranstaltung entscheidet dann für alle ihre
 * Wettkämpfe auf einmal (siehe `EventDialog.tsx`). Das Backend liefert beim Laden bereits fertig
 * gerechnet, was gilt (`effective` in [RoundProgressionConfigDto]) - das gilt aber nur für den
 * geladenen Stand. Sobald der Nutzer eine andere Auswahl trifft, muss der Hinweistext darunter
 * mitziehen, ohne auf Speichern zu warten; dafür wird `effectiveFromChoice` auf die aktuelle
 * Formularauswahl angewandt statt auf den geladenen Wert.
 */
const RoundProgressionSetting = (scope: CompetitionScopeProps) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId, competitionId} = useCompetitionScope(scope)

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

    // useWatch() ohne `name` typisiert das Ergebnis als DeepPartialSkipArrayKey<RoundProgressionForm>;
    // tatsächlich ist jedes Feld belegt, weil defaultValues bereits alle Felder abdeckt.
    const {choice} = useWatch<RoundProgressionForm>({control: formContext.control}) as RoundProgressionForm
    const effective = effectiveFromChoice(choice, eventDefault)

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
                        options={(['INHERIT', 'ENABLED', 'DISABLED'] as const).map(option => ({
                            id: option,
                            label: t(choiceLabelKeys[option]),
                        }))}
                    />
                    <SubmitButton submitting={submitting}>{t('common.save')}</SubmitButton>
                </Stack>
                {/* Was die aktuelle Auswahl bedeutet - live aus dem Formular berechnet, damit der
                    Hinweis der Auswahl folgt und nicht erst nach dem Speichern nachzieht. */}
                <Typography variant={'body2'} color={'text.secondary'}>
                    {t(
                        `event.autoCreateFollowingRounds.effective.${effective ? 'ENABLED' : 'DISABLED'}`,
                    )}
                </Typography>
                {choice === 'INHERIT' && (
                    <Typography variant={'body2'} color={'text.secondary'}>
                        {t('event.autoCreateFollowingRounds.inherited', {
                            value: t(choiceLabelKeys[eventDefault ? 'ENABLED' : 'DISABLED']),
                        })}
                    </Typography>
                )}
            </FormContainer>
        </Box>
    )
}

export default RoundProgressionSetting

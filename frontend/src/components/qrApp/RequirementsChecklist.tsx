import {
    Alert,
    Button,
    DialogActions,
    DialogContent,
    MenuItem,
    Stack,
    TextField,
    Typography,
} from '@mui/material'
import {Trans, useTranslation} from 'react-i18next'
import {
    CheckedParticipantRequirement,
    ParticipantRequirementForEventDto,
    ParticipantScanCompetitionDto,
} from '@api/types.gen.ts'
import {competitionLabel, coveringFulfillment, requirementStatus} from './requirementScope.ts'
import {Block, Check, EditNote} from '@mui/icons-material'
import {useEffect, useState} from 'react'
import BaseDialog from '@components/BaseDialog.tsx'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'

interface RequirementsChecklistProps {
    requirements: ParticipantRequirementForEventDto[]
    checkedRequirements: CheckedParticipantRequirement[]
    pending: boolean
    onRequirementChange: (
        requirementId: string,
        checked: boolean | string,
        namedParticipantId?: string,
    ) => void
    namedParticipantIds: string[]
    /** Die Wettkämpfe, in denen die gescannte Person gemeldet ist. */
    competitions: ParticipantScanCompetitionDto[]
    /** Der Wettkampf, für den gerade abgehakt wird - null heißt "noch keiner gewählt". */
    competitionId: string | null
    onCompetitionChange: (competitionId: string | null) => void
    /** Der heutige Wettkampftag laut Server; null, wenn heute keinem Tag zuzuordnen ist. */
    todayEventDayId: string | null
}

type NoteForm = {
    note: string
}

const defaultNoteValues: NoteForm = {
    note: '',
}

export const RequirementsChecklist = ({
    requirements,
    checkedRequirements,
    pending,
    onRequirementChange,
    namedParticipantIds,
    competitions,
    competitionId,
    onCompetitionChange,
    todayEventDayId,
}: RequirementsChecklistProps) => {
    const {t} = useTranslation()
    const {confirmAction} = useConfirmation()

    const scanContext = {todayEventDayId, competitionId}
    // Die Auswahl erscheint nur, wenn sie etwas ändert: ohne wettkampfbezogene Bedingung ist
    // sie an der Waage nur ein weiterer Knopf, der falsch bedient werden kann.
    const needsCompetition = requirements.some(req => req.perCompetition)

    const [reqForNoteDialog, setReqForNoteDialog] =
        useState<ParticipantRequirementForEventDto | null>(null)
    const showNoteDialog = reqForNoteDialog !== null
    const closeNoteDialog = () => setReqForNoteDialog(null)

    const openNoteDialog = (req: ParticipantRequirementForEventDto) => {
        setReqForNoteDialog(req)
    }

    const formContext = useForm<NoteForm>()

    useEffect(() => {
        if (reqForNoteDialog !== null) {
            formContext.reset(defaultNoteValues)
        }
    }, [reqForNoteDialog])

    return (
        <Stack spacing={1} width={'100%'}>
            <Typography variant="h6">
                {t('participantRequirement.participantRequirements')}
            </Typography>

            {pending && <Typography>{t('qrParticipant.loading') as string}</Typography>}

            {requirements.length === 0 && !pending && (
                <Typography>{t('qrParticipant.noRequirements') as string}</Typography>
            )}

            {needsCompetition && competitions.length > 0 && (
                <TextField
                    select
                    fullWidth
                    size="medium"
                    label={t('qrParticipant.competitionLabel')}
                    helperText={t('qrParticipant.competitionHelp')}
                    value={competitionId ?? ''}
                    onChange={e => onCompetitionChange(e.target.value || null)}>
                    {competitions.map(competition => (
                        <MenuItem key={competition.id} value={competition.id}>
                            {competitionLabel(competition)}
                        </MenuItem>
                    ))}
                </TextField>
            )}

            {needsCompetition && competitions.length === 0 && !pending && (
                <Alert severity="warning">{t('qrParticipant.noCompetitions')}</Alert>
            )}

            {requirements.some(req => req.perEventDay) && todayEventDayId === null && !pending && (
                <Alert severity="warning">{t('qrParticipant.noEventDayToday')}</Alert>
            )}

            {requirements.map(req => {
                const fulfillment = coveringFulfillment(
                    req.id,
                    {perEventDay: req.perEventDay, perCompetition: req.perCompetition},
                    checkedRequirements,
                    scanContext,
                )
                const checked = fulfillment !== undefined
                // Ohne gewählten Wettkampf landete die Bestätigung ohne Bezug und deckte damit
                // keinen Lauf ab - das sieht an der Waage aus wie erledigt und ist es nicht.
                const blocked = req.perCompetition && competitionId === null
                return (
                    <Stack
                        key={req.id}
                        direction={'row'}
                        alignItems={'center'}
                        spacing={3}>
                        <Stack direction={'row'} spacing={2}>
                            {checked ? (
                                <>
                                    <Button variant={'outlined'} sx={{visibility: 'hidden'}}>
                                        <EditNote />
                                    </Button>
                                    <Button
                                        variant={'outlined'}
                                        disabled={pending}
                                        onClick={() =>
                                            confirmAction(
                                                () =>
                                                    onRequirementChange(
                                                        req.id,
                                                        false,
                                                        req.requirements?.find(npReq =>
                                                            namedParticipantIds.some(
                                                                np => np === npReq.id,
                                                            ),
                                                        )?.id,
                                                    ),
                                                {
                                                    content: t(
                                                        'event.participantRequirement.confirmDelete.content',
                                                    ),
                                                    okText: t(
                                                        'event.participantRequirement.confirmDelete.ok',
                                                    ),
                                                    cancelText: t(
                                                        'event.participantRequirement.confirmDelete.cancel',
                                                    ),
                                                    buttonsSX: {minWidth: 80},
                                                },
                                            )
                                        }>
                                        <Block color={'error'} />
                                    </Button>
                                </>
                            ) : (
                                <>
                                    <Button
                                        variant={'outlined'}
                                        disabled={blocked}
                                        onClick={() => openNoteDialog(req)}>
                                        <EditNote />
                                    </Button>
                                    <Button
                                        variant={'outlined'}
                                        sx={{color: 'green'}}
                                        disabled={pending || blocked}
                                        onClick={() =>
                                            onRequirementChange(
                                                req.id,
                                                true,
                                                req.requirements?.find(npReq =>
                                                    namedParticipantIds.some(np => np === npReq.id),
                                                )?.id,
                                            )
                                        }>
                                        <Check />
                                    </Button>
                                </>
                            )}
                        </Stack>
                        <Stack sx={{minWidth: 0}}>
                            <Typography>{req.name}</Typography>
                            {/* Der Stand je Wettkampf (und Tag) direkt an der Bedingung: Wer in
                                zwei Wettkämpfen startet, muss zweimal auf die Waage, und die
                                Station soll das sehen, ohne die Auswahl durchzuklicken. Bei einer
                                Bedingung ohne Wettkampfbezug bleibt es bei der einen Zeile - dort
                                sagt schon das Häkchen alles, deshalb entfällt sie. */}
                            {req.perCompetition &&
                                requirementStatus(
                                    req.id,
                                    {
                                        perEventDay: req.perEventDay,
                                        perCompetition: req.perCompetition,
                                    },
                                    checkedRequirements,
                                    competitions,
                                    todayEventDayId,
                                ).map(entry => (
                                    <Stack
                                        key={entry.competitionId}
                                        direction={'row'}
                                        spacing={0.5}
                                        alignItems={'center'}>
                                        {entry.fulfilled ? (
                                            <Check color={'success'} sx={{fontSize: 16}} />
                                        ) : (
                                            <Block color={'disabled'} sx={{fontSize: 16}} />
                                        )}
                                        <Typography
                                            variant={'caption'}
                                            color={
                                                entry.fulfilled
                                                    ? 'success.main'
                                                    : 'text.secondary'
                                            }
                                            sx={{
                                                fontWeight:
                                                    entry.competitionId === competitionId
                                                        ? 700
                                                        : 400,
                                            }}>
                                            {entry.competitionLabel}
                                            {' — '}
                                            {entry.fulfilled
                                                ? t('qrParticipant.status.done')
                                                : t('qrParticipant.status.open')}
                                            {entry.note ? ` (${entry.note})` : ''}
                                        </Typography>
                                    </Stack>
                                ))}
                            {/* Wofür das Häkchen gilt, steht an der Zeile selbst: an der Waage
                                wird eine Person nacheinander für mehrere Wettkämpfe gewogen,
                                und "abgehakt" allein sagt dann zu wenig. */}
                            {(req.perCompetition || req.perEventDay) && (
                                <Typography variant={'caption'} color={'text.secondary'}>
                                    {req.perCompetition && req.perEventDay
                                        ? t('qrParticipant.scope.perCompetitionAndDay')
                                        : req.perCompetition
                                          ? t('qrParticipant.scope.perCompetition')
                                          : t('qrParticipant.scope.perEventDay')}
                                </Typography>
                            )}
                            {blocked && (
                                <Typography variant={'caption'} color={'warning.main'}>
                                    {t('qrParticipant.chooseCompetitionFirst')}
                                </Typography>
                            )}
                            {checked && fulfillment?.note != null && fulfillment.note !== '' && (
                                <Typography variant={'caption'} color={'text.secondary'}>
                                    {fulfillment.note}
                                </Typography>
                            )}
                        </Stack>
                    </Stack>
                )
            })}

            <BaseDialog open={showNoteDialog} onClose={closeNoteDialog}>
                <FormContainer
                    formContext={formContext}
                    onSuccess={formData => {
                        closeNoteDialog()
                        onRequirementChange(
                            reqForNoteDialog!.id,
                            formData.note,
                            reqForNoteDialog!.requirements?.find(npReq =>
                                namedParticipantIds.some(np => np === npReq.id),
                            )?.id,
                        )
                    }}>
                    <DialogContent>
                        <FormInputText
                            name={'note'}
                            label={t('event.participantRequirement.checkedNote')}
                        />
                    </DialogContent>
                    <DialogActions>
                        <Button onClick={closeNoteDialog}>
                            <Trans i18nKey={'common.cancel'} />
                        </Button>
                        <SubmitButton submitting={false}>
                            <Trans i18nKey={'event.participantRequirement.approve'} />
                        </SubmitButton>
                    </DialogActions>
                </FormContainer>
            </BaseDialog>
        </Stack>
    )
}

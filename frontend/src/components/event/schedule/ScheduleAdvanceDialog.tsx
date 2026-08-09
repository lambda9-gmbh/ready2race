import {useEffect, useMemo, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {FormContainer, useForm, useWatch} from 'react-hook-form-mui'
import {
    Alert,
    Button,
    DialogActions,
    DialogContent,
    DialogTitle,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material'
import {format} from 'date-fns'
import BaseDialog from '@components/BaseDialog.tsx'
import {FormInputSelect} from '@components/form/input/FormInputSelect.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {advanceAfterSkippedSlot} from '@api/sdk.gen.ts'
import {EventScheduleSlotDto} from '@api/types.gen.ts'
import {useFeedback} from '@utils/hooks.ts'
import {advanceOffer, buildShiftPreviewRows, slotLabel} from './common.ts'
import {ScheduleErrorText, advanceErrorText} from './scheduleError.ts'

type AdvanceForm = {
    targetSlotId: string
}

type Props = {
    eventId: string
    open: boolean
    onClose: () => void
    reloadData: () => void
    /** Der eben entfallene Slot — Ausgangspunkt und Quelle des Delta. */
    skippedSlot: EventScheduleSlotDto | undefined
    slots: EventScheduleSlotDto[]
}

/**
 * Das Angebot nach einer Absage: Soll der Zeitplan in die frei gewordene Zeit nachrücken, und bis
 * wohin? Bewusst ein eigener Dialog neben dem Verschieben-Werkzeug, obwohl beide dieselbe Vorschau
 * zeigen — hier gibt es nur eine Entscheidung zu treffen (den Bis-Slot), Startpunkt und Minutenzahl
 * stehen mit der Absage schon fest. Wer den Dialog schließt, lässt den Zeitplan so, wie die Absage
 * ihn hinterlassen hat; das Vorziehen ist ein Angebot, keine Folge.
 */
const ScheduleAdvanceDialog = ({eventId, open, onClose, reloadData, skippedSlot, slots}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const formContext = useForm<AdvanceForm>({defaultValues: {targetSlotId: ''}})

    const [previewRows, setPreviewRows] = useState<ReturnType<typeof buildShiftPreviewRows> | null>(
        null,
    )
    const [previewSnapshot, setPreviewSnapshot] = useState<string | null>(null)
    const [previewError, setPreviewError] = useState<ScheduleErrorText | null>(null)
    const [previewing, setPreviewing] = useState(false)
    const [applying, setApplying] = useState(false)

    useEffect(() => {
        if (open) {
            formContext.reset({targetSlotId: ''})
            setPreviewRows(null)
            setPreviewSnapshot(null)
            setPreviewError(null)
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open])

    const watched = useWatch({control: formContext.control})

    const offer = useMemo(
        () => (skippedSlot ? advanceOffer(slots, skippedSlot) : null),
        [slots, skippedSlot],
    )

    const targetOptions = useMemo(
        () =>
            (offer?.targets ?? []).map(s => ({
                id: s.id,
                label: `${format(new Date(s.startTime), t('format.time'))} – ${slotLabel(s)}`,
            })),
        [offer, t],
    )

    // Wie im Verschieben-Dialog: jede Änderung nach der Vorschau entwertet sie, "Anwenden" bleibt
    // dann gesperrt, bis erneut vorgeschaut wurde.
    const previewStale = previewSnapshot !== null && previewSnapshot !== JSON.stringify(watched)

    const errorContext = useMemo(
        () => ({
            slotName: (slotId: string) => {
                const slot = slots.find(s => s.id === slotId)
                return slot ? slotLabel(slot) : undefined
            },
            formatTime: (isoDateTime: string) => format(new Date(isoDateTime), t('format.datetime')),
        }),
        [slots, t],
    )

    const showError = (error: {status: {value: number}} & Parameters<typeof advanceErrorText>[0]) => {
        // 409 (nicht abgesagt) und 422 (kein Delta, unbrauchbarer Bis-Slot) sind beide fachliche
        // Ablehnungen mit eigenem Text — nur alles Übrige ist ein echter Ausfall.
        if (error.status.value === 409 || error.status.value === 422) {
            setPreviewError(advanceErrorText(error, errorContext))
        } else {
            feedback.error(t('common.error.unexpected'))
        }
    }

    const runPreview = async (data: AdvanceForm) => {
        if (!skippedSlot) {
            return
        }
        setPreviewing(true)
        setPreviewError(null)
        const {data: result, error} = await advanceAfterSkippedSlot({
            path: {eventId, slotId: skippedSlot.id},
            body: {targetSlotId: data.targetSlotId, dryRun: true},
        })
        setPreviewing(false)
        if (error) {
            setPreviewRows(null)
            setPreviewSnapshot(null)
            showError(error)
        } else if (result) {
            setPreviewRows(buildShiftPreviewRows(result.entries, slots))
            setPreviewSnapshot(JSON.stringify(data))
        }
    }

    const handleApply = async () => {
        if (!skippedSlot || !previewRows || previewStale) {
            return
        }
        setApplying(true)
        const {error} = await advanceAfterSkippedSlot({
            path: {eventId, slotId: skippedSlot.id},
            body: {targetSlotId: formContext.getValues().targetSlotId, dryRun: false},
        })
        setApplying(false)
        if (error) {
            // Zwischen Vorschau und Anwenden kann sich der Zeitplan geändert haben — dieselbe
            // Ablehnung trifft dann erneut zu und verdient denselben Text.
            setPreviewRows(null)
            setPreviewSnapshot(null)
            showError(error)
        } else {
            feedback.success(
                t('event.schedule.advance.success', {minutes: offer?.deltaMinutes ?? 0}),
            )
            reloadData()
            onClose()
        }
    }

    const canApply = previewRows !== null && !previewStale && !applying

    return (
        <BaseDialog open={open} onClose={onClose} maxWidth={'sm'}>
            <DialogTitle>{t('event.schedule.advance.title')}</DialogTitle>
            <FormContainer formContext={formContext} onSuccess={runPreview}>
                <DialogContent>
                    <Stack spacing={3}>
                        {skippedSlot && offer && (
                            <>
                                <Typography>
                                    {t('event.schedule.advance.intro', {
                                        label: slotLabel(skippedSlot),
                                        time: format(
                                            new Date(skippedSlot.startTime),
                                            t('format.time'),
                                        ),
                                        minutes: offer.deltaMinutes,
                                    })}
                                </Typography>
                                <FormInputSelect
                                    name={'targetSlotId'}
                                    label={t('event.schedule.advance.targetSlot')}
                                    required
                                    options={targetOptions}
                                />
                                <Typography variant={'body2'} color={'text.secondary'}>
                                    {t('event.schedule.advance.help', {
                                        minutes: offer.deltaMinutes,
                                    })}
                                </Typography>
                            </>
                        )}
                        {previewError && (
                            <Alert severity={'warning'}>
                                {t(previewError.key, previewError.values)}
                            </Alert>
                        )}
                        {previewRows && (
                            <TableContainer>
                                <Table size={'small'}>
                                    <TableHead>
                                        <TableRow>
                                            <TableCell>{t('event.schedule.slot')}</TableCell>
                                            <TableCell>{t('event.schedule.shift.old')}</TableCell>
                                            <TableCell>{t('event.schedule.shift.new')}</TableCell>
                                        </TableRow>
                                    </TableHead>
                                    <TableBody>
                                        {previewRows.map(row => (
                                            <TableRow
                                                key={row.slotId}
                                                sx={
                                                    row.changed
                                                        ? {
                                                              '& .MuiTableCell-root': {
                                                                  fontWeight: 'bold',
                                                              },
                                                          }
                                                        : undefined
                                                }>
                                                <TableCell>{row.label}</TableCell>
                                                <TableCell>
                                                    {format(
                                                        new Date(row.oldStartTime),
                                                        t('format.time'),
                                                    )}
                                                </TableCell>
                                                <TableCell>
                                                    {format(
                                                        new Date(row.newStartTime),
                                                        t('format.time'),
                                                    )}
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            </TableContainer>
                        )}
                        {previewStale && (
                            <Typography color={'text.secondary'} variant={'body2'}>
                                {t('event.schedule.shift.unchanged')}
                            </Typography>
                        )}
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={onClose} disabled={previewing || applying}>
                        {t('event.schedule.advance.decline')}
                    </Button>
                    <SubmitButton submitting={previewing}>
                        {t('event.schedule.shift.preview')}
                    </SubmitButton>
                    <SubmitButton
                        type={'button'}
                        submitting={applying}
                        disabled={!canApply}
                        onClick={handleApply}>
                        {t('event.schedule.advance.apply')}
                    </SubmitButton>
                </DialogActions>
            </FormContainer>
        </BaseDialog>
    )
}

export default ScheduleAdvanceDialog

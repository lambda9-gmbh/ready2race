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
import {FormInputRadioButtonGroup} from '@components/form/input/FormInputRadioButtonGroup.tsx'
import {FormInputSelect} from '@components/form/input/FormInputSelect.tsx'
import FormInputDateTime from '@components/form/input/FormInputDateTime.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {shiftEventSchedule} from '@api/sdk.gen.ts'
import {EventScheduleSlotDto, ShiftMode, ShiftScheduleRequest} from '@api/types.gen.ts'
import {useFeedback} from '@utils/hooks.ts'
import {buildShiftPreviewRows, defaultFromSlotId, slotLabel, slotsAfter} from './common.ts'
import {ScheduleErrorText, shiftErrorText} from './scheduleError.ts'

type ShiftForm = {
    fromSlotId: string
    mode: ShiftMode
    minutes: number | null
    newTime: string
    targetSlotId: string
}

type Props = {
    eventId: string
    open: boolean
    onClose: () => void
    reloadData: () => void
    slots: EventScheduleSlotDto[]
}

const blankValues = (slots: EventScheduleSlotDto[]): ShiftForm => ({
    fromSlotId: defaultFromSlotId(slots) ?? '',
    mode: 'PLUS_MINUTES',
    minutes: null,
    newTime: new Date().toLocaleString(),
    targetSlotId: '',
})

// Feldkombination pro Modus spiegelt die Backend-Validierung (ShiftScheduleRequest.validate):
// PLUS_MINUTES nur minutes, SET_TIME nur newTime, COMPRESS_TO_TARGET targetSlotId + minutes
// (die Alternative "newTime" für COMPRESS_TO_TARGET bräuchte ein viertes Eingabefeld nur für
// diesen einen Fall - die Minutenzahl deckt den Standardfall "verzögert um X, aufholen bis Y" ab).
const toRequest = (form: ShiftForm, dryRun: boolean): ShiftScheduleRequest => ({
    fromSlotId: form.fromSlotId,
    mode: form.mode,
    minutes: form.mode === 'PLUS_MINUTES' || form.mode === 'COMPRESS_TO_TARGET' ? form.minutes : null,
    newTime: form.mode === 'SET_TIME' ? form.newTime : null,
    targetSlotId: form.mode === 'COMPRESS_TO_TARGET' ? form.targetSlotId : null,
    dryRun,
})

const ScheduleShiftDialog = ({eventId, open, onClose, reloadData, slots}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const formContext = useForm<ShiftForm>({defaultValues: blankValues(slots)})

    const [previewRows, setPreviewRows] = useState<ReturnType<typeof buildShiftPreviewRows> | null>(
        null,
    )
    const [previewSnapshot, setPreviewSnapshot] = useState<string | null>(null)
    const [previewError, setPreviewError] = useState<ScheduleErrorText | null>(null)
    const [previewing, setPreviewing] = useState(false)
    const [applying, setApplying] = useState(false)

    useEffect(() => {
        if (open) {
            formContext.reset(blankValues(slots))
            setPreviewRows(null)
            setPreviewSnapshot(null)
            setPreviewError(null)
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open])

    const mode = useWatch({control: formContext.control, name: 'mode'})
    const fromSlotId = useWatch({control: formContext.control, name: 'fromSlotId'})
    const watched = useWatch({control: formContext.control})

    const slotOptions = useMemo(
        () =>
            slots.map(s => ({
                id: s.id,
                label: `${format(new Date(s.startTime), t('format.time'))} – ${s.name ?? s.matchName ?? s.roundName ?? s.competitionName ?? ''}`,
            })),
        [slots, t],
    )

    const targetOptions = useMemo(
        () =>
            slotsAfter(slots, fromSlotId).map(s => ({
                id: s.id,
                label: `${format(new Date(s.startTime), t('format.time'))} – ${s.name ?? s.matchName ?? s.roundName ?? s.competitionName ?? ''}`,
            })),
        [slots, fromSlotId, t],
    )

    // Vorschau gilt nur so lange als gültig, wie sich seit dem letzten Vorschau-Aufruf kein
    // Formularfeld geändert hat - jede Änderung "entwertet" sie, "Anwenden" bleibt dann gesperrt.
    const previewStale = previewSnapshot !== null && previewSnapshot !== JSON.stringify(watched)

    // Server-Meldungen nennen Slot und Zeitpunkt nur als ID bzw. ISO-Zeit; lesbar macht sie erst
    // die Slot-Liste, die dieser Dialog ohnehin hat. Ein Slot, der über den Renntag hinausrutscht,
    // bekommt bewusst Datum UND Uhrzeit - "01:15" allein verschwiege den Folgetag.
    const errorContext = useMemo(
        () => ({
            slotName: (slotId: string) => {
                const slot = slots.find(s => s.id === slotId)
                return slot ? slotLabel(slot) : undefined
            },
            formatTime: (isoDateTime: string) =>
                format(new Date(isoDateTime), t('format.datetime')),
        }),
        [slots, t],
    )

    const runPreview = async (data: ShiftForm) => {
        setPreviewing(true)
        setPreviewError(null)
        const {data: result, error} = await shiftEventSchedule({
            path: {eventId},
            body: toRequest(data, true),
        })
        setPreviewing(false)
        if (error) {
            setPreviewRows(null)
            setPreviewSnapshot(null)
            if (error.status.value === 422) {
                setPreviewError(shiftErrorText(error, errorContext))
            } else {
                feedback.error(t('common.error.unexpected'))
            }
        } else if (result) {
            setPreviewRows(buildShiftPreviewRows(result.entries, slots))
            setPreviewSnapshot(JSON.stringify(data))
        }
    }

    const handleApply = async () => {
        if (!previewRows || previewStale) {
            return
        }
        setApplying(true)
        const {error} = await shiftEventSchedule({
            path: {eventId},
            body: toRequest(formContext.getValues(), false),
        })
        setApplying(false)
        if (error) {
            // Zwischen Vorschau und Anwenden kann sich der Zeitplan geändert haben (Zeitnahme,
            // zweiter Nutzer) - dann trifft dieselbe Ablehnung wie in der Vorschau zu und verdient
            // dieselbe Meldung, statt in einem pauschalen "unerwarteter Fehler" zu verschwinden.
            if (error.status.value === 422) {
                setPreviewRows(null)
                setPreviewSnapshot(null)
                setPreviewError(shiftErrorText(error, errorContext))
            } else {
                feedback.error(t('common.error.unexpected'))
            }
        } else {
            reloadData()
            onClose()
        }
    }

    const canApply = previewRows !== null && !previewStale && !applying

    return (
        <BaseDialog open={open} onClose={onClose} maxWidth={'sm'}>
            <DialogTitle>{t('event.schedule.shift.title')}</DialogTitle>
            <FormContainer formContext={formContext} onSuccess={runPreview}>
                <DialogContent>
                    <Stack spacing={3}>
                        <FormInputSelect
                            name={'fromSlotId'}
                            label={t('event.schedule.shift.fromSlot')}
                            required
                            options={slotOptions}
                        />
                        <FormInputRadioButtonGroup
                            name={'mode'}
                            label={t('event.schedule.shift.mode')}
                            options={[
                                {id: 'PLUS_MINUTES', label: t('event.schedule.shift.modePlus')},
                                {id: 'SET_TIME', label: t('event.schedule.shift.modeSetTime')},
                                {id: 'COMPRESS_TO_TARGET', label: t('event.schedule.shift.modeCompress')},
                            ]}
                        />
                        <Typography variant={'body2'} color={'text.secondary'}>
                            {mode === 'PLUS_MINUTES' && t('event.schedule.shift.help.plus')}
                            {mode === 'SET_TIME' && t('event.schedule.shift.help.setTime')}
                            {mode === 'COMPRESS_TO_TARGET' && t('event.schedule.shift.help.compress')}
                        </Typography>
                        {(mode === 'PLUS_MINUTES' || mode === 'COMPRESS_TO_TARGET') && (
                            <FormInputNumber
                                name={'minutes'}
                                label={t(
                                    mode === 'COMPRESS_TO_TARGET'
                                        ? 'event.schedule.shift.delayMinutes'
                                        : 'event.schedule.shift.minutes',
                                )}
                                required
                                transform={{
                                    output: value =>
                                        value.target.value !== '' ? Number(value.target.value) : null,
                                }}
                            />
                        )}
                        {mode === 'SET_TIME' && (
                            <FormInputDateTime
                                required
                                name={'newTime'}
                                label={t('event.schedule.shift.newTime')}
                            />
                        )}
                        {mode === 'COMPRESS_TO_TARGET' && (
                            <FormInputSelect
                                name={'targetSlotId'}
                                label={t('event.schedule.shift.targetSlot')}
                                required
                                disabled={targetOptions.length === 0}
                                options={targetOptions}
                            />
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
                                                    {format(new Date(row.oldStartTime), t('format.time'))}
                                                </TableCell>
                                                <TableCell>
                                                    {format(new Date(row.newStartTime), t('format.time'))}
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
                        {t('common.cancel')}
                    </Button>
                    <SubmitButton submitting={previewing}>
                        {t('event.schedule.shift.preview')}
                    </SubmitButton>
                    <SubmitButton
                        type={'button'}
                        submitting={applying}
                        disabled={!canApply}
                        onClick={handleApply}>
                        {t('event.schedule.shift.apply')}
                    </SubmitButton>
                </DialogActions>
            </FormContainer>
        </BaseDialog>
    )
}

export default ScheduleShiftDialog

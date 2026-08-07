import {useEffect, useRef, useState} from 'react'
import {useTranslation} from 'react-i18next'
import {
    Alert,
    Button,
    Chip,
    DialogActions,
    DialogContent,
    DialogTitle,
    Link,
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
import SelectFileButton from '@components/SelectFileButton.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {downloadEventScheduleImportTemplate, importEventSchedule} from '@api/sdk.gen.ts'
import {EventScheduleSlotDto, ImportRowResultDto} from '@api/types.gen.ts'
import {useFeedback} from '@utils/hooks.ts'
import {hasBlockingImportRows, hasRunningOrFinishedSlots, importRowChipColor} from './common.ts'
import {ScheduleErrorText, importErrorText} from './scheduleError.ts'

type Props = {
    eventId: string
    open: boolean
    onClose: () => void
    reloadData: () => void
    slots: EventScheduleSlotDto[]
}

// Text-Spalte der Vorschau: Wettkampf- und Lauf-Text kommen als getrennte Felder vom Backend
// (siehe ImportRowResultDto), competitionText kann bei freien Zeilen fehlen.
const rowText = (row: ImportRowResultDto): string =>
    [row.competitionText, row.laufText].filter(Boolean).join(' – ')

const rowLabel = (row: ImportRowResultDto, t: (key: string, options?: object) => string): string => {
    switch (row.status) {
        case 'LINKED':
            return t('event.schedule.importDialog.rowLinked', {target: row.targetLabel ?? ''})
        case 'FREE':
            return t('event.schedule.importDialog.rowFree')
        case 'AMBIGUOUS':
            return t('event.schedule.importDialog.rowAmbiguous')
        case 'DUPLICATE':
        default:
            return t('event.schedule.importDialog.rowDuplicate')
    }
}

const ScheduleImportDialog = ({eventId, open, onClose, reloadData, slots}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const downloadRef = useRef<HTMLAnchorElement>(null)

    const [file, setFile] = useState<File | null>(null)
    const [rows, setRows] = useState<ImportRowResultDto[] | null>(null)
    const [previewError, setPreviewError] = useState<ScheduleErrorText | null>(null)
    const [previewing, setPreviewing] = useState(false)
    const [applying, setApplying] = useState(false)

    useEffect(() => {
        if (open) {
            setFile(null)
            setRows(null)
            setPreviewError(null)
        }
    }, [open])

    // Vorschau läuft automatisch nach jeder Dateiauswahl - eine neu gewählte Datei entwertet die
    // vorherige Vorschau sofort (rows wird zurückgesetzt), bis die neue Antwort da ist.
    const runPreview = async (selected: File) => {
        setPreviewing(true)
        setPreviewError(null)
        setRows(null)
        const {data, error} = await importEventSchedule({
            path: {eventId},
            body: {file: selected, dryRun: true},
        })
        setPreviewing(false)
        if (error) {
            if (error.status.value === 422) {
                setPreviewError(importErrorText(error))
            } else {
                feedback.error(t('common.error.unexpected'))
            }
        } else if (data) {
            setRows(data.rows)
        }
    }

    const handleFileSelected = (selected: File) => {
        setFile(selected)
        void runPreview(selected)
    }

    // Die Beispieldatei kommt aus dem Backend, damit ihre Kopfzeile nicht von der abweichen kann,
    // die der Import liest - siehe ScheduleImportTemplate.
    const handleDownloadTemplate = async () => {
        const {data, error} = await downloadEventScheduleImportTemplate({path: {eventId}})
        const anchor = downloadRef.current

        if (error || data === undefined || !anchor) {
            feedback.error(t('event.schedule.importDialog.templateError'))
            return
        }

        anchor.href = URL.createObjectURL(data)
        anchor.download = 'zeitstrahl-import-beispiel.xlsx'
        anchor.click()
        anchor.href = ''
        anchor.download = ''
    }

    // Der scharfe Import ist derselbe Request mit dryRun: false - siehe EventScheduleService.
    // importSchedule: Duplikate, die die Vorschau schon markiert hat, würden hier ohnehin 422
    // auslösen, der Button bleibt also zusätzlich clientseitig gesperrt (hasBlockingImportRows).
    const handleApply = async () => {
        if (!file || !rows || hasBlockingImportRows(rows)) {
            return
        }
        setApplying(true)
        const {error} = await importEventSchedule({
            path: {eventId},
            body: {file, dryRun: false},
        })
        setApplying(false)
        if (error) {
            if (error.status.value === 422) {
                setPreviewError(importErrorText(error))
            } else {
                feedback.error(t('common.error.unexpected'))
            }
        } else {
            reloadData()
            onClose()
        }
    }

    const canApply = rows !== null && !hasBlockingImportRows(rows) && !previewing && !applying

    return (
        <BaseDialog open={open} onClose={onClose} maxWidth={'md'}>
            <DialogTitle>{t('event.schedule.importDialog.title')}</DialogTitle>
            <DialogContent>
                <Stack spacing={3}>
                    <Alert variant={'outlined'} severity={'info'}>
                        {t('event.schedule.importDialog.replacesAll')}
                    </Alert>
                    {hasRunningOrFinishedSlots(slots) && (
                        <Alert variant={'outlined'} severity={'warning'}>
                            {t('event.schedule.importDialog.runningWarning')}
                        </Alert>
                    )}
                    <Stack direction={'row'} spacing={2} alignItems={'center'}>
                        <SelectFileButton
                            variant={'outlined'}
                            onSelected={handleFileSelected}
                            accept={'.xlsx'}>
                            {t('event.schedule.importDialog.choose')}
                        </SelectFileButton>
                        {file && <Typography>{file.name}</Typography>}
                    </Stack>
                    <Stack direction={'row'} spacing={1} alignItems={'center'}>
                        <Typography variant={'body2'} color={'text.secondary'}>
                            {t('event.schedule.importDialog.templateHint')}
                        </Typography>
                        <Button size={'small'} onClick={handleDownloadTemplate}>
                            {t('event.schedule.importDialog.template')}
                        </Button>
                        <Link ref={downloadRef} display={'none'}></Link>
                    </Stack>
                    {previewError && (
                        <Alert severity={'error'}>{t(previewError.key, previewError.values)}</Alert>
                    )}
                    {rows && (
                        <TableContainer>
                            <Table size={'small'}>
                                <TableHead>
                                    <TableRow>
                                        <TableCell>{t('event.schedule.importDialog.row')}</TableCell>
                                        <TableCell>{t('event.schedule.importDialog.time')}</TableCell>
                                        <TableCell>{t('event.schedule.importDialog.text')}</TableCell>
                                        <TableCell>{t('event.schedule.status')}</TableCell>
                                    </TableRow>
                                </TableHead>
                                <TableBody>
                                    {rows.map(row => (
                                        <TableRow key={row.rowNumber}>
                                            <TableCell>{row.rowNumber}</TableCell>
                                            <TableCell>
                                                {format(new Date(row.startTime), t('format.time'))}
                                            </TableCell>
                                            <TableCell>{rowText(row)}</TableCell>
                                            <TableCell>
                                                <Chip
                                                    size={'small'}
                                                    label={rowLabel(row, t)}
                                                    color={importRowChipColor(row.status)}
                                                />
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        </TableContainer>
                    )}
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={applying}>
                    {t('common.cancel')}
                </Button>
                <SubmitButton
                    type={'button'}
                    submitting={applying || previewing}
                    disabled={!canApply}
                    onClick={handleApply}>
                    {t('event.schedule.importDialog.apply')}
                </SubmitButton>
            </DialogActions>
        </BaseDialog>
    )
}

export default ScheduleImportDialog

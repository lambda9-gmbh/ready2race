import {
    Alert,
    Button,
    Checkbox,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    FormControl,
    FormControlLabel,
    FormLabel,
    Link as MuiLink,
    Radio,
    RadioGroup,
    Stack,
    Typography,
} from '@mui/material'
import {useEffect, useRef, useState} from 'react'
import {Trans, useTranslation} from 'react-i18next'
import BaseDialog from '@components/BaseDialog.tsx'
import LoadingButton from '@components/form/LoadingButton.tsx'
import {downloadResultList} from '@api/sdk.gen.ts'
import {getFilename} from '@utils/helpers.ts'
import {ResultListErrorKey, resultListErrorKey} from '@components/awardCeremony/resultListError.ts'
import {
    matchingPreset,
    presetOptions,
    ResultListOptions,
    ResultListPreset,
    resultListQuery,
} from '@components/awardCeremony/resultListOptions.ts'

type Props = {
    open: boolean
    onClose: () => void
    eventId: string
    competitionId?: string
}

/**
 * „Ergebnisliste drucken": dieselbe Datenbasis wie der Siegerehrungsbogen, aber mit wählbaren
 * Bestandteilen — zum Aushängen groß gesetzt oder im Pult-Format. Die Presets belegen die Häkchen
 * nur vor; verschickt werden immer die einzelnen Schalter. Ohne `competitionId` umfasst die Liste
 * die ganze Veranstaltung, mit ihr nur den einen Wettkampf der Platzierungsseite.
 */
const ResultListDialog = ({open, onClose, eventId, competitionId}: Props) => {
    const {t} = useTranslation()

    const [options, setOptions] = useState<ResultListOptions>(presetOptions.posting)
    const [submitting, setSubmitting] = useState(false)
    const [errorKey, setErrorKey] = useState<ResultListErrorKey | null>(null)
    const downloadRef = useRef<HTMLAnchorElement>(null)

    useEffect(() => {
        if (open) {
            setErrorKey(null)
        }
    }, [open])

    // Welche Vorlage die Häkchen gerade abbilden - `null` heißt „eigene Zusammenstellung", und
    // dann ist ehrlicherweise kein Radio markiert, statt eine Vorlage zu behaupten.
    const preset = matchingPreset(options)

    const toggle = (key: keyof ResultListOptions) =>
        setOptions(prev => ({...prev, [key]: !prev[key]}))

    const handleClose = () => {
        setErrorKey(null)
        onClose()
    }

    const handleSubmit = async () => {
        setSubmitting(true)
        setErrorKey(null)

        const {
            data: pdf,
            error,
            response,
        } = await downloadResultList({
            path: {eventId},
            query: {competitionId, ...resultListQuery(options)},
        })

        setSubmitting(false)

        if (error) {
            setErrorKey(resultListErrorKey(error))
            return
        }

        const anchor = downloadRef.current
        if (pdf !== undefined && anchor) {
            anchor.href = URL.createObjectURL(pdf)
            anchor.download = getFilename(response) ?? 'ergebnisliste.pdf'
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }

        handleClose()
    }

    const optionCheckbox = (key: keyof ResultListOptions) => (
        <FormControlLabel
            key={key}
            control={<Checkbox checked={options[key]} onChange={() => toggle(key)} />}
            label={t(`resultList.download.options.${key}`)}
        />
    )

    return (
        <BaseDialog open={open} onClose={handleClose} maxWidth={'sm'}>
            <MuiLink ref={downloadRef} display={'none'}></MuiLink>
            <DialogTitle>
                <Trans i18nKey={'resultList.download.title'} />
            </DialogTitle>
            <DialogContent>
                <Stack spacing={2}>
                    {errorKey !== null && (
                        <Alert severity={'warning'}>
                            <Trans i18nKey={errorKey} />
                        </Alert>
                    )}
                    <Typography variant={'body2'} color={'text.secondary'}>
                        {t('resultList.download.hint')}
                    </Typography>
                    <FormControl>
                        <FormLabel id={'result-list-preset'}>
                            {t('resultList.download.preset.label')}
                        </FormLabel>
                        <RadioGroup
                            row
                            aria-labelledby={'result-list-preset'}
                            // `?? ''` statt einer erzwungenen Vorlage: sobald ein Häkchen von
                            // beiden Vorlagen abweicht, ist keine mehr wahr - und keine markiert.
                            value={preset ?? ''}
                            onChange={event =>
                                setOptions(presetOptions[event.target.value as ResultListPreset])
                            }>
                            <FormControlLabel
                                value={'posting'}
                                control={<Radio />}
                                label={t('resultList.download.preset.posting')}
                            />
                            <FormControlLabel
                                value={'ceremony'}
                                control={<Radio />}
                                label={t('resultList.download.preset.ceremony')}
                            />
                        </RadioGroup>
                    </FormControl>
                    <Divider />
                    <Stack>
                        {optionCheckbox('crew')}
                        {optionCheckbox('times')}
                        {optionCheckbox('podiumOnly')}
                        {optionCheckbox('byRatingCategory')}
                        {optionCheckbox('largePrint')}
                    </Stack>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose} disabled={submitting}>
                    <Trans i18nKey={'common.cancel'} />
                </Button>
                <LoadingButton variant={'contained'} pending={submitting} onClick={handleSubmit}>
                    <Trans i18nKey={'resultList.download.action'} />
                </LoadingButton>
            </DialogActions>
        </BaseDialog>
    )
}

export default ResultListDialog

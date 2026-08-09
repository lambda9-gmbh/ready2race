import {
    Alert,
    Box,
    Button,
    Checkbox,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControlLabel,
    Link as MuiLink,
    Stack,
    Typography,
} from '@mui/material'
import {useEffect, useMemo, useRef, useState} from 'react'
import {Trans, useTranslation} from 'react-i18next'
import BaseDialog from '@components/BaseDialog.tsx'
import LoadingButton from '@components/form/LoadingButton.tsx'
import Throbber from '@components/Throbber.tsx'
import {downloadAwardCeremonySheets, getAwardCeremonies} from '@api/sdk.gen.ts'
import {AwardCeremonyChoiceDto} from '@api/types.gen.ts'
import {useFetch} from '@utils/hooks.ts'
import {getFilename} from '@utils/helpers.ts'
import {
    AwardCeremonyErrorKey,
    awardCeremonyErrorKey,
} from '@components/awardCeremony/awardCeremonyError.ts'
import {
    CeremonyGroup,
    ceremonyKey,
    ceremonyRequestKey,
    groupByCompetition,
    isSingleUncategorized,
} from '@components/awardCeremony/awardCeremonySelection.ts'

type Props = {
    open: boolean
    onClose: () => void
    eventId: string
    competitionId?: string
}

/**
 * Wählt die Ehrungen aus, die als Siegerehrungsbogen gedruckt werden - je Ehrung ein A4-Blatt.
 * Ohne `competitionId` steht die ganze Veranstaltung zur Wahl, mit ihr nur der eine Wettkampf,
 * denn dort wird der Dialog von der Platzierungsseite aus geöffnet.
 */
const AwardCeremonyDialog = ({open, onClose, eventId, competitionId}: Props) => {
    const {t} = useTranslation()

    const [selected, setSelected] = useState<Set<string>>(new Set())
    const [submitting, setSubmitting] = useState(false)
    const [errorKey, setErrorKey] = useState<AwardCeremonyErrorKey | null>(null)
    const downloadRef = useRef<HTMLAnchorElement>(null)

    const {data, pending} = useFetch(signal => getAwardCeremonies({signal, path: {eventId}}), {
        // Die Ehrungen werden bei jedem Aufruf aus der Platzberechnung abgeleitet - bis zur
        // Siegerehrung ändern sich Ergebnisse noch, deshalb wird bei jedem Öffnen neu geladen.
        preCondition: () => open,
        onResponse: ({error}) => {
            if (error) {
                setErrorKey(awardCeremonyErrorKey(error))
            }
        },
        deps: [eventId, open],
    })

    const ceremonies = useMemo<Array<AwardCeremonyChoiceDto>>(
        () =>
            (data ?? []).filter(
                choice => competitionId === undefined || choice.competitionId === competitionId,
            ),
        [data, competitionId],
    )

    const groups = useMemo(() => groupByCompetition(ceremonies), [ceremonies])

    useEffect(() => {
        setSelected(new Set(ceremonies.map(ceremonyKey)))
    }, [ceremonies])

    useEffect(() => {
        if (open) {
            setErrorKey(null)
        }
    }, [open])

    // `pending` ist im ersten Bild nach dem Öffnen noch false - ohne den Zusatz blitzte dort für
    // einen Frame "Es gibt noch keine Ehrungen" auf, bevor der Ladevorgang überhaupt beginnt.
    const loading = pending || (data === null && errorKey === null)

    const allSelected = ceremonies.length > 0 && selected.size === ceremonies.length

    // Genau das, was gleich verschickt wird - und nicht die Größe von `selected`. Die beiden
    // können auseinanderlaufen, solange eine frisch geladene Liste noch nicht mit der Vorauswahl
    // abgeglichen ist; dann stünden in `selected` nur Schlüssel, die es nicht mehr gibt. Eine
    // leere Auswahl bedeutet dem Server "alle Ehrungen drucken", der Knopf dürfte in diesem
    // Moment also gerade nicht bedienbar sein.
    const selection = ceremonies
        .filter(choice => selected.has(ceremonyKey(choice)))
        .map(ceremonyRequestKey)

    const toggle = (choice: AwardCeremonyChoiceDto) => {
        const key = ceremonyKey(choice)
        setSelected(prev => {
            const next = new Set(prev)
            if (!next.delete(key)) {
                next.add(key)
            }
            return next
        })
    }

    const toggleAll = () => {
        setSelected(allSelected ? new Set() : new Set(ceremonies.map(ceremonyKey)))
    }

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
        } = await downloadAwardCeremonySheets({
            path: {eventId},
            body: {selection},
        })

        setSubmitting(false)

        if (error) {
            setErrorKey(awardCeremonyErrorKey(error))
            return
        }

        const anchor = downloadRef.current
        if (pdf !== undefined && anchor) {
            anchor.href = URL.createObjectURL(pdf)
            anchor.download = getFilename(response) ?? 'award_ceremony.pdf'
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }

        handleClose()
    }

    const competitionLabel = (group: CeremonyGroup) =>
        [group.competitionIdentifier, group.competitionShortName].filter(Boolean).join(' · ') +
        ` — ${group.competitionName}`

    const ceremonyLabel = (choice: AwardCeremonyChoiceDto) =>
        `${choice.ratingCategoryName ?? t('awardCeremony.download.withoutCategory')} (${t('awardCeremony.download.boats', {count: choice.awardedTeams})})`

    const checkbox = (choice: AwardCeremonyChoiceDto, label: string) => (
        <FormControlLabel
            key={ceremonyKey(choice)}
            control={
                <Checkbox
                    checked={selected.has(ceremonyKey(choice))}
                    onChange={() => toggle(choice)}
                />
            }
            label={label}
        />
    )

    return (
        <BaseDialog open={open} onClose={handleClose} maxWidth={'sm'}>
            <MuiLink ref={downloadRef} display={'none'}></MuiLink>
            <DialogTitle>
                <Trans i18nKey={'awardCeremony.download.title'} />
            </DialogTitle>
            <DialogContent>
                <Stack spacing={2}>
                    {errorKey !== null && (
                        <Alert severity={'warning'}>
                            <Trans i18nKey={errorKey} />
                        </Alert>
                    )}
                    <Typography variant={'body2'} color={'text.secondary'}>
                        {t('awardCeremony.download.hint')}
                    </Typography>
                    {loading ? (
                        <Throbber />
                    ) : ceremonies.length === 0 ? (
                        // Beim gescheiterten Laden sagt der Alert schon, was los ist - "es gibt
                        // noch keine Ehrungen" wäre daneben eine zweite, falsche Begründung.
                        errorKey === null && (
                            <Typography>{t('awardCeremony.download.empty')}</Typography>
                        )
                    ) : (
                        <>
                            <Box>
                                <Button size={'small'} onClick={toggleAll}>
                                    {allSelected
                                        ? t('awardCeremony.download.deselectAll')
                                        : t('awardCeremony.download.selectAll')}
                                </Button>
                            </Box>
                            <Stack spacing={2}>
                                {groups.map(group =>
                                    isSingleUncategorized(group) ? (
                                        <Box key={group.competitionId}>
                                            {checkbox(
                                                group.ceremonies[0],
                                                `${competitionLabel(group)} (${t('awardCeremony.download.boats', {count: group.ceremonies[0].awardedTeams})})`,
                                            )}
                                        </Box>
                                    ) : (
                                        <Box key={group.competitionId}>
                                            <Typography variant={'subtitle2'}>
                                                {competitionLabel(group)}
                                            </Typography>
                                            <Stack sx={{pl: 2}}>
                                                {group.ceremonies.map(choice =>
                                                    checkbox(choice, ceremonyLabel(choice)),
                                                )}
                                            </Stack>
                                        </Box>
                                    ),
                                )}
                            </Stack>
                        </>
                    )}
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose} disabled={submitting}>
                    <Trans i18nKey={'common.cancel'} />
                </Button>
                <LoadingButton
                    variant={'contained'}
                    pending={submitting}
                    disabled={selection.length === 0}
                    onClick={handleSubmit}>
                    <Trans i18nKey={'awardCeremony.download.action'} />
                </LoadingButton>
            </DialogActions>
        </BaseDialog>
    )
}

export default AwardCeremonyDialog

import {Alert, Button, DialogActions, DialogContent, DialogTitle, Link as MuiLink, Stack, Typography} from '@mui/material'
import {FormContainer, useForm} from 'react-hook-form-mui'
import BaseDialog from '@components/BaseDialog.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {useEffect, useRef, useState} from 'react'
import {Trans, useTranslation} from 'react-i18next'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import FormInputSwitch from '@components/form/input/FormInputSwitch.tsx'
import {FormInputRadioButtonGroup} from '@components/form/input/FormInputRadioButtonGroup.tsx'
import {
    downloadAwardCertificate,
    downloadAwardCertificatesForCompetition,
    downloadAwardCertificatesForEvent,
} from '@api/sdk.gen.ts'
import {getFilename} from '@utils/helpers.ts'
import InlineLink from '@components/InlineLink.tsx'
import {
    CertificateErrorKey,
    awardCertificateErrorKey,
    awardCertificateErrorLinksToConfig,
} from '@components/certificate/certificateError.ts'

type Props = {
    open: boolean
    onClose: () => void
    eventId: string
    competitionId?: string
    registrationId?: string
}

type AwardCertificateForm = {
    format: 'pdf' | 'docx'
    maxPlace: string
    mode: 'PER_ATHLETE' | 'PER_TEAM'
    background: boolean
}

const defaultValues: AwardCertificateForm = {
    format: 'pdf',
    maxPlace: '3',
    mode: 'PER_ATHLETE',
    background: false,
}

/**
 * Triggers the download of one or several award certificates. The same dialog serves all three
 * granularities offered by the backend – it just calls a different client function depending on
 * which ids are given:
 * - only `eventId`: every certificate of the event
 * - `eventId` + `competitionId`: every certificate of that competition
 * - all three ids: a single reprint, for which the backend deliberately ignores `maxPlace`
 */
const AwardCertificateDialog = ({open, onClose, eventId, competitionId, registrationId}: Props) => {
    const {t} = useTranslation()
    const formContext = useForm<AwardCertificateForm>()
    const [submitting, setSubmitting] = useState(false)
    const [errorKey, setErrorKey] = useState<CertificateErrorKey | null>(null)
    const downloadRef = useRef<HTMLAnchorElement>(null)

    const isSingle = registrationId !== undefined

    useEffect(() => {
        if (open) {
            formContext.reset(defaultValues)
            setErrorKey(null)
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open])

    const handleClose = () => {
        setErrorKey(null)
        onClose()
    }

    const handleSubmit = async (formData: AwardCertificateForm) => {
        setSubmitting(true)
        setErrorKey(null)

        const query = {
            format: formData.format,
            mode: formData.mode,
            background: formData.background,
            // The single-certificate download ignores this on the backend anyway – a reprint of
            // e.g. a fifth place must work – so it is left out rather than sent for nothing.
            ...(isSingle ? {} : {maxPlace: Number(formData.maxPlace)}),
        }

        const {data, error, response} =
            registrationId !== undefined && competitionId !== undefined
                ? await downloadAwardCertificate({
                      path: {eventId, competitionId, registrationId},
                      query,
                  })
                : competitionId !== undefined
                  ? await downloadAwardCertificatesForCompetition({
                        path: {eventId, competitionId},
                        query,
                    })
                  : await downloadAwardCertificatesForEvent({path: {eventId}, query})

        setSubmitting(false)

        if (error) {
            // Früher unterschied der Dialog nur nach HTTP-Status: 409 hieß pauschal "keine
            // nutzbare Vorlage" (fehlend ODER unlesbar), 400 pauschal "keine Platzierungen" -
            // auch dann, wenn es sich um ein Challenge-Event handelte, das grundsätzlich keine
            // Siegerurkunden kennt. Jetzt entscheidet der ErrorCode.
            setErrorKey(awardCertificateErrorKey(error))
            return
        }

        const anchor = downloadRef.current
        if (data !== undefined && anchor) {
            anchor.href = URL.createObjectURL(data)
            anchor.download =
                getFilename(response) ??
                `award_certificate${isSingle ? '' : 's'}.${formData.format}`
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }

        handleClose()
    }

    return (
        <BaseDialog open={open} onClose={handleClose} maxWidth={'sm'}>
            <MuiLink ref={downloadRef} display={'none'}></MuiLink>
            <DialogTitle>
                <Trans i18nKey={'awardCertificate.download.title'} />
            </DialogTitle>
            <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                <DialogContent>
                    <Stack spacing={4}>
                        {errorKey !== null && (
                            <Alert severity={'warning'}>
                                <Trans i18nKey={errorKey} />
                                {/* Der Verweis in die Konfiguration hilft nur, wenn dort auch
                                    etwas zu tun ist - bei "keine Platzierungen" wäre er eine
                                    Sackgasse. */}
                                {awardCertificateErrorLinksToConfig(errorKey) && (
                                    <>
                                        {' '}
                                        <InlineLink to={'/config'} search={{tab: 'event-elements'}}>
                                            <Trans
                                                i18nKey={
                                                    'awardCertificate.download.error.missingTemplateLink'
                                                }
                                            />
                                        </InlineLink>
                                    </>
                                )}
                            </Alert>
                        )}
                        <FormInputRadioButtonGroup
                            name={'format'}
                            label={t('awardCertificate.download.format.label')}
                            row
                            options={[
                                {id: 'pdf', label: t('awardCertificate.download.format.pdf')},
                                {id: 'docx', label: t('awardCertificate.download.format.docx')},
                            ]}
                        />
                        {!isSingle && (
                            <FormInputNumber
                                name={'maxPlace'}
                                label={t('awardCertificate.download.maxPlace.label')}
                                min={1}
                                integer
                            />
                        )}
                        <FormInputRadioButtonGroup
                            name={'mode'}
                            label={t('awardCertificate.download.mode.label')}
                            row
                            options={[
                                {
                                    id: 'PER_ATHLETE',
                                    label: t('awardCertificate.download.mode.perAthlete'),
                                },
                                {
                                    id: 'PER_TEAM',
                                    label: t('awardCertificate.download.mode.perTeam'),
                                },
                            ]}
                        />
                        <Stack spacing={1}>
                            <FormInputSwitch
                                name={'background'}
                                label={t('awardCertificate.download.background.label')}
                                horizontal
                                reverse
                            />
                            <Typography variant={'body2'} color={'text.secondary'}>
                                {t('awardCertificate.download.background.hint')}
                            </Typography>
                        </Stack>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleClose} disabled={submitting}>
                        <Trans i18nKey={'common.cancel'} />
                    </Button>
                    <SubmitButton submitting={submitting}>
                        <Trans i18nKey={'awardCertificate.download.action'} />
                    </SubmitButton>
                </DialogActions>
            </FormContainer>
        </BaseDialog>
    )
}

export default AwardCertificateDialog

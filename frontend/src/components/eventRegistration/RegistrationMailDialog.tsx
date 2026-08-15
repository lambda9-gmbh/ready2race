import {
    Alert,
    Box,
    Button,
    Checkbox,
    Chip,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    IconButton,
    Stack,
    TextField,
    Typography,
} from '@mui/material'
import {Delete} from '@mui/icons-material'
import {KeyboardEvent, useCallback, useState} from 'react'
import {useTranslation} from 'react-i18next'
import BaseDialog from '@components/BaseDialog.tsx'
import SelectFileButton from '@components/SelectFileButton.tsx'
import Throbber from '@components/Throbber.tsx'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getRegistrationMailRecipients, sendRegistrationMail} from '@api/sdk.gen.ts'
import {RegistrationMailRecipientDto} from '@api/types.gen.ts'

type Props = {
    open: boolean
    onClose: () => void
    eventId: string
}

/** Ab hier warnt der Dialog: Anhänge werden je Empfänger kopiert. */
const ATTACHMENT_WARNING_BYTES = 5 * 1024 * 1024

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/

const formatSize = (bytes: number) =>
    bytes < 1024 * 1024 ? `${Math.round(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`

const RegistrationMailDialog = ({open, onClose, eventId}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [selected, setSelected] = useState<string[]>([])
    const [addresses, setAddresses] = useState<string[]>([])
    const [addressInput, setAddressInput] = useState('')
    const [addressError, setAddressError] = useState<string | null>(null)
    const [subject, setSubject] = useState('')
    const [body, setBody] = useState('')
    const [files, setFiles] = useState<File[]>([])
    const [confirming, setConfirming] = useState(false)
    const [submitting, setSubmitting] = useState(false)

    const {data, pending} = useFetch(
        signal => getRegistrationMailRecipients({signal, path: {eventId}}),
        {
            // Beim Öffnen sind alle erreichbaren Melder angehakt - der Regelfall ist "an alle".
            onResponse: ({data}) => {
                if (data) {
                    setSelected(
                        data
                            .filter((r: RegistrationMailRecipientDto) => r.email)
                            .map((r: RegistrationMailRecipientDto) => r.registrationId),
                    )
                }
            },
            preCondition: () => open,
            deps: [open, eventId],
        },
    )

    const recipients = data ?? []
    const reachable = recipients.filter(r => r.email)
    const attachmentBytes = files.reduce((sum, file) => sum + file.size, 0)
    const totalRecipients = selected.length + addresses.length

    const toggle = (registrationId: string) =>
        setSelected(prev =>
            prev.includes(registrationId)
                ? prev.filter(id => id !== registrationId)
                : [...prev, registrationId],
        )

    const commitAddress = useCallback(() => {
        const address = addressInput.trim()
        if (address === '') {
            return
        }
        if (!emailPattern.test(address)) {
            setAddressError(t('event.registration.mail.error.invalidAddress', {address}))
            return
        }
        setAddressError(null)
        setAddresses(prev =>
            prev.some(a => a.toLowerCase() === address.toLowerCase()) ? prev : [...prev, address],
        )
        setAddressInput('')
    }, [addressInput, t])

    const onAddressKeyDown = (event: KeyboardEvent) => {
        if (event.key === 'Enter' || event.key === ',') {
            event.preventDefault()
            commitAddress()
        }
    }

    const reset = () => {
        setAddresses([])
        setAddressInput('')
        setAddressError(null)
        setSubject('')
        setBody('')
        setFiles([])
        setConfirming(false)
    }

    const close = () => {
        reset()
        onClose()
    }

    const send = async () => {
        setSubmitting(true)
        // Ohne das try bleibt der Dialog nach einem Netzfehler mit gesperrtem Knopf stehen -
        // der abgewiesene Aufruf käme nie bis zum setSubmitting(false) unten.
        let result
        try {
            result = await sendRegistrationMail({
                path: {eventId},
                body: {
                    request: {
                        subject,
                        body,
                        registrationIds: selected,
                        additionalAddresses: addresses,
                    },
                    files,
                },
            })
        } catch {
            setSubmitting(false)
            setConfirming(false)
            feedback.error(t('common.error.unexpected'))
            return
        }
        const {data, error} = result
        setSubmitting(false)
        setConfirming(false)

        if (error) {
            if (error.status.value === 409) {
                feedback.error(t('event.registration.mail.error.recipientGone'))
            } else {
                feedback.error(t('common.error.unexpected'))
            }
        } else {
            feedback.success(
                t('event.registration.mail.success', {count: data?.enqueued ?? totalRecipients}),
            )
            close()
        }
    }

    const canSend = totalRecipients > 0 && subject.trim() !== '' && body.trim() !== ''

    return (
        <>
            <BaseDialog open={open} onClose={close} maxWidth={'md'}>
                <DialogTitle>{t('event.registration.mail.title')}</DialogTitle>
                <DialogContent dividers>
                    {pending ? (
                        <Throbber />
                    ) : (
                        <Stack spacing={3}>
                            <Box>
                                <Stack
                                    direction={'row'}
                                    justifyContent={'space-between'}
                                    alignItems={'center'}>
                                    <Typography variant={'subtitle1'}>
                                        {t('event.registration.mail.recipients')}
                                    </Typography>
                                    <Button
                                        size={'small'}
                                        onClick={() =>
                                            setSelected(
                                                selected.length === reachable.length
                                                    ? []
                                                    : reachable.map(r => r.registrationId),
                                            )
                                        }>
                                        {selected.length === reachable.length
                                            ? t('common.deselectAll')
                                            : t('common.selectAll')}
                                    </Button>
                                </Stack>
                                <Box sx={{maxHeight: 260, overflowY: 'auto'}}>
                                    {recipients.map(recipient => (
                                        <Stack
                                            key={recipient.registrationId}
                                            direction={'row'}
                                            alignItems={'center'}
                                            spacing={1}>
                                            <Checkbox
                                                size={'small'}
                                                disabled={!recipient.email}
                                                checked={selected.includes(recipient.registrationId)}
                                                onChange={() => toggle(recipient.registrationId)}
                                            />
                                            <Box>
                                                <Typography variant={'body2'}>
                                                    {recipient.clubName}
                                                </Typography>
                                                <Typography
                                                    variant={'caption'}
                                                    color={
                                                        recipient.email
                                                            ? 'text.secondary'
                                                            : 'error'
                                                    }>
                                                    {recipient.email
                                                        ? `${recipient.name} · ${recipient.email}`
                                                        : t('event.registration.mail.noUser')}
                                                </Typography>
                                            </Box>
                                        </Stack>
                                    ))}
                                </Box>
                            </Box>

                            <Divider />

                            <Box>
                                <TextField
                                    fullWidth
                                    size={'small'}
                                    label={t('event.registration.mail.additionalAddresses')}
                                    placeholder={t('event.registration.mail.addressHint')}
                                    value={addressInput}
                                    error={addressError !== null}
                                    helperText={addressError}
                                    onChange={e => setAddressInput(e.target.value)}
                                    onKeyDown={onAddressKeyDown}
                                    onBlur={commitAddress}
                                />
                                {addresses.length > 0 && (
                                    <Stack direction={'row'} flexWrap={'wrap'} gap={1} mt={1}>
                                        {addresses.map(address => (
                                            <Chip
                                                key={address}
                                                label={address}
                                                size={'small'}
                                                onDelete={() =>
                                                    setAddresses(prev =>
                                                        prev.filter(a => a !== address),
                                                    )
                                                }
                                            />
                                        ))}
                                    </Stack>
                                )}
                            </Box>

                            <TextField
                                fullWidth
                                size={'small'}
                                label={t('event.registration.mail.subject')}
                                value={subject}
                                onChange={e => setSubject(e.target.value)}
                            />
                            <TextField
                                fullWidth
                                multiline
                                minRows={8}
                                label={t('event.registration.mail.body')}
                                helperText={t('event.registration.mail.placeholders')}
                                value={body}
                                onChange={e => setBody(e.target.value)}
                            />

                            <Box>
                                <Typography variant={'subtitle1'}>
                                    {t('event.registration.mail.attachments')}
                                </Typography>
                                {files.map((file, index) => (
                                    <Stack
                                        key={`${file.name}-${index}`}
                                        direction={'row'}
                                        alignItems={'center'}
                                        justifyContent={'space-between'}>
                                        <Typography variant={'body2'}>
                                            {file.name} ({formatSize(file.size)})
                                        </Typography>
                                        <IconButton
                                            size={'small'}
                                            onClick={() =>
                                                setFiles(prev => prev.filter((_, i) => i !== index))
                                            }>
                                            <Delete fontSize={'small'} />
                                        </IconButton>
                                    </Stack>
                                ))}
                                <SelectFileButton
                                    variant={'text'}
                                    multiple
                                    onSelected={selectedFiles =>
                                        setFiles(prev => [...prev, ...Array.from(selectedFiles)])
                                    }>
                                    {t('event.registration.mail.addAttachment')}
                                </SelectFileButton>
                                {attachmentBytes > ATTACHMENT_WARNING_BYTES && (
                                    <Alert severity={'warning'}>
                                        {t('event.registration.mail.attachmentWarning', {
                                            size: formatSize(attachmentBytes),
                                        })}
                                    </Alert>
                                )}
                            </Box>
                        </Stack>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={close}>{t('common.cancel')}</Button>
                    <Button
                        variant={'contained'}
                        disabled={!canSend}
                        onClick={() => setConfirming(true)}>
                        {t('event.registration.mail.send')}
                    </Button>
                </DialogActions>
            </BaseDialog>

            <BaseDialog open={confirming} onClose={() => setConfirming(false)} maxWidth={'xs'}>
                <DialogTitle>{t('event.registration.mail.confirm.title')}</DialogTitle>
                <DialogContent>
                    <Typography>
                        {t('event.registration.mail.confirm.text', {count: totalRecipients})}
                    </Typography>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setConfirming(false)}>{t('common.cancel')}</Button>
                    <Button variant={'contained'} disabled={submitting} onClick={send}>
                        {t('event.registration.mail.send')}
                    </Button>
                </DialogActions>
            </BaseDialog>
        </>
    )
}

export default RegistrationMailDialog

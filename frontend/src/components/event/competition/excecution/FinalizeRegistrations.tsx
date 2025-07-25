import {
    Alert,
    AlertTitle,
    Box,
    Button,
    DialogActions,
    DialogContent,
    DialogTitle,
    Link as MuiLink,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {
    finalizeRegistrations,
    getRegistrationResult,
    getRegistrationsWithoutTeamNumber,
} from '@api/sdk.gen.ts'
import {useRef, useState} from 'react'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {eventRoute} from '@routes'
import * as React from 'react'
import Checkbox from '@mui/material/Checkbox'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import Throbber from '@components/Throbber.tsx'
import ReplayIcon from '@mui/icons-material/Replay'
import DownloadIcon from '@mui/icons-material/Download'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import {Info} from '@mui/icons-material'
import BaseDialog from '@components/BaseDialog.tsx'

type Props = {
    registrationsFinalized: boolean
}
const FinalizeRegistrations = ({registrationsFinalized}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()

    const downloadRef = useRef<HTMLAnchorElement>(null)

    const [finalized, setFinalized] = useState<false | number>(
        registrationsFinalized ? Date.now() : false,
    )

    const handleFinalizeRegistrations = async (keepNumbers: boolean) => {
        const {error} = await finalizeRegistrations({
            path: {eventId},
            query: {
                keepNumbers: keepNumbers,
            },
        })
        if (error) {
            feedback.error(t('common.error.unexpected'))
        } else {
            feedback.success(t('event.action.finalizeRegistrations.success'))
            setFinalized(Date.now())
            closeDialog()
        }
    }

    const handleReportDownload = async () => {
        const {data, error, response} = await getRegistrationResult({
            path: {eventId},
            query: {
                remake: true,
            },
        })
        const anchor = downloadRef.current

        const disposition = response.headers.get('Content-Disposition')
        const filename = disposition?.match(/attachment; filename="?(.+)"?/)?.[1]

        if (error) {
            feedback.error(t('event.document.download.error'))
        } else if (data !== undefined && anchor) {
            anchor.href = URL.createObjectURL(data)
            anchor.download = filename ?? 'registration-result.pdf'
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    const {data: registrationsWithoutTeamNumber, pending: pendingRegistrationsWithoutTeamNumber} =
        useFetch(signal => getRegistrationsWithoutTeamNumber({signal, path: {eventId}}), {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('common.error.unexpected'))
                }
            },
            deps: [eventId, finalized],
        })

    const [dialogIsOpen, setDialogIsOpen] = useState(false)
    const openDialog = () => {
        setDialogIsOpen(true)
    }
    const closeDialog = () => {
        setDialogIsOpen(false)
    }

    const [keepTeamNumbersSelected, setKeepTeamNumbersSelected] = useState(true)
    const handleKeepNumbersChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        setKeepTeamNumbersSelected(event.target.checked)
    }

    return (
        <Box>
            <MuiLink ref={downloadRef} display={'none'}></MuiLink>
            <Stack spacing={4}>
                {registrationsWithoutTeamNumber !== null ? (
                    finalized === false ? (
                        <Box>
                            <Button
                                variant={'contained'}
                                onClick={() => handleFinalizeRegistrations(false)}>
                                {t('event.action.finalizeRegistrations.finalize')}
                            </Button>
                        </Box>
                    ) : (
                        <>
                            {registrationsWithoutTeamNumber.length > 0 && (
                                <Alert severity={'warning'}>
                                    <Box sx={{display: 'flex', gap: 1}}>
                                        <AlertTitle>
                                            {t(
                                                'event.action.finalizeRegistrations.newRegistrations.altertTitle',
                                            )}
                                        </AlertTitle>
                                        <HtmlTooltip
                                            placement={'bottom'}
                                            title={
                                                <TableContainer>
                                                    <Table>
                                                        <TableHead>
                                                            <TableRow>
                                                                <TableCell>
                                                                    {t(
                                                                        'event.competition.competition',
                                                                    )}
                                                                </TableCell>
                                                                <TableCell>
                                                                    {t(
                                                                        'event.registration.registration',
                                                                    )}
                                                                </TableCell>
                                                            </TableRow>
                                                        </TableHead>
                                                        <TableBody>
                                                            {registrationsWithoutTeamNumber
                                                                ?.sort((a, b) =>
                                                                    a.competitionIdentifier ===
                                                                    b.competitionIdentifier
                                                                        ? a.registrationClub >
                                                                          b.registrationClub
                                                                            ? -1
                                                                            : 1
                                                                        : a.competitionIdentifier >
                                                                            b.competitionIdentifier
                                                                          ? -1
                                                                          : 1,
                                                                )
                                                                .map(reg => (
                                                                    <TableRow
                                                                        key={reg.registrationId}>
                                                                        <TableCell>
                                                                            {
                                                                                reg.competitionIdentifier
                                                                            }
                                                                        </TableCell>
                                                                        <TableCell>
                                                                            {reg.registrationClub +
                                                                                (reg.registrationName ??
                                                                                    '')}
                                                                        </TableCell>
                                                                    </TableRow>
                                                                ))}
                                                        </TableBody>
                                                    </Table>
                                                </TableContainer>
                                            }>
                                            <Info color={'info'} fontSize={'small'} />
                                        </HtmlTooltip>
                                    </Box>
                                    {t('event.action.finalizeRegistrations.newRegistrations.hint')}
                                </Alert>
                            )}
                            <Stack spacing={1} sx={{alignItems: 'start'}}>
                                <Button
                                    variant={'contained'}
                                    onClick={handleReportDownload}
                                    startIcon={<DownloadIcon />}
                                    sx={{flex: 1}}>
                                    {t('event.action.registrationsReport.download')}
                                </Button>
                                <Button
                                    variant={'outlined'}
                                    onClick={openDialog}
                                    startIcon={<ReplayIcon />}>
                                    {t(
                                        'event.action.finalizeRegistrations.refinalizeRegistrations',
                                    )}
                                </Button>
                            </Stack>
                            <BaseDialog open={dialogIsOpen} onClose={closeDialog} maxWidth={'xs'}>
                                <DialogTitle>
                                    {t(
                                        'event.action.finalizeRegistrations.refinalizeRegistrations',
                                    )}
                                </DialogTitle>
                                {registrationsWithoutTeamNumber.length > 0 ? (
                                    <>
                                        <DialogContent>
                                            <Stack spacing={2} sx={{m: 2}}>
                                                <FormInputLabel
                                                    label={t(
                                                        'event.action.finalizeRegistrations.keepTeamNumbers.keep',
                                                    )}
                                                    required={true}
                                                    horizontal
                                                    reverse>
                                                    <Checkbox
                                                        checked={keepTeamNumbersSelected}
                                                        onChange={handleKeepNumbersChange}
                                                    />
                                                </FormInputLabel>
                                                <Alert severity={'info'}>
                                                    {t(
                                                        'event.action.finalizeRegistrations.keepTeamNumbers.hint',
                                                    )}
                                                </Alert>
                                            </Stack>
                                        </DialogContent>
                                        <DialogActions>
                                            <Button variant={'outlined'} onClick={closeDialog}>
                                                {t('common.cancel')}
                                            </Button>
                                            <Button
                                                variant={'contained'}
                                                onClick={() => {
                                                    void handleFinalizeRegistrations(
                                                        keepTeamNumbersSelected,
                                                    )
                                                    setDialogIsOpen(false)
                                                }}>
                                                {t('event.action.finalizeRegistrations.refinalize')}
                                            </Button>
                                        </DialogActions>
                                    </>
                                ) : (
                                    <>
                                        <DialogContent>
                                            <Alert severity={'info'}>
                                                {t('event.action.finalizeRegistrations.reshuffle')}
                                            </Alert>
                                        </DialogContent>
                                        <DialogActions>
                                            <Button variant={'outlined'} onClick={closeDialog}>
                                                {t('common.cancel')}
                                            </Button>
                                            <Button
                                                variant={'contained'}
                                                onClick={() =>
                                                    void handleFinalizeRegistrations(false)
                                                }>
                                                {t('event.action.finalizeRegistrations.refinalize')}
                                            </Button>
                                        </DialogActions>
                                    </>
                                )}
                            </BaseDialog>
                        </>
                    )
                ) : (
                    pendingRegistrationsWithoutTeamNumber && <Throbber />
                )}
            </Stack>
        </Box>
    )
}
export default FinalizeRegistrations

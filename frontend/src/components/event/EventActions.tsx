import {
    Alert,
    AlertTitle,
    Box,
    Button,
    Dialog,
    Link as MuiLink,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
} from '@mui/material'
import {Trans, useTranslation} from 'react-i18next'
import {
    finalizeRegistrations,
    getRegistrationResult,
    getRegistrationsWithoutTeamNumber,
    produceInvoicesForEventRegistrations,
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
import ReceiptIcon from '@mui/icons-material/Receipt'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import {Info} from '@mui/icons-material'

type Props = {
    registrationsFinalized: boolean
}
const EventActions = ({registrationsFinalized}: Props) => {
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
            feedback.error('[todo] Error when finalizing Registrations')
        } else {
            feedback.success('Registrations finalized')
            setFinalized(Date.now())
        }
    }

    const handleReportDownload = async () => {
        const {data, error} = await getRegistrationResult({
            path: {eventId},
            query: {
                remake: true,
            },
        })
        const anchor = downloadRef.current

        if (error) {
            feedback.error(t('event.document.download.error'))
        } else if (data !== undefined && anchor) {
            anchor.href = URL.createObjectURL(data)
            anchor.download = 'registration-result.pdf' // TODO: read from content-disposition header
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    const {data: registrationsWithoutTeamNumber, pending: pendingRegistrationsWithoutTeamNumber} =
        useFetch(signal => getRegistrationsWithoutTeamNumber({signal, path: {eventId}}), {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error('[todo] Error when loading competition registrations')
                }
            },
            deps: [eventId, finalized],
        })

    const [dialogIsOpen, setDialogIsOpen] = useState(false)

    const [keepTeamNumbersSelected, setKeepTeamNumbersSelected] = useState(true)
    const handleKeepNumbersChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        setKeepTeamNumbersSelected(event.target.checked)
    }

    const handleProduceInvoices = async () => {
        const {data, error} = await produceInvoicesForEventRegistrations({
            path: {eventId},
        })

        if (error !== undefined) {
            feedback.error('[todo] could not produce invoices, cause: ...') // todo: if 409: Provide Bank Account and Contact Information
        } else if (data !== undefined) {
            feedback.success('[todo] invoice producing jobs created')
        }
    }

    return (
        <>
            <MuiLink ref={downloadRef} display={'none'}></MuiLink>
            <Stack spacing={4}>
                {registrationsWithoutTeamNumber !== null ? (
                    finalized === false ? (
                        <Button
                            variant={'contained'}
                            onClick={() => handleFinalizeRegistrations(false)}>
                            {'[todo] Finalize Registrations'}
                        </Button>
                    ) : (
                        <>
                            {registrationsWithoutTeamNumber.length > 0 && (
                                <Alert severity={'warning'}>
                                    <Box sx={{display: 'flex', gap: 1}}>
                                        <AlertTitle>
                                            {'[todo] There are new registrations'}
                                        </AlertTitle>
                                        <HtmlTooltip
                                            placement={'bottom'}
                                            title={
                                                <TableContainer>
                                                    <Table>
                                                        <TableHead>
                                                            <TableRow>
                                                                <TableCell>
                                                                    {'[todo] Competition'}
                                                                </TableCell>
                                                                <TableCell>
                                                                    {'[todo] Registration'}
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
                                    [todo] Since the last finalization, new registrations were made.
                                    These new registrations will not participate in the competitions
                                    until the registrations are refinalized.
                                </Alert>
                            )}
                            <Box sx={{display: 'flex', gap: 2}}>
                                <Button
                                    variant={'contained'}
                                    onClick={handleReportDownload}
                                    startIcon={<DownloadIcon />}
                                    sx={{flex: 1}}>
                                    {t('event.action.registrationsReport.download')}
                                </Button>
                                <Button
                                    variant={'outlined'}
                                    onClick={() => setDialogIsOpen(true)}
                                    startIcon={<ReplayIcon />}>
                                    {'[todo] Refinalize Registrations'}
                                </Button>
                            </Box>
                            <Dialog
                                open={dialogIsOpen}
                                onClose={() => setDialogIsOpen(false)}
                                className="ready2race">
                                <Stack spacing={2} sx={{m: 2}}>
                                    {registrationsWithoutTeamNumber.length > 0 ? (
                                        <>
                                            <FormInputLabel
                                                label={'[todo] Keep set team numbers'}
                                                required={true}
                                                horizontal
                                                reverse>
                                                <Checkbox
                                                    checked={keepTeamNumbersSelected}
                                                    onChange={handleKeepNumbersChange}
                                                />
                                            </FormInputLabel>
                                            <Alert severity={'info'}>
                                                [todo] When keeping the team numbers, new
                                                teams/participants will receive new random team
                                                numbers. These will be higher than the currently
                                                highest of the competition. If not, all the team
                                                numbers will be reshuffled.
                                            </Alert>
                                            <Button
                                                variant={'contained'}
                                                onClick={() => {
                                                    void handleFinalizeRegistrations(
                                                        keepTeamNumbersSelected,
                                                    )
                                                    setDialogIsOpen(false)
                                                }}>
                                                {'[todo] Refinalize'}
                                            </Button>
                                        </>
                                    ) : (
                                        <>
                                            <Alert severity={'info'}>
                                                [todo] This will reshuffle all team numbers
                                            </Alert>
                                            <Button
                                                variant={'contained'}
                                                onClick={() => {
                                                    void handleFinalizeRegistrations(false)
                                                    setDialogIsOpen(false)
                                                }}>
                                                {'[todo] Refinalize'}
                                            </Button>
                                        </>
                                    )}
                                </Stack>
                            </Dialog>
                        </>
                    )
                ) : (
                    pendingRegistrationsWithoutTeamNumber && <Throbber />
                )}
                <Button
                    variant={'contained'}
                    onClick={handleProduceInvoices}
                    startIcon={<ReceiptIcon />}>
                    <Trans i18nKey={'event.action.produceInvoices'} />
                </Button>
            </Stack>
        </>
    )
}
export default EventActions

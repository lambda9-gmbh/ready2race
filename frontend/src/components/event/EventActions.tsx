import {
    Alert,
    AlertTitle,
    Box,
    Button,
    Dialog,
    Link as MuiLink,
    Stack,
    Typography,
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

type Props = {
    registrationsFinalized: boolean
}
const EventActions = ({registrationsFinalized}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()

    const downloadRef = useRef<HTMLAnchorElement>(null)

    const [finalizeSuccessful, setFinalizeSuccessful] = useState(false)

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
            setFinalizeSuccessful(true)
            feedback.success('Registrations finalized')
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

    const handleProduceInvoices = async () => {
        const {data, error} = await produceInvoicesForEventRegistrations({
            path: {eventId},
        })

        if (error !== undefined) {
            feedback.error('[todo] could not produce invoices, cause: ...')
        } else if (data !== undefined) {
            feedback.success('[todo] invoice producing jobs created')
        }
    }

    const {data: registrationsWithoutTeamNumber, pending: pendingRegistrationsWithoutTeamNumber} =
        useFetch(signal => getRegistrationsWithoutTeamNumber({signal, path: {eventId}}), {
            onResponse: ({data, error}) => {
                if (error) {
                    feedback.error('[todo] Error when loading competition registrations')
                } else {
                }
            },
            deps: [],
        })

    const [dialogIsOpen, setDialogIsOpen] = useState(false)

    const [keepTeamNumbersSelected, setKeepTeamNumbersSelected] = useState(true)
    const handleKeepNumbersChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        setKeepTeamNumbersSelected(event.target.checked)
    }

    return (
        <>
            <MuiLink ref={downloadRef} display={'none'}></MuiLink>
            <Stack spacing={4}>
                {registrationsFinalized ? (
                    <Button
                        variant={'contained'}
                        onClick={() => handleFinalizeRegistrations(false)}>
                        {'[todo] Finalize Registrations'}
                    </Button>
                ) : (
                    registrationsWithoutTeamNumber !== null && (
                        <>
                            {registrationsWithoutTeamNumber.length > 0 && (
                                <Alert severity={'warning'}>
                                    <AlertTitle>{'[todo] There are new registrations'}</AlertTitle>
                                    [todo] Since the last finalization, new registrations were made.
                                    These new registrations will not participate in the competitions
                                    until the registrations are refinalized.
                                </Alert>
                            )}
                            <Button variant={'contained'} onClick={handleReportDownload}>
                                {t('event.action.registrationsReport.download')}
                            </Button>
                            <Button variant={'contained'} onClick={() => setDialogIsOpen(true)}>
                                {'[todo] Refinalize Registrations'}
                            </Button>
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
                )}

                <Button variant={'contained'} onClick={handleProduceInvoices}>
                    <Trans i18nKey={'event.action.produceInvoices'} />
                </Button>
            </Stack>
        </>
    )
}
export default EventActions

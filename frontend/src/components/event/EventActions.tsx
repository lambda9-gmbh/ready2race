import {Button, Link as MuiLink, Stack} from "@mui/material";
import {Trans, useTranslation} from "react-i18next";
import {getRegistrationResult, produceInvoicesForEventRegistrations} from "@api/sdk.gen.ts";
import {useRef} from "react";
import {useFeedback} from "@utils/hooks.ts";
import {eventRoute} from "@routes";

const EventActions = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()

    const downloadRef = useRef<HTMLAnchorElement>(null)

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
            path: {eventId}
        })

        if (error !== undefined) {
            feedback.error('[todo] could not produce invoices, cause: ...')
        } else if (data !== undefined) {
            feedback.success('[todo] invoice producing jobs created')
        }
    }

    return (
        <Stack spacing={4}>
            <MuiLink ref={downloadRef} display={'none'}></MuiLink>
            <Button variant={'contained'} onClick={handleReportDownload}>
                {t('event.action.registrationsReport.download')}
            </Button>
            <Button variant={'contained'} onClick={handleProduceInvoices}>
                <Trans i18nKey={'event.action.produceInvoices'}/>
            </Button>
        </Stack>
    )
}
export default EventActions;
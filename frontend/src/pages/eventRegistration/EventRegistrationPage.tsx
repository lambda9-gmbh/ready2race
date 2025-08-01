import {useTranslation} from 'react-i18next'
import {Box} from '@mui/material'
import {getEventRegistration, getRegistrationInvoices} from '@api/sdk.gen.ts'
import {eventRegistrationRoute} from '@routes'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import Throbber from '@components/Throbber'
import InvoiceTable from '@components/invoice/InvoiceTable.tsx'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {InvoiceDto} from '@api/types.gen.ts'

const EventRegistrationPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const path = eventRegistrationRoute.useParams()

    const {data, pending} = useFetch(signal => getEventRegistration({signal, path}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(
                    t('common.load.error.single', {entity: t('event.registration.registration')}),
                )
            }
        },
        deps: [path],
    })

    const invoiceAdministrationProps = useEntityAdministration<InvoiceDto>(t('invoice.invoice'), {
        entityCreate: false,
        entityUpdate: false,
    })

    return (
        <Box>
            {data ? (
                <InvoiceTable
                    {...invoiceAdministrationProps.table}
                    title={t('invoice.invoices')}
                    dataRequest={(signal: AbortSignal, params: PaginationParameters) =>
                        getRegistrationInvoices({
                            signal,
                            path,
                            query: {...params},
                        })
                    }
                />
            ) : (
                pending && <Throbber />
            )}
        </Box>
    )
}

export default EventRegistrationPage

import TabPanel from '@components/tab/TabPanel.tsx'
import {EventTab} from '../EventPage.tsx'
import InvoiceTable from '@components/invoice/InvoiceTable.tsx'
import {useEntityAdministration, useFeedback} from '@utils/hooks.ts'
import {InvoiceDto} from '@api/types.gen.ts'
import {Trans, useTranslation} from 'react-i18next'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {getEventInvoices, produceInvoicesForEventRegistrations} from '@api/sdk.gen.ts'
import {Button} from '@mui/material'
import {useUser} from '@contexts/user/UserContext.ts'
import {createInvoiceGlobal} from '@authorization/privileges.ts'
import {useConfirmation} from "@contexts/confirmation/ConfirmationContext.ts";

type Props = {
    activeTab: EventTab
    eventId: string
    invoicesProducible: boolean
    reloadEvent: () => void
}

const InvoicesTabPanel = ({activeTab, eventId, invoicesProducible, reloadEvent}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const {checkPrivilege} = useUser()
    const {confirmAction} = useConfirmation()

    const invoiceAdministrationProps = useEntityAdministration<InvoiceDto>(t('invoice.invoice'), {
        entityCreate: false,
        entityUpdate: false,
    })

    const handleProduceInvoices = () => {
        confirmAction(
            async () => {
                const {data, error} = await produceInvoicesForEventRegistrations({
                    path: {eventId},
                })
                if (error !== undefined) {
                    let reason = t('common.error.unexpected')
                    if (error.status.value === 409) {
                        switch (error.errorCode) {
                            case 'NO_ASSIGNED_PAYEE_INFORMATION':
                            case 'NO_ASSIGNED_CONTACT_INFORMATION':
                            case 'INVOICES_ALREADY_PRODUCED':
                            case 'EVENT_REGISTRATION_ONGOING':
                                reason = t(`invoice.produce.error.${error.errorCode}`)
                        }
                    }
                    feedback.error(reason)
                } else if (data !== undefined) {
                    feedback.success(t('invoice.produce.success'))
                    reloadEvent()
                }
            },
            {
                title: t('invoice.produce.info.paymentDueBy.title'),
                content: t('invoice.produce.info.paymentDueBy.content'),
                okText: t('event.action.produceInvoices')
            }
        )
    }

    return (
        <TabPanel index={'invoices'} activeTab={activeTab}>
            <InvoiceTable
                {...invoiceAdministrationProps.table}
                title={t('invoice.invoices')}
                resource={'INVOICE'}
                dataRequest={(signal: AbortSignal, params: PaginationParameters) =>
                    getEventInvoices({
                        signal,
                        path: {eventId},
                        query: {...params},
                    })
                }
                customTableActions={
                    checkPrivilege(createInvoiceGlobal) && invoicesProducible ? (
                        <Button variant={'outlined'} onClick={handleProduceInvoices}>
                            <Trans i18nKey={'event.action.produceInvoices'} />
                        </Button>
                    ) : undefined
                }
            />
        </TabPanel>
    )
}

export default InvoicesTabPanel

import TabPanel from '@components/tab/TabPanel.tsx'
import {EventTab} from '../EventPage.tsx'
import InvoiceTable from '@components/invoice/InvoiceTable.tsx'
import {useEntityAdministration, useFeedback} from '@utils/hooks.ts'
import {EventDto, InvoiceDto} from '@api/types.gen.ts'
import {Trans, useTranslation} from 'react-i18next'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {getEventInvoices, produceInvoicesForEventRegistrations} from '@api/sdk.gen.ts'
import {Box, Button, List, ListItem, ListItemText, Tooltip} from '@mui/material'
import {useUser} from '@contexts/user/UserContext.ts'
import {createInvoiceGlobal} from '@authorization/privileges.ts'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {arrayOfNotNull, eventRegistrationPossible} from '@utils/helpers.ts'
import InlineLink from '@components/InlineLink.tsx'

type Props = {
    activeTab: EventTab
    event: EventDto
    reloadEvent: () => void
}

const InvoicesTabPanel = ({activeTab, event, reloadEvent}: Props) => {
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
                    path: {eventId: event.id},
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
                okText: t('event.action.produceInvoices'),
            },
        )
    }

    const problems: ('INVOICES_ALREADY_PRODUCED' | 'EVENT_REGISTRATION_ONGOING')[] = arrayOfNotNull(
        event.invoicesProduced ? 'INVOICES_ALREADY_PRODUCED' : null,
        eventRegistrationPossible(event.registrationAvailableFrom, event.registrationAvailableTo)
            ? 'EVENT_REGISTRATION_ONGOING'
            : null,
    )

    return (
        <TabPanel index={'invoices'} activeTab={activeTab}>
            <InvoiceTable
                {...invoiceAdministrationProps.table}
                title={t('invoice.invoices')}
                hints={[
                    t('invoice.tableHint.1'),
                    <>
                        {t('invoice.tableHint.2')}
                        <InlineLink to={'/config'} search={{tab: 'event-elements'}}>
                            {t('invoice.tableHint.3')}
                        </InlineLink>
                        {t('invoice.tableHint.4')}
                    </>,
                    <>
                        {t('invoice.tableHint.5')}
                        <InlineLink to={'/config'} search={{tab: 'event-elements'}}>
                            {t('invoice.tableHint.6')}
                        </InlineLink>
                        {t('invoice.tableHint.7')}
                    </>,
                ]}
                dataRequest={(signal: AbortSignal, params: PaginationParameters) =>
                    getEventInvoices({
                        signal,
                        path: {eventId: event.id},
                        query: {...params},
                    })
                }
                customTableActions={
                    checkPrivilege(createInvoiceGlobal) ? (
                        <Tooltip
                            title={
                                <List>
                                    {problems.map(p => (
                                        <ListItem key={p}>
                                            <ListItemText
                                                primary={
                                                    <Trans i18nKey={`invoice.produce.error.${p}`} />
                                                }></ListItemText>
                                        </ListItem>
                                    ))}
                                </List>
                            }>
                            <Box>
                                <Button
                                    variant={'outlined'}
                                    onClick={handleProduceInvoices}
                                    disabled={problems.length > 0}>
                                    <Trans i18nKey={'event.action.produceInvoices'} />
                                </Button>
                            </Box>
                        </Tooltip>
                    ) : undefined
                }
            />
        </TabPanel>
    )
}

export default InvoicesTabPanel

import TabPanel from '@components/tab/TabPanel.tsx'
import {EventTab} from '../EventPage.tsx'
import InvoiceTable from '@components/invoice/InvoiceTable.tsx'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import {EventDto, InvoiceDto, RegistrationInvoiceType} from '@api/types.gen.ts'
import {Trans, useTranslation} from 'react-i18next'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {getEventInvoices, getEventInvoicingInfo, produceInvoicesForEventRegistrations} from '@api/sdk.gen.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import {createInvoiceGlobal} from '@authorization/privileges.ts'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'
import {arrayOfNotNull, eventRegistrationPossible} from '@utils/helpers.ts'
import InlineLink from '@components/InlineLink.tsx'
import SelectionMenu from '@components/SelectionMenu.tsx'
import {Alert, Card, CardContent, Stack, Typography} from "@mui/material";

type Props = {
    activeTab: EventTab
    event: EventDto
    reloadEvent: () => void
}

const invoiceTypes: RegistrationInvoiceType[] = ['REGULAR', 'LATE']
type InvoiceType = (typeof invoiceTypes)[number]

const InvoicesTabPanel = ({activeTab, event, reloadEvent}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const {checkPrivilege} = useUser()
    const {confirmAction} = useConfirmation()

    const {reloadData, ...invoiceAdministrationTableProps} = useEntityAdministration<InvoiceDto>(t('invoice.invoice'), {
        entityCreate: false,
        entityUpdate: false,
    }).table

    const handleProduceInvoices = (type: RegistrationInvoiceType) => {
        confirmAction(
            async () => {
                const {data, error} = await produceInvoicesForEventRegistrations({
                    path: {eventId: event.id},
                    body: {type},
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

    const problems: Record<
        InvoiceType,
        ('INVOICES_ALREADY_PRODUCED' | 'EVENT_REGISTRATION_ONGOING')[]
    > = {
        REGULAR: arrayOfNotNull(
            event.invoicesProduced ? 'INVOICES_ALREADY_PRODUCED' : null,
            eventRegistrationPossible(
                event.registrationAvailableFrom,
                event.registrationAvailableTo,
            )
                ? 'EVENT_REGISTRATION_ONGOING'
                : null,
        ),
        LATE: arrayOfNotNull(
            event.lateInvoicesProduced ? 'INVOICES_ALREADY_PRODUCED' : null,
            eventRegistrationPossible(
                event.registrationAvailableTo,
                event.lateRegistrationAvailableTo,
            )
                ? 'EVENT_REGISTRATION_ONGOING'
                : null,
        ),
    }

    const {data, reload: reloadInvoicingInfo} = useFetch( signal => getEventInvoicingInfo({signal, path: {eventId: event.id}}))

    return (
        <TabPanel index={'invoices'} activeTab={activeTab}>
            <Stack spacing={4}>
                {data &&
                    <Card>
                        <CardContent>
                            <Typography>
                                <Trans i18nKey={'event.invoices.infoCard.totalAmount'} values={{amount: data?.totalAmount}} />
                            </Typography>
                            <Typography>
                                <Trans i18nKey={'event.invoices.infoCard.paidAmount'} values={{amount: data?.paidAmount}} />
                            </Typography>
                            {data?.producing &&
                                <Alert severity={'warning'}>
                                    <Trans i18nKey={'event.invoices.infoCard.alert'} />
                                </Alert>
                            }
                        </CardContent>
                    </Card>
                }
                <InvoiceTable
                    {...invoiceAdministrationTableProps}
                    reloadData={() => {
                        reloadInvoicingInfo()
                        reloadData()
                    }}
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
                            <SelectionMenu
                                anchor={{
                                    button: {
                                        vertical: 'bottom',
                                        horizontal: 'right',
                                    },
                                    menu: {
                                        vertical: 'top',
                                        horizontal: 'right',
                                    },
                                }}
                                buttonContent={t('event.action.produceInvoices')}
                                keyLabel={'event-action-produceInvoices'}
                                onSelectItem={async (type: string) => {
                                    const invoiceType = type as RegistrationInvoiceType
                                    handleProduceInvoices(invoiceType)
                                }}
                                items={
                                    invoiceTypes.map(id => ({
                                        id,
                                        label: t('invoice.produce.for', {
                                            type: `$t(invoice.produce.type.${id})`,
                                        }),
                                        problems: problems[id].map(p =>
                                            t(`invoice.produce.error.${p}`),
                                        ),
                                    })) satisfies {id: InvoiceType; label: string; problems: string[]}[]
                                }
                            />
                        ) : undefined
                    }
                />
            </Stack>
        </TabPanel>
    )
}

export default InvoicesTabPanel

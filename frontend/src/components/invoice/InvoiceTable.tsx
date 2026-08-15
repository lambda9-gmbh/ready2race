import EntityTable, {ExtendedGridColDef} from '@components/EntityTable.tsx'
import {GridActionsCellItem, GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {BaseEntityTableProps, EntityAction, PageResponse} from '@utils/types.ts'
import {type GetRegistrationInvoicesError, InvoiceDto, Privilege} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {RequestResult} from '@hey-api/client-fetch'
import {Check, Close, CreditCardOff, Download, Payment} from '@mui/icons-material'
import {downloadInvoice, setInvoicePaid} from '@api/sdk.gen.ts'
import {ReactNode, useRef} from 'react'
import {Box, Link, Stack, Tooltip, Typography} from '@mui/material'
import {useFeedback} from '@utils/hooks.ts'
import {format} from 'date-fns'
import {updateInvoiceGlobal} from '@authorization/privileges.ts'
import {getFilename} from '@utils/helpers.ts'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'invoiceNumber', sort: 'asc'}]

type Props = BaseEntityTableProps<InvoiceDto> & {
    dataRequest: (
        signal: AbortSignal,
        paginationParameters: PaginationParameters,
    ) => RequestResult<PageResponse<InvoiceDto>, GetRegistrationInvoicesError, false>
    customTableActions?: ReactNode
}

const InvoiceTable = (props: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const downloadRef = useRef<HTMLAnchorElement>(null)

    const columns: ExtendedGridColDef<InvoiceDto>[] = [
        {
            field: 'invoiceNumber',
            headerName: t('invoice.invoiceNumber'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'billedToOrganization',
            headerName: t('invoice.billedTo'),
            minWidth: 200,
            flex: 1,
            renderCell: ({row}) => {
                // Bei nur einem Vereinsverwalter steht dessen Name in billedToName - haeufig ist das
                // der Vereinsname selbst, dann waere die zweite Zeile eine Dopplung.
                const organization = row.billedToOrganization ?? row.billedToName
                const name = row.billedToName !== organization ? row.billedToName : undefined
                return (
                    <Stack sx={{py: 0.5}}>
                        <Typography>{organization ?? '-'}</Typography>
                        {row.billedToContacts.length > 0 ? (
                            // Die Empfaenger sind der aktuelle Stand des Vereins, nicht der zur
                            // Rechnungserstellung - hierhin geht die Rechnung heute raus.
                            row.billedToContacts.map(contact => (
                                <Typography
                                    key={contact.email}
                                    variant={'caption'}
                                    color={'text.secondary'}>
                                    {contact.name !== organization && `${contact.name} · `}
                                    <Link
                                        href={`mailto:${contact.email}`}
                                        color={'inherit'}
                                        onClick={e => e.stopPropagation()}>
                                        {contact.email}
                                    </Link>
                                </Typography>
                            ))
                        ) : name ? (
                            // Rueckfallebene: der Verein hat keine Nutzer mehr, dann bleibt nur
                            // der zur Rechnungserstellung festgehaltene Name.
                            <Typography variant={'caption'} color={'text.secondary'}>
                                {name}
                            </Typography>
                        ) : null}
                    </Stack>
                )
            },
        },
        {
            field: 'totalAmount',
            headerName: t('invoice.amount'),
            minWidth: 150,
            flex: 0,
            sortable: false,
            valueFormatter: v => v + ' €',
        },
        {
            field: 'createdAt',
            headerName: t('entity.createdAt'),
            minWidth: 150,
            flex: 0,
            valueGetter: (v: string) => (v ? format(new Date(v), t('format.datetime')) : null),
        },
        {
            field: 'paidAt',
            headerName: t('invoice.paid'),
            flex: 0,
            renderCell: ({value}) => (
                <Tooltip
                    title={value ? format(new Date(value), t('format.datetime')) : t('common.no')}>
                    {value === undefined ? <Close /> : <Check />}
                </Tooltip>
            ),
        },
    ]

    const handleDownload = async (invoiceId: string) => {
        const {data, error, response} = await downloadInvoice({path: {invoiceId}})
        const anchor = downloadRef.current

        if (error) {
            feedback.error(t('invoice.downloadError'))
        } else if (data !== undefined && anchor) {
            anchor.href = URL.createObjectURL(data)
            anchor.download = getFilename(response) ?? 'invoice.pdf'
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    const handlePaid = async (invoiceId: string, paid: boolean) => {
        const {error} = await setInvoicePaid({path: {invoiceId}, body: {paid}})

        if (error) {
            feedback.error(t('common.error.unexpected'))
        }
        props.reloadData()
    }

    const customEntityActions = (
        entity: InvoiceDto,
        checkPrivilege: (privilege: Privilege) => boolean,
    ): EntityAction[] => [
        <GridActionsCellItem
            icon={<Download />}
            label={t('invoice.download')}
            onClick={() => handleDownload(entity.id)}
            showInMenu
        />,
        checkPrivilege(updateInvoiceGlobal) &&
            (!entity.paidAt ? (
                <GridActionsCellItem
                    icon={<Payment />}
                    label={t('invoice.action.setPaid')}
                    onClick={() => handlePaid(entity.id, true)}
                    showInMenu
                />
            ) : (
                <GridActionsCellItem
                    icon={<CreditCardOff />}
                    label={t('invoice.action.setNotPaid')}
                    onClick={() => handlePaid(entity.id, false)}
                    showInMenu
                />
            )),
    ]

    return (
        <Box>
            <Link ref={downloadRef} display={'none'}></Link>
            <EntityTable
                {...props}
                resource={'INVOICE'}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                customEntityActions={customEntityActions}
            />
        </Box>
    )
}

export default InvoiceTable

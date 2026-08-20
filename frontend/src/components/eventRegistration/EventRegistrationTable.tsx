import EntityTable, {ExtendedGridColDef} from '@components/EntityTable.tsx'
import {BaseEntityTableProps} from '@utils/types.ts'
import {EventRegistrationViewDto} from '@api/types.gen.ts'
import {GridPaginationModel, GridSortModel} from '@mui/x-data-grid'
import {useTranslation} from 'react-i18next'
import {PaginationParameters} from '@utils/ApiUtils.ts'
import {deleteEventRegistration, getRegistrationsForEvent} from '@api/sdk.gen.ts'
import {format} from 'date-fns'
import {MouseEvent, useState} from 'react'
import {IconButton, Stack, Typography} from '@mui/material'
import {Message} from '@mui/icons-material'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import {EventRegistrationMessageDialog} from '@components/dashboard/EventRegistrationMessageDialog.tsx'

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'createdAt', sort: 'asc'}]

const formatFee = (amount: number) => `${amount.toFixed(2)} €`

const deleteRequest = (dto: EventRegistrationViewDto) =>
    deleteEventRegistration({path: {eventId: dto.eventId, eventRegistrationId: dto.id}})

const EventRegistrationTable = ({
    eventId,
    ...props
}: BaseEntityTableProps<EventRegistrationViewDto> & {eventId: string}) => {
    const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) =>
        getRegistrationsForEvent({
            signal,
            path: {eventId},
            query: {...paginationParameters},
        })

    const {t} = useTranslation()

    const [messageDialogOpen, setMessageDialogOpen] = useState(false)
    const [message, setMessage] = useState<string | undefined>()

    const showMessage = (event: MouseEvent, msg: string) => {
        event.stopPropagation()
        setMessage(msg)
        setMessageDialogOpen(true)
    }

    const columns: ExtendedGridColDef<EventRegistrationViewDto>[] = [
        {
            field: 'createdAt',
            headerName: t('entity.createdAt'),
            valueGetter: (v: string) => (v ? format(new Date(v), t('format.datetime')) : null),
            minWidth: 200,
            flex: 1,
        },
        {
            field: 'clubName',
            headerName: t('club.club'),
            flex: 2,
        },
        {
            field: 'totalFees',
            headerName: t('event.registration.fees.total'),
            minWidth: 150,
            flex: 0,
            align: 'right',
            headerAlign: 'right',
            // Der Betrag entsteht erst aus zwei Rechnungen - eine ueber die regulaeren Meldungen,
            // eine ueber die Nachmeldungen. Die Aufteilung steht deshalb unter der Summe, sobald
            // Nachmeldegebuehren anfallen.
            valueGetter: (_, row) => Number(row.regularFees) + Number(row.lateFees),
            renderCell: ({value, row}) => {
                const late = Number(row.lateFees)
                return (
                    <Stack sx={{py: 0.5}} alignItems={'end'}>
                        {/* Ohne hinterlegte Gebuehren bleibt die Spalte leer statt "0,00 €" zu wiederholen. */}
                        <Typography>{value > 0 ? formatFee(value) : '-'}</Typography>
                        {late > 0 && (
                            <Typography variant={'caption'} color={'text.secondary'}>
                                {t('event.registration.fees.split', {
                                    regular: formatFee(Number(row.regularFees)),
                                    late: formatFee(late),
                                })}
                            </Typography>
                        )}
                    </Stack>
                )
            },
        },
        {
            field: 'message',
            headerName: t('event.registration.message'),
            width: 90,
            align: 'center',
            headerAlign: 'center',
            renderCell: ({value}) =>
                value ? (
                    <HtmlTooltip title={value}>
                        <IconButton size={'small'} onClick={event => showMessage(event, value)}>
                            <Message fontSize={'small'} />
                        </IconButton>
                    </HtmlTooltip>
                ) : null,
        },
    ]

    return (
        <>
            <EntityTable
                {...props}
                resource={'REGISTRATION'}
                initialPagination={initialPagination}
                pageSizeOptions={pageSizeOptions}
                initialSort={initialSort}
                columns={columns}
                dataRequest={dataRequest}
                deleteRequest={deleteRequest}
                linkColumn={entity => ({
                    to: '/event/$eventId/registration/$eventRegistrationId',
                    params: {eventId: entity.eventId, eventRegistrationId: entity.id},
                })}
            />
            <EventRegistrationMessageDialog
                open={messageDialogOpen}
                onClose={() => setMessageDialogOpen(false)}
                content={message}
            />
        </>
    )
}

export default EventRegistrationTable

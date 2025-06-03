import EntityTable from "@components/EntityTable.tsx";
import {BaseEntityTableProps} from "@utils/types.ts";
import {ContactInformationDto} from "@api/types.gen.ts";
import {GridColDef, GridPaginationModel, GridSortModel} from "@mui/x-data-grid";
import {deleteContact, getContacts} from "@api/sdk.gen.ts";
import {PaginationParameters} from "@utils/ApiUtils.ts";
import {useTranslation} from "react-i18next";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) =>
    getContacts({
        signal,
        query: paginationParameters
    })

const deleteRequest = (entity: ContactInformationDto) =>
    deleteContact({path: {contactId: entity.id}})

const ContactInformationTable = (props: BaseEntityTableProps<ContactInformationDto>) => {

    const {t} = useTranslation()

    const columns: GridColDef<ContactInformationDto>[] = [
        {
            field: 'name',
            headerName: t('contact.name'),
            minWidth: 200,
            flex: 1
        },
        {
            field: 'addressZip',
            headerName: t('contact.zip'),
            minWidth: 200,
            flex: 1
        },
        {
            field: 'addressCity',
            headerName: t('contact.city'),
            minWidth: 200,
            flex: 1
        },
        {
            field: 'addressStreet',
            headerName: t('contact.street'),
            minWidth: 200,
            flex: 1
        },
        {
            field: 'email',
            headerName: t('contact.email'),
            minWidth: 200,
            flex: 1
        },
    ]

    return (
        <EntityTable
            {...props}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
            deleteRequest={deleteRequest}
            parentResource={'EVENT'}
        />
    )
}

export default ContactInformationTable
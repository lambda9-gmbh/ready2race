import {BaseEntityTableProps} from "@utils/types.ts";
import {DocumentTemplateDto} from "@api/types.gen.ts";
import {useTranslation} from "react-i18next";
import {GridColDef, GridPaginationModel, GridSortModel} from "@mui/x-data-grid";
import EntityTable from "@components/EntityTable.tsx";
import {PaginationParameters} from "@utils/ApiUtils.ts";
import {getDocumentTemplates} from "@api/sdk.gen.ts";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) =>
    getDocumentTemplates({
        signal,
        query: {...paginationParameters}
    })

const DocumentTemplateTable = (props: BaseEntityTableProps<DocumentTemplateDto>) => {
    const {t} = useTranslation()

    const columns: GridColDef<DocumentTemplateDto>[] = [
        {
            field: 'name',
            headerName: t('document.template.name'),
            minWidth: 200,
            flex: 1,
        }
    ]

    return (
        <EntityTable
            {...props}
            parentResource={'EVENT'}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
        />
    )
}

export default DocumentTemplateTable
import EntityTable, {ExtendedGridColDef} from "@components/EntityTable.tsx";
import {BaseEntityTableProps} from "@utils/types.ts";
import {StartListConfigDto} from "@api/types.gen.ts";
import {GridPaginationModel, GridSortModel} from "@mui/x-data-grid";
import {PaginationParameters} from "@utils/ApiUtils.ts";
import {deleteStartListConfig, getStartListConfigs} from "@api/sdk.gen.ts";
import {useTranslation} from "react-i18next";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) =>
    getStartListConfigs({
        signal,
        query: {...paginationParameters}
    })

const deleteRequest = (dto: StartListConfigDto) =>
    deleteStartListConfig({path: {startListConfigId: dto.id}})

const StartListConfigTable = (props: BaseEntityTableProps<StartListConfigDto>) => {

    const {t} = useTranslation()

    const columns: ExtendedGridColDef<StartListConfigDto>[] = [
        {
            field: 'name',
            headerName: t('configuration.export.startlist.name'),
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
            deleteRequest={deleteRequest}
        />
    )
}

export default StartListConfigTable
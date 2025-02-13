import {GridColDef, GridPaginationModel, GridSortModel} from "@mui/x-data-grid";
import {BaseEntityTableProps} from "../../../../utils/types.ts";
import {
    deleteRaceCategory,
    getRaceCategories,
    RaceCategoryDto
} from "../../../../api";
import {useTranslation} from "react-i18next";
import {PaginationParameters} from "../../../../utils/ApiUtils.ts";
import EntityTable from "../../../EntityTable.tsx";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
    return getRaceCategories({
        signal,
        query: {...paginationParameters},
    })
}

const deleteRequest = (dto: RaceCategoryDto) => {
    return deleteRaceCategory({path: {raceCategoryId: dto.id}})
}

const RaceCategoryTable = (props: BaseEntityTableProps<RaceCategoryDto>) => {
    const {t} = useTranslation()

    const columns: GridColDef<RaceCategoryDto>[] = [
        {
            field: 'name',
            headerName: t('entity.name'),
            minWidth: 200,
            flex: 1,
        },
        {
            field: 'description',
            headerName: t('entity.description'),
            minWidth: 200,
            flex: 2,
            sortable: false,
        }
    ]

    return (
        <EntityTable
            {...props}
            initialPagination={initialPagination}
            pageSizeOptions={pageSizeOptions}
            initialSort={initialSort}
            columns={columns}
            dataRequest={dataRequest}
            entityName={t('event.race.category.category')}
            deleteRequest={deleteRequest}
        />
    )
}

export default RaceCategoryTable
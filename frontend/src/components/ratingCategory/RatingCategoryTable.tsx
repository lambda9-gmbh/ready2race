import {BaseEntityTableProps} from "@utils/types.ts";
import {RatingCategoryDto} from "@api/types.gen.ts";
import EntityTable, {ExtendedGridColDef} from "@components/EntityTable.tsx";
import {GridPaginationModel, GridSortModel} from "@mui/x-data-grid";
import {PaginationParameters} from "@utils/ApiUtils.ts";
import {deleteRatingCategory, getRatingCategories} from "@api/sdk.gen.ts";
import {useTranslation} from "react-i18next";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) =>
    getRatingCategories({
        signal,
        query: {...paginationParameters}
    })

const deleteRequest = (dto: RatingCategoryDto) =>
    deleteRatingCategory({path: {ratingCategoryId: dto.id}})

const RatingCategoryTable = (props: BaseEntityTableProps<RatingCategoryDto>) => {

    const {t} = useTranslation()

    const columns: ExtendedGridColDef<RatingCategoryDto>[] = [
        {
            field: 'name',
            headerName: t('configuration.ratingCategory.name'),
            minWidth: 200,
            flex: 1,
        },
        {
            field: 'description',
            headerName: t('configuration.ratingCategory.description'),
            minWidth: 200,
            flex: 2,
            sortable: false
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

export default RatingCategoryTable
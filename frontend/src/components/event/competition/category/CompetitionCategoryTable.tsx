import {GridColDef, GridPaginationModel, GridSortModel} from "@mui/x-data-grid";
import {BaseEntityTableProps} from "@utils/types.ts";
import {useTranslation} from "react-i18next";
import {PaginationParameters} from "@utils/ApiUtils.ts";
import EntityTable from "@components/EntityTable.tsx";
import {deleteCompetitionCategory, getCompetitionCategories} from "@api/sdk.gen.ts";
import {CompetitionCategoryDto} from "@api/types.gen.ts";

const initialPagination: GridPaginationModel = {
    page: 0,
    pageSize: 10,
}
const pageSizeOptions: (number | {value: number; label: string})[] = [10]
const initialSort: GridSortModel = [{field: 'name', sort: 'asc'}]

const dataRequest = (signal: AbortSignal, paginationParameters: PaginationParameters) => {
    return getCompetitionCategories({
        signal,
        query: {...paginationParameters},
    })
}

const deleteRequest = (dto: CompetitionCategoryDto) => {
    return deleteCompetitionCategory({path: {competitionCategoryId: dto.id}})
}

const CompetitionCategoryTable = (props: BaseEntityTableProps<CompetitionCategoryDto>) => {
    const {t} = useTranslation()

    const columns: GridColDef<CompetitionCategoryDto>[] = [
        {
            field: 'name',
            headerName: t('event.competition.category.name'),
            minWidth: 150,
            flex: 1,
        },
        {
            field: 'description',
            headerName: t('event.competition.category.description'),
            flex: 2,
            sortable: false,
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
            entityName={t('event.competition.category.category')}
            deleteRequest={deleteRequest}
        />
    )
}

export default CompetitionCategoryTable
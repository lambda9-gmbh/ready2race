import {GridPaginationModel, GridSortModel} from '@mui/x-data-grid'

export type PaginationParameters = {
    limit: number
    offset: number
    sort: string
    search?: string
}

function toSnakeCase(value: string){
    if(value.length < 1){
        return ''
    } else {
        const result = [value.charAt(0).toLowerCase()]
        for (let i = 1; i < value.length; i++) {
            const char = value.charAt(i)
            if (/[A-Z]/.test(char)) {
                result.push('_')
                result.push(char.toLowerCase())
            } else {
                result.push(char)
            }
        }
        return result.join('')
    }
}

export const paginationParameters = (
    pageModel: GridPaginationModel,
    sortModel: GridSortModel,
    searchString?: string,
): PaginationParameters => {
    return {
        limit: pageModel.pageSize,
        offset: pageModel.pageSize * pageModel.page,
        sort: JSON.stringify(sortModel.map((v) => {
            return {field: toSnakeCase(v.field).toUpperCase(), direction: v.sort === 'desc' ? 'DESC' : 'ASC'}
        })),
        search: searchString !== "" ? searchString : undefined
    }
}

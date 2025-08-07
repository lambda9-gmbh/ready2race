import RatingCategoryTable from "@components/ratingCategory/RatingCategoryTable.tsx";
import {useEntityAdministration} from "@utils/hooks.ts";
import {RatingCategoryDto} from "@api/types.gen.ts";
import RatingCategoryDialog from "@components/ratingCategory/RatingCategoryDialog.tsx";
import {useTranslation} from "react-i18next";

const RatingCategoryPanel = () => {
    const {t} = useTranslation()
    const administrationProps = useEntityAdministration<RatingCategoryDto>(t('configuration.ratingCategory.ratingCategory'))

    return (
        <>
            <RatingCategoryTable
                {...administrationProps.table}
                title={t('configuration.ratingCategory.ratingCategories')}
                hints={[t('configuration.ratingCategory.tableHint')]}
            />
            <RatingCategoryDialog {...administrationProps.dialog} />
        </>
    )
}

export default RatingCategoryPanel
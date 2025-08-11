import {useEntityAdministration} from "@utils/hooks.ts";
import {MatchResultImportConfigDto} from "@api/types.gen.ts";
import {useTranslation} from "react-i18next";
import MatchResultImportConfigTable from "@components/matchResultImportConfig/MatchResultImportConfigTable.tsx";
import MatchResultImportConfigDialog from "@components/matchResultImportConfig/MatchResultImportConfigDialog.tsx";

const MatchResultImportConfigPanel = () => {
    const {t} = useTranslation()
    const administrationProps = useEntityAdministration<MatchResultImportConfigDto>(t('configuration.import.matchResult.config'))

    return (
        <>
            <MatchResultImportConfigTable
                {...administrationProps.table}
                title={t('configuration.import.matchResult.configs')}
                hints={[t('configuration.import.matchResult.tableHint')]}
                id={'matchResults'}
            />
            <MatchResultImportConfigDialog {...administrationProps.dialog} />
        </>
    )
}

export default MatchResultImportConfigPanel
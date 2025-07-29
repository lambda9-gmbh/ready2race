import StartListConfigTable from "@components/startListConfig/StartListConfigTable.tsx";
import {useEntityAdministration} from "@utils/hooks.ts";
import {StartListConfigDto} from "@api/types.gen.ts";
import StartListConfigDialog from "@components/startListConfig/StartListConfigDialog.tsx";
import {useTranslation} from "react-i18next";

const StartListConfigPanel = () => {
    const {t} = useTranslation()
    const administrationProps = useEntityAdministration<StartListConfigDto>(t('configuration.export.startlist.startlist'))

    return (
        <>
            <StartListConfigTable  {...administrationProps.table} title={t('configuration.export.startlist.startlists')}/>
            <StartListConfigDialog {...administrationProps.dialog} />
        </>
    )
}

export default StartListConfigPanel
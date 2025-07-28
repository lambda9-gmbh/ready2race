import StartListConfigTable from "@components/startListConfig/StartListConfigTable.tsx";
import {useEntityAdministration} from "@utils/hooks.ts";
import {StartListConfigDto} from "@api/types.gen.ts";
import StartListConfigDialog from "@components/startListConfig/StartListConfigDialog.tsx";

const StartListConfigPanel = () => {

    const administrationProps = useEntityAdministration<StartListConfigDto>('[todo] SL Config')

    return (
        <>
            <StartListConfigTable  {...administrationProps.table} title={'[todo] SL-Configs'}/>
            <StartListConfigDialog {...administrationProps.dialog} />
        </>
    )
}

export default StartListConfigPanel
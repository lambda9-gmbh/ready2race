import InvoiceTable from "@components/invoice/InvoiceTable.tsx";
import {useEntityAdministration} from "@utils/hooks.ts";
import {InvoiceDto} from "@api/types.gen.ts";
import {useTranslation} from "react-i18next";
import {PaginationParameters} from "@utils/ApiUtils.ts";

const AdministrationPage = () => {

    const {t} = useTranslation()

    const invoiceAdministrationProps =
        useEntityAdministration<InvoiceDto>(t('invoice.invoice'), {entityCreate: false, entityUpdate: false})

    /*return (
        <InvoiceTable
            {...invoiceAdministrationProps.table}
            title={t('invoice.invoices')}
            resource={'INVOICE'}
            dataRequest={(signal: AbortSignal, params: PaginationParameters) =>
                0//getInvoices
            }
        />
    )*/
}

export default AdministrationPage
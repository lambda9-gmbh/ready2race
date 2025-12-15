import "@typespec/http";

namespace Ready2Race;

using Http;

scalar uuid extends string;

model UrlPaginationParameters {
    @query limit?: int32;
    @query offset?: int32;
    @query sort?: string;
    @query search?: string;
}


"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
var openapi_ts_1 = require("@hey-api/openapi-ts");
exports.default = (0, openapi_ts_1.defineConfig)({
    client: '@hey-api/client-fetch',
    input: '../backend/src/main/resources/openapi/documentation.yaml',
    output: {
        format: 'prettier',
        path: 'src/api',
    }
});

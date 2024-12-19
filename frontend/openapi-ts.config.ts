import {defineConfig} from '@hey-api/openapi-ts'

export default defineConfig({
    client: '@hey-api/client-fetch',
    input: '../backend/src/main/resources/openapi/documentation.yaml',
    output: {
        format: 'prettier',
        path: 'src/api',
    }
})
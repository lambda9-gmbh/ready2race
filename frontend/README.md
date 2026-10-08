# Ready2Race Frontend

For Docker self-hosting and runtime URL configuration, see the
[deployment guide](../docs/docker-deployment.md). Runtime settings in `window.READY2RACE_CONFIG`
override build-time `VITE_*` settings; absolute URLs remain supported for embedded frontends.

## Installation

### Dependencies
- npm

### Building
Before building, you need to provide a `.env` including the URL to your Backend API. You can copy template.env and fill
in the missing values.\
To build the frontend, you just have to run the following commands:
```shell
$ npm i
$ npm run build
```
This should generate a `/dist` directory including all files for serving your frontend application.

## Running
Serve the generated files in the `/dist` directory.

## Contributing

### Code format

Make sure to format your code with Prettier. It is already added as a devDependency and ready to use after `npm install`.

### Local Configuration

Add `.env` to the frontend root with adequate keys/values. You can copy `template.env` for fitting keys.

### Noteworthy Packages

Before implementing own solutions, it is recommended to check the already used libraries for fitting features.

- client fetch api + code generation from openapi documentation [hey-api](https://heyapi.dev/)
- client site routing [TanStack Router](https://tanstack.com/router/latest)
- component library [MUI](https://mui.com/material-ui/getting-started/)
- internationalization [i18next](https://www.i18next.com/)

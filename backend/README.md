# Ready2Race Backend

## Contributing

### Local Configuration

Add `.env` to the backend root with adequate keys/values. You can copy `template.env` for fitting keys.
You can use any other name für your local Env-File, you just need to add its name as first Argument when
starting the application. Without the argument, `.env` will be used.

### Ktor Plugins

Before adding new server features, please make sure there isn't already a usable ktor plugin for the job.

## Running

### Configure application

Add an adequate `.env` to your server and give its Path as first argument when running the application. You can copy
`template.env` for fitting keys.

### Configure Hashing

Password4j with Argon2 is used for Hashing. By default, a `psw4j.properties` file is used with default values. You can find
recommended settings in the [GitHub Wiki](https://github.com/Password4j/password4j/wiki/Recommended-settings#argon2).
To set the path to your custom properties file, you can use

`-Dpsw4j.configuration=path/to/my/file.properties`

when starting the application with Java.
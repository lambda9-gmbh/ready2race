package de.lambda9.ready2race.backend.plugins

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        jackson {
            registerModules(JavaTimeModule().apply {

                // LOCAL DATETIME

                addDeserializer(
                    LocalDateTime::class.java,
                    LocalDateTimeDeserializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                )

                addSerializer(
                    LocalDateTime::class.java,
                    LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                )

                addSerializer(LocalTime::class.java, LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm:ss")))


                // LOCAL DATES

                addSerializer(
                    LocalDate::class.java,
                    LocalDateSerializer(DateTimeFormatter.ISO_DATE)
                )

                // Solves the problem of APIs sending the date, as well as
                // time, when you only need time.
                addDeserializer(
                    LocalDate::class.java, LocalDateDeserializer(
                        DateTimeFormatterBuilder()
                            .parseCaseInsensitive()
                            .append(DateTimeFormatter.ISO_LOCAL_DATE)
                            .optionalStart()
                            .appendLiteral('T')
                            .append(DateTimeFormatter.ISO_LOCAL_TIME)
                            .optionalEnd()
                            .toFormatter()
                    )
                )
            })
        }
    }
}

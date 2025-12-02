package de.lambda9.ready2race.backend.validation

val emailPattern = """^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))${'$'}""".toRegex()

val timecodePattern = """^([-+])?(\d+)(:([0-5]\d))?(:([0-5]\d))?(\.(\d{1,3}))?${'$'}""".toRegex()
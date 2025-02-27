package de.lambda9.ready2race.backend.app.captcha.boundary

import de.lambda9.ready2race.backend.afterNow
import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.captcha.control.CaptchaRepo
import de.lambda9.ready2race.backend.app.captcha.entity.CaptchaChallengeDto
import de.lambda9.ready2race.backend.app.captcha.entity.CaptchaError
import de.lambda9.ready2race.backend.database.generated.tables.records.CaptchaRecord
import de.lambda9.ready2race.backend.responses.ApiResponse
import de.lambda9.ready2race.backend.toBase64
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unit
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

object CaptchaService {

    private const val SOLUTION_DENSITY = 3 // one possible solution every SOLUTION_DENSITY pixels
    private const val CAPTCHA_WIDTH = 480
    private const val CAPTCHA_HEIGHT = 100
    private const val HANDLE_SIZE = 72
    private const val TARGET_SIZE = 80

    private const val ALLOWED_INACCURACY = 2

    private val CHALLENGE_LIFETIME = 5.minutes

    fun newChallenge(): App<Nothing, ApiResponse> = KIO.comprehension {
        val solutionScaledWidth = (CAPTCHA_WIDTH - HANDLE_SIZE) / SOLUTION_DENSITY
        val solutionScaledOffset = HANDLE_SIZE / SOLUTION_DENSITY / 2

        // solution => center of circle, scaled to number of solutions
        val solution = Random.nextInt(solutionScaledWidth) + solutionScaledOffset
        val start = (solution + Random.nextInt(
            ALLOWED_INACCURACY + 1,
            solutionScaledWidth - ALLOWED_INACCURACY
        )) % solutionScaledWidth

        // transformedSolution => left of circle, scaled to image pixels
        val transformedSolution = solution * SOLUTION_DENSITY - TARGET_SIZE / 2

        val img = BufferedImage(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, BufferedImage.TYPE_INT_RGB)
        val g2d = img.createGraphics()
        g2d.color = Color(153, 153, 153)
        g2d.fillRect(0, 0, CAPTCHA_WIDTH, CAPTCHA_HEIGHT)
        g2d.color = Color(51, 51, 51)
        g2d.stroke = BasicStroke(7F)
        g2d.drawOval(Random.nextInt(-20, 20), Random.nextInt(-40, 0), Random.nextInt(100, 400), Random.nextInt(80, 200))
        g2d.drawOval(
            Random.nextInt(-300, -100) + CAPTCHA_WIDTH,
            Random.nextInt(-40, 0),
            Random.nextInt(100, 400),
            Random.nextInt(80, 200)
        )
        g2d.drawOval(
            Random.nextInt(-20, 20),
            Random.nextInt(-80, -40) + CAPTCHA_HEIGHT,
            Random.nextInt(100, 400),
            Random.nextInt(80, 200)
        )
        g2d.drawOval(
            Random.nextInt(-300, -100) + CAPTCHA_WIDTH,
            Random.nextInt(-80, -40) + CAPTCHA_HEIGHT,
            Random.nextInt(100, 400),
            Random.nextInt(80, 200)
        )
        g2d.drawOval(transformedSolution, 10, TARGET_SIZE, TARGET_SIZE)
        g2d.dispose()

        val baos = ByteArrayOutputStream()
        ImageIO.write(img, "png", baos)
        val imgBytes = baos.toByteArray()
        baos.flush()
        baos.close()

        val captchaId = !CaptchaRepo.create(
            CaptchaRecord(
                id = UUID.randomUUID(),
                solution = solution,
                expiresAt = CHALLENGE_LIFETIME.afterNow()
            )
        ).orDie()

        KIO.ok(
            ApiResponse.Dto(
                CaptchaChallengeDto(
                    id = captchaId,
                    imgSrc = "data:image/png;base64,${imgBytes.toBase64()}",
                    solutionMin = solutionScaledOffset,
                    solutionMax = solutionScaledWidth + solutionScaledOffset - 1,
                    handleToHeightRatio = HANDLE_SIZE.toFloat() / CAPTCHA_HEIGHT.toFloat(),
                    start = start,
                )
            )
        )
    }

    fun trySolution(
        id: UUID,
        input: Int,
    ): App<CaptchaError, Unit> = KIO.comprehension {
        val solution = !CaptchaRepo.consume(id).orDie()

        when {
            solution == null -> App.fail(CaptchaError.ChallengeNotFound)

            input !in (solution.solution - ALLOWED_INACCURACY).rangeTo(solution.solution + ALLOWED_INACCURACY) ->
                App.fail(CaptchaError.WrongSolution)

            else -> unit
        }
    }

    fun deleteExpired(): App<Nothing, Int> = CaptchaRepo.deleteExpired().orDie()
}
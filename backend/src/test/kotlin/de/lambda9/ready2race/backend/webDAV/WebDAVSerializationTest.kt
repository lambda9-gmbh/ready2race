package de.lambda9.ready2race.backend.webDAV

import de.lambda9.ready2race.backend.app.webDAV.entity.*
import de.lambda9.ready2race.backend.app.webDAV.entity.users.*
import de.lambda9.ready2race.backend.calls.serialization.jsonMapper
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertNotNull

class WebDAVSerializationTest {

    @Test
    fun testSuccessfulSerializationOfDataUsersExport() {
        // Create test data with all fields populated
        val testUser = AppUserExport(
            id = UUID.randomUUID(),
            email = "test@example.com",
            password = "hashed_password_123",
            firstname = "John",
            lastname = "Doe",
            language = "en",
            createdAt = LocalDateTime.of(2024, 1, 1, 10, 0),
            createdBy = UUID.randomUUID(),
            updatedAt = LocalDateTime.of(2024, 1, 2, 10, 0),
            updatedBy = UUID.randomUUID(),
            club = UUID.randomUUID()
        )

        val testRole = RoleExport(
            id = UUID.randomUUID(),
            name = "Admin",
            description = "Administrator role",
            static = false,
            createdAt = LocalDateTime.of(2024, 1, 1, 10, 0),
            createdBy = UUID.randomUUID(),
            updatedAt = LocalDateTime.of(2024, 1, 2, 10, 0),
            updatedBy = UUID.randomUUID()
        )

        val roleHasPrivilege = RoleHasPrivilegeExport(
            role = testRole.id,
            privilege = UUID.randomUUID()
        )

        val appUserHasRole = AppUserHasRoleExport(
            appUser = testUser.id,
            role = testRole.id
        )

        val exportData = DataUsersExport(
            appUsers = listOf(testUser),
            roles = listOf(testRole),
            roleHasPrivileges = listOf(roleHasPrivilege),
            appUserHasRoles = listOf(appUserHasRole)
        )

        // Test serialization
        val jsonContent = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exportData)

        assertNotNull(jsonContent)
        assertContains(jsonContent, "test@example.com")
        assertContains(jsonContent, "Admin")
        assertContains(jsonContent, "2024-01-01T10:00:00")

        println("Successful serialization test passed")
    }

    @Test
    fun testSerializationWithNullFields() {
        // Create test data with null optional fields
        val testUser = AppUserExport(
            id = UUID.randomUUID(),
            email = "minimal@example.com",
            password = "password",
            firstname = "Jane",
            lastname = "Smith",
            language = "de",
            createdAt = LocalDateTime.now(),
            createdBy = null,  // null field
            updatedAt = LocalDateTime.now(),
            updatedBy = null,  // null field
            club = null        // null field
        )

        val testRole = RoleExport(
            id = UUID.randomUUID(),
            name = "User",
            description = null,  // null field
            static = true,
            createdAt = LocalDateTime.now(),
            createdBy = null,    // null field
            updatedAt = LocalDateTime.now(),
            updatedBy = null     // null field
        )

        val exportData = DataUsersExport(
            appUsers = listOf(testUser),
            roles = listOf(testRole),
            roleHasPrivileges = emptyList(),
            appUserHasRoles = emptyList()
        )

        // Test serialization with nulls
        val jsonContent = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exportData)

        assertNotNull(jsonContent)
        assertContains(jsonContent, "minimal@example.com")
        assertContains(jsonContent, "\"club\" : null")
        assertContains(jsonContent, "\"description\" : null")

        println("Null fields serialization test passed")
    }

    @Test
    fun testSerializationWithEmptyLists() {
        val exportData = DataUsersExport(
            appUsers = emptyList(),
            roles = emptyList(),
            roleHasPrivileges = emptyList(),
            appUserHasRoles = emptyList()
        )

        // Test serialization with empty lists
        val jsonContent = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exportData)

        assertNotNull(jsonContent)
        assertContains(jsonContent, "\"appUsers\" : [ ]")
        assertContains(jsonContent, "\"roles\" : [ ]")

        println("Empty lists serialization test passed")
    }

    @Test
    fun testTryCatchHandling() {
        // Simulate the try-catch pattern used in WebDAVService
        fun simulateExportSerialization(): String? {
            val exportData = DataUsersExport(
                appUsers = listOf(
                    AppUserExport(
                        id = UUID.randomUUID(),
                        email = "test@example.com",
                        password = "password",
                        firstname = "Test",
                        lastname = "User",
                        language = "en",
                        createdAt = LocalDateTime.now(),
                        createdBy = null,
                        updatedAt = LocalDateTime.now(),
                        updatedBy = null,
                        club = null
                    )
                ),
                roles = emptyList(),
                roleHasPrivileges = emptyList(),
                appUserHasRoles = emptyList()
            )

            return try {
                jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exportData)
            } catch (e: Exception) {
                println("Caught exception in try-catch: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }

        val result = simulateExportSerialization()
        assertNotNull(result, "Serialization should succeed for valid data")

        println("Try-catch handling test passed")
    }

    @Test
    fun testLargeDataSetSerialization() {
        // Create a larger dataset to test performance
        val users = (1..100).map { i ->
            AppUserExport(
                id = UUID.randomUUID(),
                email = "user$i@example.com",
                password = "password$i",
                firstname = "User",
                lastname = "Number$i",
                language = if (i % 2 == 0) "en" else "de",
                createdAt = LocalDateTime.now().minusDays(i.toLong()),
                createdBy = if (i > 1) UUID.randomUUID() else null,
                updatedAt = LocalDateTime.now(),
                updatedBy = UUID.randomUUID(),
                club = if (i % 3 == 0) UUID.randomUUID() else null
            )
        }

        val roles = (1..10).map { i ->
            RoleExport(
                id = UUID.randomUUID(),
                name = "Role$i",
                description = "Description for role $i",
                static = i <= 3,
                createdAt = LocalDateTime.now().minusDays(i.toLong()),
                createdBy = UUID.randomUUID(),
                updatedAt = LocalDateTime.now(),
                updatedBy = UUID.randomUUID()
            )
        }

        val exportData = DataUsersExport(
            appUsers = users,
            roles = roles,
            roleHasPrivileges = emptyList(),
            appUserHasRoles = emptyList()
        )

        // Test serialization of large dataset
        val startTime = System.currentTimeMillis()
        val jsonContent = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exportData)
        val endTime = System.currentTimeMillis()

        assertNotNull(jsonContent)
        assertContains(jsonContent, "user1@example.com")
        assertContains(jsonContent, "user100@example.com")
        assertContains(jsonContent, "Role1")
        assertContains(jsonContent, "Role10")

        println("Large dataset serialization completed in ${endTime - startTime}ms")
        println("JSON size: ${jsonContent.length} characters")
    }
}
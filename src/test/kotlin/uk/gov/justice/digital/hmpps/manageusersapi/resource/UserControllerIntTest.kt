package uk.gov.justice.digital.hmpps.manageusersapi.resource

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.OK
import org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters.fromValue
import uk.gov.justice.digital.hmpps.manageusersapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.manageusersapi.model.AuthSource.auth
import uk.gov.justice.digital.hmpps.manageusersapi.model.AuthSource.azuread
import uk.gov.justice.digital.hmpps.manageusersapi.model.AuthSource.delius
import uk.gov.justice.digital.hmpps.manageusersapi.model.AuthSource.nomis
import uk.gov.justice.digital.hmpps.manageusersapi.model.AuthSource.none
import java.util.Locale
import java.util.UUID

class UserControllerIntTest : IntegrationTestBase() {

  fun stubUserNotFound(
    username: String,
    external: Boolean = true,
    nomis: Boolean = false,
    azure: Boolean = false,
    delius: Boolean = false,
  ) {
    if (external) externalUsersApiMockServer.stubGetFail("/users/$username", NOT_FOUND)
    if (nomis) nomisApiMockServer.stubGetFail("/users/${username.uppercase()}", NOT_FOUND)
    if (azure) hmppsAuthMockServer.stubGetFail("/auth/api/azureuser/$username", NOT_FOUND)
    if (delius) deliusApiMockServer.stubGetFail("/user/$username", NOT_FOUND)
  }

  @Nested
  inner class FindByUsername {

    @Test
    fun `access forbidden when no authority`() {
      webTestClient.get().uri("/users/AUTH_ADM")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun ` should fail with not_found for basic username`() {
      val username = "BOB_ADM"
      stubUserNotFound(username, external = true, nomis = true, delius = true)

      webTestClient.get().uri("/users/$username")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isNotFound
        .expectHeader().contentType(APPLICATION_JSON)
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it["error"]).isEqualTo("Not Found")
          assertThat(it["error_description"]).isEqualTo("Account for username $username not found")
          assertThat(it["field"]).isEqualTo("username")
        }
      hmppsAuthMockServer.verify(0, getRequestedFor(urlEqualTo("/auth/api/azureuser/$username")))
    }

    @Test
    fun ` should fail with not_found for UUID username`() {
      val username = UUID.randomUUID().toString()
      stubUserNotFound(username, external = true, nomis = true, azure = true, delius = true)

      webTestClient.get().uri("/users/$username")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isNotFound
        .expectHeader().contentType(APPLICATION_JSON)
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it["error"]).isEqualTo("Not Found")
          assertThat(it["error_description"]).isEqualTo("Account for username $username not found")
          assertThat(it["field"]).isEqualTo("username")
        }
    }

    @Test
    fun ` should fail with not_found for email address username`() {
      val username = "testing@digital.justice.gov.uk"
      stubUserNotFound(username, external = true)

      webTestClient.get().uri("/users/$username")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isNotFound
      nomisApiMockServer.verify(0, getRequestedFor(urlEqualTo("/users/$username")))
      hmppsAuthMockServer.verify(0, getRequestedFor(urlEqualTo("/auth/api/azureuser/$username")))
      deliusApiMockServer.verify(0, getRequestedFor(urlEqualTo("/user/$username")))
    }

    @Test
    fun ` external user found success`() {
      val username = "EXT_ADM"
      val uuid = UUID.randomUUID()
      externalUsersApiMockServer.stubUserByUsername(username)
      hmppsAuthMockServer.stubUserIdByUsernameAndSource(username, auth, uuid)
      webTestClient.get().uri("/users/$username")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isOk
        .expectHeader().contentType(APPLICATION_JSON)
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "username" to username,
              "active" to true,
              "name" to "Ext Adm",
              "authSource" to "auth",
              "userId" to "5105a589-75b3-4ca0-9433-b96228c1c8f3",
              "uuid" to uuid.toString(),
            ),
          )
        }
    }

    @Test
    fun ` nomis user found success`() {
      val username = "NUSER_GEN"
      val uuid = UUID.randomUUID()
      stubUserNotFound(username, external = true)
      nomisApiMockServer.stubFindUserBasicDetailsByUsername(username)
      hmppsAuthMockServer.stubUserIdByUsernameAndSource(username, nomis, uuid)
      webTestClient.get().uri("/users/$username")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isOk
        .expectHeader().contentType(APPLICATION_JSON)
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "username" to username.uppercase(),
              "active" to true,
              "name" to "Nomis Take",
              "authSource" to "nomis",
              "userId" to "123456",
              "staffId" to 123456,
              "activeCaseLoadId" to "MDI",
              "uuid" to uuid.toString(),
            ),
          )
        }
    }

    @Test
    fun ` azure user found success`() {
      val username = "ce232d07-40c3-47c6-9903-613bb31132af"
      val uuid = UUID.randomUUID()
      stubUserNotFound(username, external = true, nomis = true)
      hmppsAuthMockServer.stubAzureUserByUsername(username)
      hmppsAuthMockServer.stubUserIdByUsernameAndSource(username, azuread, uuid)
      webTestClient.get().uri("/users/$username")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isOk
        .expectHeader().contentType(APPLICATION_JSON)
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "username" to username.uppercase(),
              "active" to true,
              "name" to "Azure User",
              "authSource" to "azuread",
              "userId" to "azure.user@justice.gov.uk",
              "uuid" to uuid.toString(),
            ),
          )
        }
    }

    @Test
    fun ` delius user found success`() {
      val uuid = UUID.randomUUID()
      val username = "deliususer"
      stubUserNotFound(username, external = true, nomis = true)
      deliusApiMockServer.stubGetUser(username)
      hmppsAuthMockServer.stubUserIdByUsernameAndSource(username, delius, uuid)
      webTestClient.get().uri("/users/$username")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isOk
        .expectHeader().contentType(APPLICATION_JSON)
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "username" to username.uppercase(),
              "active" to true,
              "name" to "Delius Smith",
              "authSource" to "delius",
              "userId" to "1234567890",
              "uuid" to uuid.toString(),
            ),
          )
        }
    }
  }

  @Nested
  inner class MyDetails {
    @Test
    fun `Users Me endpoint returns user data for external user`() {
      val username = "AUTH_ADM"
      val uuid = UUID.randomUUID()
      externalUsersApiMockServer.stubUserByUsername(username)
      hmppsAuthMockServer.stubUserIdByUsernameAndSource(username, auth, uuid)
      webTestClient
        .get().uri("/users/me")
        .headers(
          setAuthorisationWithAuthSource(),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "username" to "AUTH_ADM",
              "active" to true,
              "name" to "Ext Adm",
              "authSource" to "auth",
              "userId" to "5105a589-75b3-4ca0-9433-b96228c1c8f3",
              "uuid" to uuid.toString(),
            ),
          )
        }
    }

    @Test
    fun `Users Me endpoint returns user data for nomis user`() {
      val username = "AUTH_ADM"
      val uuid = UUID.randomUUID()
      nomisApiMockServer.stubFindUserBasicDetailsByUsername(username)
      hmppsAuthMockServer.stubUserIdByUsernameAndSource(username, nomis, uuid)
      webTestClient
        .get().uri("/users/me")
        .headers(
          setAuthorisationWithAuthSource(authSource = nomis),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "username" to username,
              "active" to true,
              "name" to "Nomis Take",
              "authSource" to "nomis",
              "userId" to "123456",
              "staffId" to 123456,
              "activeCaseLoadId" to "MDI",
              "uuid" to uuid.toString(),
            ),
          )
        }
    }

    @Test
    fun `Users Me endpoint returns user name if no user`() {
      webTestClient
        .get().uri("/users/me")
        .headers(
          setAuthorisationWithAuthSource(user = "basicuser", authSource = none),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it).containsExactlyEntriesOf(
            mapOf(
              "username" to "basicuser",
              "authSource" to "none",
            ),
          )
        }
    }

    @Test
    fun `Users Me endpoint not accessible without valid token`() {
      webTestClient
        .get().uri("/users/me")
        .exchange()
        .expectStatus().isUnauthorized
    }
  }

  @Nested
  inner class FindMyEmail {
    @Test
    fun `My email endpoint returns unverified email`() {
      val username = "USER_UNVERIFIED"
      hmppsAuthMockServer.stubUserEmail(username, unverifiedParam = true, verifiedEmail = false)

      webTestClient
        .get().uri("/users/me/email?unverified=true")
        .headers(
          setAuthorisation(
            "USER_UNVERIFIED",
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "username" to username,
              "email" to "User.FromAuth@digital.justice.gov.uk",
              "verified" to false,
            ),
          )
        }
    }

    @Test
    fun `My email endpoint returns no content unverified email`() {
      val username = "USER_UNVERIFIED"
      hmppsAuthMockServer.stubUserEmail(username, unverifiedParam = false, verifiedEmail = false)

      webTestClient
        .get().uri("/users/me/email?unverified=false")
        .headers(
          setAuthorisation(
            "USER_UNVERIFIED",
          ),
        )
        .exchange()
        .expectStatus().isNoContent
    }

    @Test
    fun `My email endpoint returns verified email`() {
      val username = "USER_VERIFIED"
      hmppsAuthMockServer.stubUserEmail(username)

      webTestClient
        .get().uri("/users/me/email")
        .headers(
          setAuthorisation(
            "USER_VERIFIED",
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "username" to username,
              "email" to "User.FromAuth@digital.justice.gov.uk",
              "verified" to true,
            ),
          )
        }
    }
  }

  @Nested
  inner class FindUserEmail {

    @Test
    fun `User email endpoint not accessible without valid token`() {
      webTestClient
        .get().uri("/users/bob/email")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `User email endpoint returns no content for unverified user in auth`() {
      hmppsAuthMockServer.stubUserEmail("AUTH_UNVERIFIED", unverifiedParam = false, verifiedEmail = false)

      webTestClient
        .get().uri("/users/AUTH_UNVERIFIED/email")
        .headers(setAuthorisation("ITAG_USER"))
        .exchange()
        .expectStatus().isNoContent
    }

    @Test
    fun `User email endpoint returns not found if no user`() {
      val username = "no_user"
      hmppsAuthMockServer.stubGetFail("/auth/api/user/$username/authEmail?unverified=false", NOT_FOUND)
      stubUserNotFound(username, external = true, nomis = true, delius = true)

      webTestClient
        .get().uri("/users/$username/email")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isNotFound

      hmppsAuthMockServer.verify(0, getRequestedFor(urlMatching("/auth/api/user\\?username=$username&source=([a-z]*)")))
    }

    @Test
    fun `User email endpoint returns unverified user in auth if param set`() {
      val username = "AUTH_UNVERIFIED"
      hmppsAuthMockServer.stubUserEmail(username, unverifiedParam = true, verifiedEmail = false)

      webTestClient
        .get().uri("/users/$username/email?unverified=true")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "username" to username,
              "email" to "User.FromAuth@digital.justice.gov.uk",
              "verified" to false,
            ),
          )
        }
      hmppsAuthMockServer.verify(0, getRequestedFor(urlEqualTo("/auth/api/user/$username/auth")))
    }

    @Test
    fun `User email endpoint returns verified user email from auth`() {
      val username = "AUTH_USER"
      hmppsAuthMockServer.stubUserEmail("AUTH_USER")

      webTestClient
        .get().uri("/users/$username/email")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "username" to username,
              "email" to "User.FromAuth@digital.justice.gov.uk",
              "verified" to true,
            ),
          )
        }
      hmppsAuthMockServer.verify(0, getRequestedFor(urlEqualTo("/auth/api/user/$username/auth")))
    }

    @Test
    fun `User email endpoint returns user data for external user`() {
      val username = "AUTH_USER"
      hmppsAuthMockServer.stubGetFail("/auth/api/user/$username/authEmail?unverified=false", NOT_FOUND)
      externalUsersApiMockServer.stubUserByUsername(username)
      hmppsAuthMockServer.stubUserIdByUsernameAndSource(username, auth, UUID.randomUUID())

      webTestClient
        .get().uri("/users/$username/email")
        .headers(setAuthorisation(username))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "username" to username,
              "email" to "ext_test@digital.justice.gov.uk",
              "verified" to true,
            ),
          )
        }
      hmppsAuthMockServer.verify(1, getRequestedFor(urlEqualTo("/auth/api/user/$username/auth")))
    }

    @Test
    fun `User email endpoint returns user data for nomis user`() {
      val username = "NUSER_GEN1"
      hmppsAuthMockServer.stubGetFail("/auth/api/user/$username/authEmail?unverified=false", NOT_FOUND)
      stubUserNotFound(username, external = true)
      nomisApiMockServer.stubFindUserByUsername(username)
      hmppsAuthMockServer.stubUserIdByUsernameAndSource(username, nomis, UUID.randomUUID())

      webTestClient
        .get().uri("/users/$username/email")
        .headers(setAuthorisation(username))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "username" to username,
              "email" to "nomis.usergen@digital.justice.gov.uk",
              "verified" to true,
            ),
          )
        }
      hmppsAuthMockServer.verify(1, getRequestedFor(urlEqualTo("/auth/api/user/$username/nomis")))
    }

    @Test
    fun `User email endpoint returns empty for nomis user without email`() {
      val username = "NUSER_GEN"
      hmppsAuthMockServer.stubGetFail("/auth/api/user/$username/authEmail?unverified=true", NOT_FOUND)
      stubUserNotFound(username, external = true)
      nomisApiMockServer.stubFindUserByUsernameNoEmail(username.uppercase())
      hmppsAuthMockServer.stubUserIdByUsernameAndSource(username, nomis, UUID.randomUUID())

      webTestClient
        .get().uri("/users/$username/email?unverified=true")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "username" to username,
              "verified" to false,
            ),
          )
        }
    }

    @Test
    fun `User email endpoint returns user data for delius user`() {
      val username = "delius_smith"
      hmppsAuthMockServer.stubGetFail("/auth/api/user/$username/authEmail?unverified=false", NOT_FOUND)
      stubUserNotFound(username, external = true, nomis = true)
      deliusApiMockServer.stubGetUser(username)
      hmppsAuthMockServer.stubUserIdByUsernameAndSource(username, delius, UUID.randomUUID())

      webTestClient
        .get().uri("/users/$username/email")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "username" to username.uppercase(),
              "email" to "delius.smithy@digital.justice.gov.uk",
              "verified" to true,
            ),
          )
        }
    }

    @Test
    fun `User email endpoint returns user data for azure user`() {
      val username = "ce232d07-40c3-47c6-9903-613bb31132af"
      hmppsAuthMockServer.stubGetFail("/auth/api/user/$username/authEmail?unverified=false", NOT_FOUND)
      stubUserNotFound(username, external = true, nomis = true)
      hmppsAuthMockServer.stubAzureUserByUsername(username)
      hmppsAuthMockServer.stubUserIdByUsernameAndSource(username, azuread, UUID.randomUUID())

      webTestClient
        .get().uri("/users/$username/email")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it).containsExactlyInAnyOrderEntriesOf(
            mapOf(
              "username" to username.uppercase(),
              "email" to "azure.user@justice.gov.uk",
              "verified" to true,
            ),
          )
        }
    }
  }

  @Nested
  inner class MyRoles {
    @Test
    fun `User Me Roles endpoint returns principal user data`() {
      webTestClient
        .get().uri("/users/me/roles")
        .headers(
          setAuthorisation(
            "ITAG_USER",
            listOf("ROLE_MAINTAIN_ACCESS_ROLES", "ROLE_MAINTAIN_OAUTH_USERS", "ROLE_OAUTH_ADMIN"),
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("[*].roleCode").value<List<String>> {
          assertThat(it).contains("MAINTAIN_OAUTH_USERS")
          assertThat(it).contains("OAUTH_ADMIN")
        }
    }

    @Test
    fun `User Me Roles endpoint returns principal user data for auth user`() {
      webTestClient
        .get().uri("/users/me/roles")
        .headers(setAuthorisation("AUTH_ADM", listOf("ROLE_GLOBAL_SEARCH")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("[*].roleCode").value<List<String>> {
          assertThat(it).contains("GLOBAL_SEARCH")
        }
    }

    @Test
    fun `User Me Roles endpoint not accessible without valid token`() {
      webTestClient
        .get().uri("/users/me/roles")
        .exchange()
        .expectStatus().isUnauthorized
    }
  }

  @Nested
  inner class MyGroups {

    private val userId: UUID = UUID.fromString("5E3850B9-9D6E-49D7-B8E7-42874D6CEEA8")

    @Test
    fun `get user groups when no authority`() {
      webTestClient.get().uri("/users/me/groups")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `Users Me groups endpoint returns empty result if user found`() {
      val username = "basicuser"
      stubUserNotFound(username, external = true, nomis = true, delius = true)
      webTestClient
        .get().uri("/users/me/groups")
        .headers(
          setAuthorisation("basicuser"),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$").isEmpty
    }

    @Test
    fun ` get user groups with children`() {
      val username = "AUTH_ADM"
      externalUsersApiMockServer.stubUserByUsername(username, userId)
      externalUsersApiMockServer.stubGetUserGroups(userId, true)

      webTestClient
        .get().uri("/users/me/groups")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .json(
          """
            [
              {
                "groupCode": "SITE_1_GROUP_1",
                "groupName": "Site 1 - Group 1"
              },
              {
                "groupCode": "SITE_2_GROUP_2",
                "groupName": "Site 2 - Group 2"
              }
            ]
          """.trimIndent(),
        )
    }
  }

  @Nested
  inner class UserRoles {
    @Test
    fun `No roles for for valid azure user`() {
      val username = "ce232d07-40c3-47c6-9903-613bb31132af".uppercase(Locale.getDefault())
      val userMessage = "User not found: Account for username $username not found"
      val developerMessage = "Account for username $username not found"
      externalUsersApiMockServer.stubGet(OK, "/users/username/$username/roles", userMessage, developerMessage)
      nomisApiMockServer.stubGetFail("/users/$username", NOT_FOUND)
      hmppsAuthMockServer.stubAzureUserByUsername(username)
      webTestClient.get().uri("/users/$username/roles")
        .headers(setAuthorisation(roles = listOf("ROLE_INTEL_ADMIN")))
        .exchange()
        .expectStatus().isOk
        .expectHeader().contentType(APPLICATION_JSON)
        .expectBody()
        .jsonPath("$").value<List<Any>> {
          assertThat(it).isEmpty()
        }
    }

    @Test
    fun `Roles of valid nomis user`() {
      val username = "NUSER_GEN"
      val userMessage = "User not found: Account for username $username not found"
      val developerMessage = "Account for username $username not found"
      externalUsersApiMockServer.stubGet(OK, "/users/username/$username/roles", userMessage, developerMessage)
      nomisApiMockServer.stubFindUserByUsername(username)
      callApiWithRole(username, "ROLE_PCMS_USER_ADMIN")
        .jsonPath("[*].roleCode").value<List<String>> {
          assertThat(it).contains("MAINTAIN_ACCESS_ROLES")
          assertThat(it).contains("GLOBAL_SEARCH")
          assertThat(it).contains("HMPPS_REGISTERS_MAINTAINER")
          assertThat(it).contains("HPA_USER")
        }
    }

    @Test
    fun `Can get a list of roles of valid nomis user with the ROLE_USER_PERMISSIONS__RO role`() {
      val username = "NUSER_GEN"
      val userMessage = "User not found: Account for username $username not found"
      val developerMessage = "Account for username $username not found"
      externalUsersApiMockServer.stubGet(OK, "/users/username/$username/roles", userMessage, developerMessage)
      nomisApiMockServer.stubFindUserByUsername(username)
      callApiWithRole(username, "ROLE_USER_PERMISSIONS__RO")
        .jsonPath("[*].roleCode").value<List<String>> {
          assertThat(it).contains("MAINTAIN_ACCESS_ROLES")
          assertThat(it).contains("GLOBAL_SEARCH")
          assertThat(it).contains("HMPPS_REGISTERS_MAINTAINER")
          assertThat(it).contains("HPA_USER")
        }
    }

    @Test
    fun ` external user found success`() {
      val username = "EXT_ADM"
      val uuid = UUID.randomUUID()
      externalUsersApiMockServer.stubGetSearchableRoles("/users/username/$username/roles")
      hmppsAuthMockServer.stubUserIdByUsernameAndSource(username, auth, uuid)
      callApiWithRole(username, "ROLE_PCMS_USER_ADMIN")
        .jsonPath("[*].roleCode").value<List<String>> {
          assertThat(it).contains("PF_POLICE")
        }
    }

    private fun callApiWithRole(username: String, role: String): WebTestClient.BodyContentSpec = webTestClient.get().uri("/users/$username/roles")
      .headers(setAuthorisation(roles = listOf(role)))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(APPLICATION_JSON)
      .expectBody()

    @Test
    fun `access forbidden when no authority`() {
      webTestClient.get().uri("/users/username/roles")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should fail with not_found for invalid username`() {
      val username = "AUTH_ADM"
      externalUsersApiMockServer.stubGetFail("/users/username/$username/roles", NOT_FOUND)
      nomisApiMockServer.stubGetFail("/users/$username", NOT_FOUND)
      deliusApiMockServer.stubGetFail("/user/$username", NOT_FOUND)
      webTestClient.get().uri("/users/$username/roles")
        .headers(setAuthorisation(roles = listOf("ROLE_PCMS_USER_ADMIN")))
        .exchange()
        .expectStatus().isNotFound
        .expectHeader().contentType(APPLICATION_JSON)
        .expectBody()
        .jsonPath("$").value<Map<String, Any>> {
          assertThat(it["error"]).isEqualTo("Not Found")
          assertThat(it["error_description"]).isEqualTo("Account for username $username not found")
          assertThat(it["field"]).isEqualTo("username")
        }
      hmppsAuthMockServer.verify(0, getRequestedFor(urlEqualTo("/auth/api/azureuser/$username")))
    }

    @Test
    fun `says whether each user has IMS_USER, in the order given`() {
      externalUsersApiMockServer.stubFor(
        get(urlEqualTo("/users/username/IMS_USER1/roles")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""[{ "roleCode": "IMS_USER", "roleName": "IMS user" }]"""),
        ),
      )
      externalUsersApiMockServer.stubGetSearchableRoles("/users/username/EXT_ADM/roles")
      externalUsersApiMockServer.stubGetFail("/users/username/AUTH_ADM/roles", NOT_FOUND)
      nomisApiMockServer.stubGetFail("/users/AUTH_ADM", NOT_FOUND)
      deliusApiMockServer.stubGetFail("/user/AUTH_ADM", NOT_FOUND)

      webTestClient.post().uri("/users/has-role")
        .headers(setAuthorisation(roles = listOf("ROLE_INTEL_ADMIN")))
        .body(fromValue(mapOf("usernames" to listOf("IMS_USER1", "EXT_ADM", "AUTH_ADM"))))
        .exchange()
        .expectStatus().isOk
        .expectHeader().contentType(APPLICATION_JSON)
        .expectBody().json(
          """
          {
            "content": [
              {"username": "IMS_USER1", "hasRole": true },
              {"username": "EXT_ADM", "hasRole": false },
              {"username": "AUTH_ADM", "hasRole": false }
            ],
            "totalElements": 3,
            "totalPages": 1,
            "number": 0,
            "size": 10,
            "first": true,
            "last": true,
          }
          """.trimIndent(),
        )
    }

    @Test
    fun `accepts 50 usernames`() {
      externalUsersApiMockServer.stubGetSearchableRoles("/users/username/EXT_ADM/roles")

      webTestClient.post().uri("/users/has-role")
        .headers(setAuthorisation(roles = listOf("ROLE_INTEL_ADMIN")))
        .body(fromValue(mapOf("usernames" to List(50) { "EXT_ADM" })))
        .exchange()
        .expectStatus().isOk
    }

    @Test
    fun `bad request when more than 50 usernames are given`() {
      webTestClient.post().uri("/users/has-role")
        .headers(setAuthorisation(roles = listOf("ROLE_INTEL_ADMIN")))
        .body(fromValue(mapOf("usernames" to List(51) { "USER_$it" })))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `bad request when no usernames are given`() {
      webTestClient.post().uri("/users/has-role")
        .headers(setAuthorisation(roles = listOf("ROLE_INTEL_ADMIN")))
        .body(fromValue(mapOf("usernames" to emptyList<String>())))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `bad request when a username is blank`() {
      webTestClient.post().uri("/users/has-role")
        .headers(setAuthorisation(roles = listOf("ROLE_INTEL_ADMIN")))
        .body(fromValue(mapOf("usernames" to listOf("EXT_ADM", " "))))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `unauthorised when there is no token`() {
      webTestClient.post().uri("/users/has-role")
        .body(fromValue(mapOf("usernames" to listOf("EXT_ADM"))))
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `forbidden without ROLE_INTEL_ADMIN`() {
      webTestClient.post().uri("/users/has-role")
        .headers(setAuthorisation(roles = listOf("ROLE_PCMS_USER_ADMIN")))
        .body(fromValue(mapOf("usernames" to listOf("EXT_ADM"))))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `returns the upstream error rather than false when a lookup fails`() {
      externalUsersApiMockServer.stubGetFail("/users/username/IMS_USER1/roles", SERVICE_UNAVAILABLE)

      webTestClient.post().uri("/users/has-role")
        .headers(setAuthorisation(roles = listOf("ROLE_INTEL_ADMIN")))
        .body(fromValue(mapOf("usernames" to listOf("IMS_USER1"))))
        .exchange()
        .expectStatus().isEqualTo(SERVICE_UNAVAILABLE)

      nomisApiMockServer.verify(0, getRequestedFor(urlPathEqualTo("/users/IMS_USER1")))
    }
  }

  @Nested
  inner class MappedDeliusRoles {
    @Test
    fun `User Me Roles endpoint returns principal user data for auth user`() {
      webTestClient
        .get().uri("/roles/delius")
        .headers(setAuthorisation("AUTH_ADM", listOf()))
        .exchange()
        .expectStatus().isOk
        .expectBody().json(
          """
                         {
                              "TEST_ROLE": 
                                [
                                    "ROLE_LICENCE_RO",
                                    "ROLE_GLOBAL_SEARCH"
                                ],
                              "TEST_WORKLOAD_MEASUREMENT_ROLE": 
                                [
                                    "ROLE_WORKLOAD_MEASUREMENT"
                                ]
                        }
                        """,
        )
    }

    @Test
    fun `access forbidden when no authority`() {
      webTestClient.get().uri("/roles/delius?deliusRoles=NON_MAPPED_ROLE")
        .exchange()
        .expectStatus().isUnauthorized
    }
  }

  @Nested
  inner class MyCaseloads {

    private val userId: UUID = UUID.fromString("5E3850B9-9D6E-49D7-B8E7-42874D6CEEA8")

    @Test
    fun `get caseloads when no authority`() {
      webTestClient.get().uri("/users/me/caseloads")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `get user caseloads`() {
      val username = "NUSER_GEN"
      externalUsersApiMockServer.stubUserByUsername(username, userId)
      nomisApiMockServer.stubFindUserCaseloads(username)

      webTestClient
        .get().uri("/users/me/caseloads")
        .headers(setAuthorisation())
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .json(
          """
            {
               "username": "$username",
               "active": true,
               "accountType": "GENERAL",
               "activeCaseload": {
                 "id": "WWI",
                 "name": "WANDSWORTH (HMP)",
                 "function": "GENERAL"
               },
               "caseloads": [
                 {
                   "id": "WWI",
                   "name": "WANDSWORTH (HMP)",
                   "function": "GENERAL"
                 }
               ]
             }
          """.trimIndent(),
        )
    }
  }

  @Nested
  inner class UpdateMyActiveCaseload {

    @Test
    fun `update active caseload is not accessible without a valid token`() {
      webTestClient
        .put().uri("/users/me/activeCaseLoad")
        .body(fromValue(mapOf("caseLoadId" to "WWI")))
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `update active caseload when user has access`() {
      val username = "NUSER_GEN"
      nomisApiMockServer.stubFindUserCaseloads(username)
      nomisApiMockServer.stubPut("/users/$username/default-caseload/wwi", OK)

      webTestClient
        .put().uri("/users/me/activeCaseLoad")
        .headers(setAuthorisation(username))
        .body(fromValue(mapOf("caseLoadId" to "wwi")))
        .exchange()
        .expectStatus().isOk
        .expectBody().isEmpty

      nomisApiMockServer.verify(
        getRequestedFor(urlEqualTo("/me/caseloads"))
          .withHeader("Authorization", matching(".+")),
      )
      nomisApiMockServer.verify(
        1,
        putRequestedFor(urlEqualTo("/users/$username/default-caseload/wwi"))
          .withHeader("Authorization", matching(".+")),
      )
    }

    @Test
    fun `update active caseload is forbidden when user does not have access`() {
      val username = "NUSER_GEN"
      val caseLoadId = "MDI"
      nomisApiMockServer.stubFindUserCaseloads(username)

      webTestClient
        .put().uri("/users/me/activeCaseLoad")
        .headers(setAuthorisation(username))
        .body(fromValue(mapOf("caseLoadId" to caseLoadId)))
        .exchange()
        .expectStatus().isForbidden
        .expectHeader().contentType(APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.status").isEqualTo(403)
        .jsonPath("$.userMessage").isEqualTo("The user does not have access to the caseLoadId = $caseLoadId")
        .jsonPath("$.developerMessage").isEqualTo("The user does not have access to the caseLoadId = $caseLoadId")

      nomisApiMockServer.verify(
        0,
        putRequestedFor(urlEqualTo("/users/$username/default-caseload/$caseLoadId")),
      )
    }

    @Test
    fun `update active caseload rejects a request without a caseload ID`() {
      webTestClient
        .put().uri("/users/me/activeCaseLoad")
        .headers(setAuthorisation("NUSER_GEN"))
        .body(fromValue(emptyMap<String, String>()))
        .exchange()
        .expectStatus().isBadRequest
        .expectHeader().contentType(APPLICATION_JSON)
    }

    @Test
    fun `update active caseload rejects a blank caseload ID`() {
      webTestClient
        .put().uri("/users/me/activeCaseLoad")
        .headers(setAuthorisation("NUSER_GEN"))
        .body(fromValue(mapOf("caseLoadId" to "   ")))
        .exchange()
        .expectStatus().isBadRequest
        .expectHeader().contentType(APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.status").isEqualTo(400)
        .jsonPath("$.userMessage").isEqualTo("Validation failure: caseLoadId must not be blank")
    }

    @Test
    fun `update active caseload propagates a NOMIS failure`() {
      val username = "NUSER_GEN"
      nomisApiMockServer.stubFindUserCaseloads(username)
      nomisApiMockServer.stubPut("/users/$username/default-caseload/WWI", SERVICE_UNAVAILABLE)

      webTestClient
        .put().uri("/users/me/activeCaseLoad")
        .headers(setAuthorisation(username))
        .body(fromValue(mapOf("caseLoadId" to "WWI")))
        .exchange()
        .expectStatus().isEqualTo(SERVICE_UNAVAILABLE)
        .expectHeader().contentType(APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.status").isEqualTo(SERVICE_UNAVAILABLE.value())
        .jsonPath("$.userMessage").isEqualTo("Nomis User message for PUT failed")
        .jsonPath("$.developerMessage").isEqualTo("Developer Nomis user message for PUT failed")
    }
  }

  @Nested
  inner class FindByCaseloadAndRole {

    @BeforeEach
    internal fun setup() {
      nomisApiMockServer.resetAll()
    }

    @Test
    fun `should return only users matching active caseload`() {
      val status = "ACTIVE"
      val roles = listOf("ADD_SENSITIVE_CASE_NOTES")
      val caseload = "MDI"
      nomisApiMockServer.stubUsersByRoleAndActiveCaseload(roles, caseload)

      webTestClient
        .get()
        .uri { builder ->
          builder.path("/prisonusers/find-by-caseload-and-role")
            .queryParam("status", status)
            .queryParam("activeCaseload", caseload)
            .queryParam("roleCode", roles.joinToString(","))
            .queryParam("activeCaseloadOnly", true)
            .build()
        }
        .headers(setAuthorisation(roles = listOf("ROLE_USERS__PRISON_USERS__FIND_BY_CASELOAD_AND_ROLE__RO")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .assertUserByRoleAndCaseloadResponse(
          firstName = "Maggie",
          lastName = "Simpson",
          caseload = caseload,
        )

      nomisApiMockServer.verify(
        1,
        getRequestedFor(urlPathEqualTo("/users"))
          .withHeader("Authorization", matching("Bearer .+"))
          .withQueryParam("status", equalTo("ACTIVE"))
          .withQueryParam("activeCaseload", equalTo(caseload))
          .withQueryParam("accessRoles", equalTo(roles.joinToString(",")))
          .withoutQueryParam("caseload"),
      )
    }
  }

  @Test
  fun `should default to users with active caseload when 'activeCaseloadOnly' param is not specified`() {
    val status = "ACTIVE"
    val roles = listOf("ADD_SENSITIVE_CASE_NOTES")
    val caseload = "MDI"
    nomisApiMockServer.stubUsersByRoleAndActiveCaseload(roles, caseload)

    webTestClient
      .get()
      .uri { builder ->
        builder.path("/prisonusers/find-by-caseload-and-role")
          .queryParam("status", status)
          .queryParam("activeCaseload", caseload)
          .queryParam("roleCode", roles.joinToString(","))
          .build()
      }
      .headers(setAuthorisation(roles = listOf("ROLE_USERS__PRISON_USERS__FIND_BY_CASELOAD_AND_ROLE__RO")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .assertUserByRoleAndCaseloadResponse(
        firstName = "Maggie",
        lastName = "Simpson",
        caseload = caseload,
      )

    nomisApiMockServer.verify(
      1,
      getRequestedFor(urlPathEqualTo("/users"))
        .withHeader("Authorization", matching("Bearer .+"))
        .withQueryParam("status", equalTo("ACTIVE"))
        .withQueryParam("activeCaseload", equalTo(caseload))
        .withQueryParam("accessRoles", equalTo(roles.joinToString(",")))
        .withoutQueryParam("caseload"),
    )
  }

  @Test
  fun `should return any user with the specified caseload irrespective of it being their currently active caseload`() {
    val status = "ACTIVE"
    val roles = listOf("ADD_SENSITIVE_CASE_NOTES")
    val caseload = "MDI"
    nomisApiMockServer.stubUsersByRoleAndCaseload(roles, caseload)

    webTestClient
      .get()
      .uri { builder ->
        builder.path("/prisonusers/find-by-caseload-and-role")
          .queryParam("status", status)
          .queryParam("activeCaseload", caseload)
          .queryParam("roleCode", roles.joinToString(","))
          .queryParam("activeCaseloadOnly", false)
          .build()
      }
      .headers(setAuthorisation(roles = listOf("ROLE_USERS__PRISON_USERS__FIND_BY_CASELOAD_AND_ROLE__RO")))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .assertUserByRoleAndCaseloadResponse(
        firstName = "Homer",
        lastName = "Simpson",
        caseload = caseload,
      )

    nomisApiMockServer.verify(
      1,
      getRequestedFor(urlPathEqualTo("/users"))
        .withHeader("Authorization", matching("Bearer .+"))
        .withQueryParam("status", equalTo("ACTIVE"))
        .withQueryParam("caseload", equalTo(caseload))
        .withQueryParam("accessRoles", equalTo(roles.joinToString(",")))
        .withoutQueryParam("activeCaseload"),
    )
  }

  internal fun WebTestClient.BodyContentSpec.assertUserByRoleAndCaseloadResponse(
    firstName: String,
    lastName: String,
    caseload: String,
  ) {
    this.jsonPath("$.totalPages").isEqualTo(1)
      .jsonPath("$.totalElements").isEqualTo(1)
      .jsonPath("$.numberOfElements").isEqualTo(1)
      .jsonPath("$.empty").isEqualTo(false)
      .jsonPath("$.content[0].username").isEqualTo("$firstName.$lastName")
      .jsonPath("$.content[0].staffId").isEqualTo(100)
      .jsonPath("$.content[0].firstName").isEqualTo(firstName)
      .jsonPath("$.content[0].lastName").isEqualTo(lastName)
      .jsonPath("$.content[0].active").isEqualTo(true)
      .jsonPath("$.content[0].status").isEqualTo("ACTIVE")
      .jsonPath("$.content[0].locked").isEqualTo(false)
      .jsonPath("$.content[0].expired").isEqualTo(false)
      .jsonPath("$.content[0].activeCaseload.id").isEqualTo(caseload)
      .jsonPath("$.content[0].activeCaseload.name").isEqualTo("$caseload (HMP)")
      .jsonPath("$.content[0].dpsRoleCount").isEqualTo(0)
      .jsonPath("$.content[0].staffStatus").isEqualTo("ACTIVE")
  }
}

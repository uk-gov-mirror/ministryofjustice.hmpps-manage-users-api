package uk.gov.justice.digital.hmpps.manageusersapi.adapter.auth

import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.manageusersapi.adapter.WebClientUtils
import uk.gov.justice.digital.hmpps.manageusersapi.model.AuthService
import uk.gov.justice.digital.hmpps.manageusersapi.model.AuthSource
import uk.gov.justice.digital.hmpps.manageusersapi.model.AuthUser
import uk.gov.justice.digital.hmpps.manageusersapi.model.AzureUser
import uk.gov.justice.digital.hmpps.manageusersapi.model.EmailAddress
import uk.gov.justice.digital.hmpps.manageusersapi.resource.PagedResponse
import uk.gov.justice.digital.hmpps.manageusersapi.resource.UserAllowlistAddRequest
import uk.gov.justice.digital.hmpps.manageusersapi.resource.UserAllowlistDetail
import uk.gov.justice.digital.hmpps.manageusersapi.resource.UserAllowlistPatchRequest
import uk.gov.justice.digital.hmpps.manageusersapi.resource.UserAllowlistUserType
import uk.gov.justice.digital.hmpps.manageusersapi.service.Status
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.manageusersapi.service.auth.Status as AllowListStatus

@Service
class AuthApiService(
  @Qualifier("authWebClientUtils") val serviceWebClientUtils: WebClientUtils,
  @Qualifier("authUserWebClientUtils") val userWebClientUtils: WebClientUtils,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun createNewToken(createTokenRequest: CreateTokenRequest): String {
    log.debug("Create Token for user, {}", createTokenRequest.username)
    return serviceWebClientUtils.postWithResponse(
      "/api/new-token",
      mapOf(
        "username" to createTokenRequest.username,
        "email" to createTokenRequest.email,
        "source" to createTokenRequest.source,
        "firstName" to createTokenRequest.firstName,
        "lastName" to createTokenRequest.lastName,
      ),
      String::class.java,
    )
  }

  fun createTokenByEmailType(tokenByEmailTypeRequest: TokenByEmailTypeRequest): String {
    log.debug("Create Token for user ${tokenByEmailTypeRequest.username} with email type ${tokenByEmailTypeRequest.emailType}")
    return serviceWebClientUtils.postWithResponse(
      "/api/token/email-type",
      mapOf("username" to tokenByEmailTypeRequest.username, "emailType" to tokenByEmailTypeRequest.emailType),
      String::class.java,
    )
  }

  fun createResetTokenForUser(userId: UUID) = serviceWebClientUtils.postWithResponse("/api/token/reset/{userId}", String::class.java, userId)

  fun findAuthUserEmail(username: String, unverified: Boolean) = serviceWebClientUtils.getIgnoreError("/api/user/{username}/authEmail?unverified={unverified}", EmailAddress::class.java, username, unverified)

  fun findAzureUserByUsername(username: String): AzureUser? = try {
    UUID.fromString(username)
    serviceWebClientUtils.getIgnoreError("/api/azureuser/{username}", AzureUser::class.java, username)
  } catch (_: IllegalArgumentException) {
    log.info("Auth not called for Azure user as username not valid UUID: {}", username)
    null
  }

  fun findAzureUserByUsernameIfPresent(username: String): AzureUser? = try {
    UUID.fromString(username)
    serviceWebClientUtils.getIfPresent("/api/azureuser/{username}", AzureUser::class.java, username)
  } catch (_: IllegalArgumentException) {
    log.info("Auth not called for Azure user as username not valid UUID: {}", username)
    null
  }

  fun findServiceByServiceCode(serviceCode: String) = serviceWebClientUtils.get("/api/services/{serviceCode}", AuthService::class.java, serviceCode)

  fun findUserIdByUsernameAndSource(username: String, source: AuthSource): AuthUser = serviceWebClientUtils.get("/api/user/{username}/{source}", AuthUser::class.java, username, source)

  fun syncUserEmailUpdate(username: String, newEmail: String, newUsername: String) = serviceWebClientUtils.putWithBody(mapOf("email" to newEmail, "username" to newUsername), "/api/externaluser/sync/{username}/email", username)

  fun syncUserEnabled(username: String) = serviceWebClientUtils.putWithBody(mapOf("enabled" to true), "/api/externaluser/sync/{username}/enabled", username)

  fun syncExternalUserCreate(email: String, firstName: String, lastName: String) = serviceWebClientUtils.postWithBody(mapOf("email" to email, "username" to email, "firstName" to firstName, "lastName" to lastName), "/api/externaluser/sync/create")

  fun syncUserDisabled(username: String, inactiveReason: String) = serviceWebClientUtils.putWithBody(mapOf("enabled" to false, "inactiveReason" to inactiveReason), "/api/externaluser/sync/{username}/enabled", username)

  fun confirmRecognised(username: String) = userWebClientUtils.getWithEmptyResponseSucceeds("/api/user/{username}/recognised", username)

  fun syncEmailWithNomis(username: String, nomisEmail: String?) = userWebClientUtils.postWithBody(mapOf("email" to nomisEmail), "/api/prisonuser/{username}/email/sync", username)

  fun updateEmail(username: String, newEmailAddress: String) = userWebClientUtils.putWithBody(mapOf("email" to newEmailAddress), "/api/prisonuser/{username}/email", username)

  fun findUserEmails(usernames: List<String>): List<EmailAddress> = serviceWebClientUtils.postWithResponse(
    "/api/prisonuser/email",
    usernames,
    EmailList::class.java,
  )

  fun findUsers(
    name: String?,
    status: Status?,
    authSources: List<AuthSource>?,
    page: Int?,
    size: Int?,
    sort: String?,
  ) = userWebClientUtils.getWithParams(
    "/api/user/search",
    object : ParameterizedTypeReference<PagedResponse<AuthUserDto>>() {},
    mapNonNull(
      "name" to name,
      "status" to status,
      "authSources" to authSources,
      "page" to page,
      "size" to size,
      "sort" to sort,
    ),
  )

  fun addUserToAllowlist(userAllowlistAddRequest: UserAllowlistAddRequest) = userWebClientUtils.postWithBody(userAllowlistAddRequest, "/api/user/allowlist")

  fun getAllAllowlistUsers(name: String?, status: AllowListStatus, userType: UserAllowlistUserType?, pageable: Pageable) = userWebClientUtils.getWithParams(
    "/api/user/allowlist",
    object : ParameterizedTypeReference<PagedResponse<UserAllowlistDetail>>() {},
    mapNonNull(
      "name" to name,
      "status" to status,
      "userType" to userType,
      "page" to pageable.pageNumber,
      "size" to pageable.pageSize,
    ),
  )

  fun getAllowlistUser(username: String): UserAllowlistDetail = userWebClientUtils.get(
    "/api/user/allowlist/{username}",
    UserAllowlistDetail::class.java,
    username,
  )

  fun updateAllowlistUserAccess(id: UUID, updateUserAccessRequest: UserAllowlistPatchRequest) {
    userWebClientUtils.patchWithBody(
      updateUserAccessRequest,
      "/api/user/allowlist/{id}",
      id,
    )
  }
}

fun <K, V> mapNonNull(vararg pairs: Pair<K, V>) = mapOf(*pairs).filterValues { it != null }

class EmailList : MutableList<EmailAddress> by ArrayList()

data class TokenByEmailTypeRequest(
  val username: String,
  val emailType: String,
)

data class CreateTokenRequest(
  val username: String,
  val email: String,
  val source: String,
  val firstName: String,
  val lastName: String,
)

data class AuthUserDto(
  @Schema(description = "User ID", example = "91229A16-B5F4-4784-942E-A484A97AC865")
  val userId: String? = null,

  @Schema(description = "Username", example = "externaluser")
  val username: String? = null,

  @Schema(description = "Email address", example = "external.user@someagency.justice.gov.uk")
  val email: String? = null,

  @Schema(description = "First name", example = "External")
  val firstName: String? = null,

  @Schema(description = "Last name", example = "User")
  val lastName: String? = null,

  @Schema(description = "Account is locked due to incorrect password attempts", example = "true")
  val locked: Boolean,

  @Schema(required = true, description = "Account is enabled", example = "false")
  val enabled: Boolean,

  @Schema(required = true, description = "Email address has been verified", example = "false")
  val verified: Boolean,

  @Schema(required = true, description = "Last time user logged in", example = "01/01/2001")
  val lastLoggedIn: LocalDateTime? = null,

  @Schema(required = true, description = "Authentication source", example = "delius")
  val source: String,
)

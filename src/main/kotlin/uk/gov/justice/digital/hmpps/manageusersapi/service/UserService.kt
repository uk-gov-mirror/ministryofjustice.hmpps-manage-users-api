package uk.gov.justice.digital.hmpps.manageusersapi.service

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.manageusersapi.adapter.auth.AuthApiService
import uk.gov.justice.digital.hmpps.manageusersapi.adapter.delius.UserApiService
import uk.gov.justice.digital.hmpps.manageusersapi.adapter.external.UserRolesApiService
import uk.gov.justice.digital.hmpps.manageusersapi.adapter.external.UserSearchApiService
import uk.gov.justice.digital.hmpps.manageusersapi.adapter.nomis.RolesApiService
import uk.gov.justice.digital.hmpps.manageusersapi.model.AuthSource
import uk.gov.justice.digital.hmpps.manageusersapi.model.EmailAddress
import uk.gov.justice.digital.hmpps.manageusersapi.model.GenericUser
import uk.gov.justice.digital.hmpps.manageusersapi.model.UserCaseloadDetail
import uk.gov.justice.digital.hmpps.manageusersapi.resource.ActiveCaseLoad
import uk.gov.justice.digital.hmpps.manageusersapi.resource.UserHasRoleResponse
import uk.gov.justice.digital.hmpps.manageusersapi.resource.UserRole
import uk.gov.justice.digital.hmpps.manageusersapi.resource.external.UserGroupDto
import uk.gov.justice.digital.hmpps.manageusersapi.service.external.UserGroupService
import uk.gov.justice.hmpps.kotlin.auth.HmppsAuthenticationHolder
import java.util.UUID

@Service
class UserService(
  private val authApiService: AuthApiService,
  private val deliusApiService: UserApiService,
  private val externalUsersSearchApiService: UserSearchApiService,
  private val prisonApiService: uk.gov.justice.digital.hmpps.manageusersapi.adapter.nomis.UserApiService,
  private val hmppsAuthenticationHolder: HmppsAuthenticationHolder,
  private val externalRolesApiService: UserRolesApiService,
  private val userGroupsService: UserGroupService,
  private val nomisRolesApiService: RolesApiService,
) {
  fun findUserByUsername(username: String): GenericUser? = findMasterUserBasicDetails(username)?.toGenericUser()?.apply {
    val authUserId = authApiService.findUserIdByUsernameAndSource(username, this.authSource)
    this.uuid = authUserId.uuid
  }

  fun findUserByUsernameWithAuthSource(username: String): GenericUser? = when (AuthSource.valueOf(hmppsAuthenticationHolder.authSource.toString().lowercase())) {
    AuthSource.auth -> externalUsersSearchApiService.findUserByUsernameOrNull(username)?.toGenericUser()
    AuthSource.nomis -> prisonApiService.findUserBasicDetailsByUsername(username)?.toGenericUser()
    AuthSource.azuread -> authApiService.findAzureUserByUsername(username)?.toGenericUser()
    AuthSource.delius -> deliusApiService.findUserByUsername(username)?.toGenericUser()
    AuthSource.none -> null
  }?.apply {
    val authUserId = authApiService.findUserIdByUsernameAndSource(username, authSource)
    this.uuid = authUserId.uuid
  }

  fun findGroupDetails(username: String): List<UserGroupDto> = externalUsersSearchApiService.findUserByUsernameOrNull(username).let { user ->
    user?.let { getUserGroups(it.userId) }
  } ?: emptyList()
  private fun getUserGroups(uuid: UUID) = userGroupsService.getUserGroups(uuid, true).map { UserGroupDto.fromDomain(it) }
  private fun findMasterUserBasicDetails(username: String) = externalUsersSearchApiService.findUserByUsernameOrNull(username)
    ?: run {
      prisonApiService.findUserBasicDetailsByUsername(username)
        ?: run {
          authApiService.findAzureUserByUsername(username)
            ?: run {
              deliusApiService.findUserByUsername(username)
            }
        }
    }

  private fun findMasterUser(username: String) = externalUsersSearchApiService.findUserByUsernameOrNull(username)
    ?: run {
      prisonApiService.findUserByUsername(username)
        ?: run {
          authApiService.findAzureUserByUsername(username)
            ?: run {
              deliusApiService.findUserByUsername(username)
            }
        }
    }

  fun findUserEmail(username: String, unverified: Boolean): EmailAddress? = authApiService.findAuthUserEmail(username, unverified)
    ?: run {
      findMasterUser(username)?.let { masterUser ->
        val userEmail = masterUser.emailAddress()
        // save back to auth (this just mimics how auth currently works)
        authApiService.findUserIdByUsernameAndSource(username, masterUser.authSource)
        userEmail
      }
    }

  /**
   * Says whether each user on one page of [usernames] has the IMS_USER role.
   *
   * Duplicates are removed before paging and the original order is kept, so the same list and page always give the
   * same users. Only the users on the requested page are looked up upstream. A user who isn't found gets false.
   *
   * Unlike [findRolesByUsername], an upstream error other than 404 is not treated as "not found", it fails the
   * request, so a caller is never told a user lacks the role when the lookup didn't actually succeed.
   */
  fun usersHaveIMSRole(usernames: List<String>, pageable: Pageable): Page<UserHasRoleResponse> {
    val distinctUsernames = usernames.distinct()
    val from = minOf(pageable.offset, distinctUsernames.size.toLong()).toInt()
    val to = minOf(from + pageable.pageSize, distinctUsernames.size)

    val results = distinctUsernames.subList(from, to).map { username ->
      val roles = findRolesByUsernameIfPresent(username).orEmpty()
      UserHasRoleResponse(username = username, hasRole = roles.any { it.roleCode == IMS_USER_ROLE })
    }
    return PageImpl(results, pageable, distinctUsernames.size.toLong())
  }

  fun findRolesByUsername(username: String): List<UserRole>? {
    return externalRolesApiService.findRolesByUsernameOrNull(username)?.map { UserRole(it.roleCode) }
      ?: run { prisonApiService.findUserByUsername(username)?.roles?.map { UserRole(it) } }
      ?: run { authApiService.findAzureUserByUsername(username)?.roles?.map { UserRole(it.name) } }
      ?: run { deliusApiService.findUserByUsername(username)?.roles?.map { UserRole(it.substring(5)) } } // remove ROLE_
  }

  private fun findRolesByUsernameIfPresent(username: String): List<UserRole>? = externalRolesApiService.findRolesByUsernameIfPresent(username)?.map { UserRole(it.roleCode) }
    ?: prisonApiService.findUserByUsernameIfPresent(username)?.roles?.map { UserRole(it) }
    ?: authApiService.findAzureUserByUsernameIfPresent(username)?.roles?.map { UserRole(it.name) }
    ?: deliusApiService.findUserByUsernameIfPresent(username)?.roles?.map { UserRole(it.substring(5)) }

  fun getAllDeliusRoles() = deliusApiService.getAllDeliusRoles()

  fun myRoles() = hmppsAuthenticationHolder.authentication.authorities
    .mapNotNull { it.authority }
    .filter { it.startsWith("ROLE_") }
    .map { ExternalUserRole(it.substring(5)) }

  fun updateMyActiveCaseload(requestedActiveCaseload: ActiveCaseLoad) {
    val myCaseloads = getCaseloads()
    if (myCaseloads.caseloads.stream().anyMatch { cl ->
        cl.id.equals(
          requestedActiveCaseload.caseLoadId,
          ignoreCase = true,
        )
      }
    ) {
      nomisRolesApiService.setDefaultCaseload(hmppsAuthenticationHolder.username!!, requestedActiveCaseload.caseLoadId)
    } else {
      throw AccessDeniedException(String.format("The user does not have access to the caseLoadId = %s", requestedActiveCaseload.caseLoadId))
    }
  }

  fun getCaseloads(): UserCaseloadDetail = nomisRolesApiService.getCaseloads()
}

@Schema(description = "User Role")
data class ExternalUserRole(
  @Schema(required = true, description = "Role Code", example = "GLOBAL_SEARCH")
  val roleCode: String,
)

private const val IMS_USER_ROLE = "IMS_USER"

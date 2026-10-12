package uk.gov.justice.digital.hmpps.manageusersapi.adapter.external

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.manageusersapi.adapter.WebClientUtils
import uk.gov.justice.digital.hmpps.manageusersapi.resource.external.UserRole
import java.util.UUID

@Service
class UserRolesApiService(
  @Qualifier("externalUsersUserWebClientUtils") val userWebClientUtils: WebClientUtils,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getUserRoles(userId: UUID): List<UserRole> = userWebClientUtils.get("/users/{userId}/roles", UserRoleList::class.java, userId)

  fun addRolesByUserId(userId: UUID, roleCodes: List<String>) {
    log.debug("Adding roles {} for user {}", roleCodes, userId)
    userWebClientUtils.postWithBody(roleCodes, "/users/{userId}/roles", userId)
  }

  fun deleteRoleByUserId(userId: UUID, role: String) {
    log.debug("Delete role {} for user {}", role, userId)
    userWebClientUtils.delete("/users/{userId}/roles/{role}", userId, role)
  }

  fun getAssignableRoles(userId: UUID) = userWebClientUtils.get("/users/{userId}/assignable-roles", UserRoleList::class.java, userId)

  fun getAllSearchableRoles() = userWebClientUtils.get("/users/me/searchable-roles", UserRoleList::class.java)

  fun findRolesByUsernameOrNull(userName: String): List<UserRole>? = userWebClientUtils.getIgnoreError("/users/username/{userName}/roles", UserRoleList::class.java, userName)

  fun findRolesByUsernameIfPresent(userName: String): List<UserRole>? = userWebClientUtils.getIfPresent("/users/username/{userName}/roles", UserRoleList::class.java, userName)
}

class UserRoleList : MutableList<UserRole> by ArrayList()

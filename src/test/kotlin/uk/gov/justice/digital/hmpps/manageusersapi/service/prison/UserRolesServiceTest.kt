package uk.gov.justice.digital.hmpps.manageusersapi.service.prison

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.manageusersapi.adapter.external.RolesApiService
import uk.gov.justice.digital.hmpps.manageusersapi.model.AdminTypeReturn
import uk.gov.justice.digital.hmpps.manageusersapi.model.PrisonCaseload
import uk.gov.justice.digital.hmpps.manageusersapi.model.PrisonRole
import uk.gov.justice.digital.hmpps.manageusersapi.model.PrisonRoleType
import uk.gov.justice.digital.hmpps.manageusersapi.model.PrisonUserRole
import uk.gov.justice.digital.hmpps.manageusersapi.model.Role
import uk.gov.justice.digital.hmpps.manageusersapi.resource.prison.CaseloadRoleDetail
import uk.gov.justice.digital.hmpps.manageusersapi.resource.prison.RoleDetail
import uk.gov.justice.digital.hmpps.manageusersapi.resource.prison.UsageType
import uk.gov.justice.digital.hmpps.manageusersapi.resource.prison.UserRoleDetail
import uk.gov.justice.digital.hmpps.manageusersapi.adapter.nomis.RolesApiService as PrisonRolesApiService

class UserRolesServiceTest {
  private val externalRolesApiService: RolesApiService = mock()
  private val prisonRolesApiService: PrisonRolesApiService = mock()
  private val userRolesService = UserRolesService(externalRolesApiService, prisonRolesApiService)

  @Test
  fun `get user roles`() {
    val userRolesFromNomis = createUserRoleDetails()
    val rolesFromExternalUsers = listOf(
      Role(
        roleCode = "OMIC_ADMIN",
        roleName = "Key-worker allocator",
        roleDescription = null,
        adminType = listOf(AdminTypeReturn("DPS_ADM", "DPS Central Administrator")),
      ),
      Role(
        roleCode = "MAINTAIN_ACCESS_ROLES",
        roleName = "Maintain DPS user roles",
        roleDescription = null,
        adminType = listOf(AdminTypeReturn("DPS_ADM", "DPS Central Administrator")),
      ),
    )

    whenever(externalRolesApiService.getRoles(any())).thenReturn(rolesFromExternalUsers)
    whenever(prisonRolesApiService.getUserRoles(anyString())).thenReturn(userRolesFromNomis)
    val userRoles = userRolesService.getUserRoles("BOB")

    assertThat(userRoles).isEqualTo(userRolesFromNomis)
  }

  @Test
  fun `get user roles - RoleName is more than 30 characters so roleName take from external users`() {
    val userRolesFromNomis = createUserRoleDetails()
    val rolesFromExternalUsers = listOf(
      Role(
        roleCode = "OMIC_ADMIN",
        roleName = "Key-worker allocator",
        roleDescription = null,
        adminType = listOf(AdminTypeReturn("DPS_ADM", "DPS Central Administrator")),
      ),
      Role(
        roleCode = "MAINTAIN_ACCESS_ROLES",
        roleName = "Maintain access roles that has more than 30 characters in the role name",
        roleDescription = null,
        adminType = listOf(AdminTypeReturn("DPS_ADM", "DPS Central Administrator")),
      ),
    )

    whenever(externalRolesApiService.getRoles(any())).thenReturn(rolesFromExternalUsers)
    whenever(prisonRolesApiService.getUserRoles(anyString())).thenReturn(userRolesFromNomis)
    val userRoles = userRolesService.getUserRoles("BOB")

    assertThat(userRoles).isNotEqualTo(userRolesFromNomis)
    assertThat(userRoles.dpsRoles[1].name).isEqualTo("Maintain access roles that has more than 30 characters in the role name")
  }

  @Test
  fun `get user roles - Roles are in alpha order by role name`() {
    val userRolesFromNomis = createUserRoleDetails()
    val rolesFromExternalUsers = listOf(
      Role(
        roleCode = "MAINTAIN_ACCESS_ROLES",
        roleName = "Maintain access roles that has more than 30 characters in the role name",
        roleDescription = null,
        adminType = listOf(AdminTypeReturn("DPS_ADM", "DPS Central Administrator")),
      ),
      Role(
        roleCode = "OMIC_ADMIN",
        roleName = "Key-worker allocator",
        roleDescription = null,
        adminType = listOf(AdminTypeReturn("DPS_ADM", "DPS Central Administrator")),
      ),
    )

    whenever(externalRolesApiService.getRoles(any())).thenReturn(rolesFromExternalUsers)
    whenever(prisonRolesApiService.getUserRoles(anyString())).thenReturn(userRolesFromNomis)
    val userRoles = userRolesService.getUserRoles("BOB")

    assertThat(userRoles).isNotEqualTo(userRolesFromNomis)
    assertThat(userRoles.dpsRoles[0].name).isEqualTo("Key-worker allocator")
    assertThat(userRoles.dpsRoles[1].name).isEqualTo("Maintain access roles that has more than 30 characters in the role name")
  }

  @Nested
  inner class CreatePrisonUserRoles {
    @Test
    fun `calls addRolesToUser api`() {
      val prisonUserRoleDetail = createPrisonUserRoleDetails()
      whenever(prisonRolesApiService.addRolesToUser(any(), any(), any())).thenReturn(prisonUserRoleDetail)

      val details = userRolesService.addRolesToUser(prisonUserRoleDetail.username, listOf("ROLE_3", "ROLE_4"), "NWEB")

      verify(prisonRolesApiService).addRolesToUser(prisonUserRoleDetail.username, listOf("ROLE_3", "ROLE_4"), "NWEB")
      assertThat(details).isEqualTo(prisonUserRoleDetail)
    }

    @Test
    fun `calls addRolesToUserAsSystem api`() {
      val prisonUserRoleDetail = createPrisonUserRoleDetails()
      whenever(prisonRolesApiService.addRolesToUserAsSystem(any(), any(), any())).thenReturn(prisonUserRoleDetail)

      val details = userRolesService.addRolesToUserAsSystem(prisonUserRoleDetail.username, listOf("ROLE_3", "ROLE_4"), "NWEB")

      verify(prisonRolesApiService).addRolesToUserAsSystem(prisonUserRoleDetail.username, listOf("ROLE_3", "ROLE_4"), "NWEB")
      assertThat(details).isEqualTo(prisonUserRoleDetail)
    }
  }

  @Nested
  inner class RemovePrisonUserRole {
    @Test
    fun `calls removeRoleFromUser api`() {
      val prisonUserRoleDetail = createPrisonUserRoleDetails()
      whenever(prisonRolesApiService.removeRoleFromUser(any(), any(), any())).thenReturn(prisonUserRoleDetail)

      val details = userRolesService.removeRoleFromUser(prisonUserRoleDetail.username, "role 1", "NWEB")

      verify(prisonRolesApiService).removeRoleFromUser(prisonUserRoleDetail.username, "role 1", "NWEB")
      assertThat(details).isEqualTo(prisonUserRoleDetail)
    }
  }

  private fun createUserRoleDetails() = PrisonUserRole(
    username = "bob",
    active = true,
    activeCaseload = PrisonCaseload(id = "CADM_I", name = "Central Administration Caseload For Hmps", function = "GENERAL"),
    dpsRoles = listOf(
      PrisonRole(
        code = "OMIC_ADMIN",
        name = "Key-worker allocator",
        sequence = 1,
        type = PrisonRoleType.APP,
        adminRoleOnly = false,
      ),
      PrisonRole(
        code = "MAINTAIN_ACCESS_ROLES",
        name = "Maintain DPS user roles",
        sequence = 1,
        type = PrisonRoleType.APP,
        adminRoleOnly = false,
      ),
    ),
    nomisRoles = listOf(),
  )

  private fun createPrisonUserRoleDetails(
    username: String = "NUSER_GEN",
    active: Boolean = true,
    accountType: UsageType = UsageType.GENERAL,
  ) = UserRoleDetail(
    username = username,
    active = active,
    accountType = accountType,
    activeCaseload = uk.gov.justice.digital.hmpps.manageusersapi.resource.prison.PrisonCaseload("MDI", "Moorland (HMP)", "GENERAL"),
    dpsRoles = listOf(),
    nomisRoles = listOf(
      CaseloadRoleDetail(
        uk.gov.justice.digital.hmpps.manageusersapi.resource.prison.PrisonCaseload("MDI", "Moorland (HMP)", "GENERAL"),
        listOf(
          RoleDetail("ROLE1", "Role 1"),
          RoleDetail("ROLE2", "Role 2"),
        ),
      ),
    ),
  )
}

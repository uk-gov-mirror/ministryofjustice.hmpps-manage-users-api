package uk.gov.justice.digital.hmpps.manageusersapi.service.bulkjob

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.manageusersapi.event.BulkUserJobItemMessage
import uk.gov.justice.digital.hmpps.manageusersapi.model.DPS_CASELOAD
import uk.gov.justice.digital.hmpps.manageusersapi.repository.BulkUserJobItemRepository
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItem
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItemStatus
import uk.gov.justice.digital.hmpps.manageusersapi.service.prison.UserRolesService
import java.util.UUID

@Service
class BulkUserJobItemRoleAssignmentService(
  private val bulkUserJobItemRepository: BulkUserJobItemRepository,
  private val userRolesService: UserRolesService,
  private val bulkUserJobReconciliationService: BulkUserJobReconciliationService,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    private const val USER_NOT_FOUND = "User not found"
    private const val SYSTEM_ISSUE = "System issue"
  }

  fun processRoleAssignmentMessage(message: BulkUserJobItemMessage) {
    val item = bulkUserJobItemRepository.findById(message.jobItemId).orElse(null)
    if (item == null) {
      log.warn("Skipping bulk user job item {} because it no longer exists", message.jobItemId)
      return
    }

    if (!claimForAssignment(item)) {
      log.info(
        "Skipping bulk user job item {} because it is not awaiting assignment (already in a terminal state or not yet published)",
        message.jobItemId,
      )
      return
    }

    if (!messageMatchesPersistedItem(message, item)) {
      markError(item.id, SYSTEM_ISSUE)
      bulkUserJobReconciliationService.reconcileBulkJob(item.bulkUserJob.id)
      log.warn("Received mismatched payload for bulk user job item {}", item.id)
      return
    }

    val username = item.username.uppercase()
    val roleCode = item.rolename.uppercase()

    try {
      userRolesService.addRolesToUserAsSystem(username, listOf(roleCode), DPS_CASELOAD)
      markSuccess(item.id)
      bulkUserJobReconciliationService.reconcileBulkJob(item.bulkUserJob.id)
    } catch (e: WebClientResponseException.NotFound) {
      markError(item.id, USER_NOT_FOUND)
      bulkUserJobReconciliationService.reconcileBulkJob(item.bulkUserJob.id)
    } catch (e: WebClientResponseException.Conflict) {
      // The user already has the role (either pre-existing, or assigned by a previous processing of this message that
      // failed before recording success), so treat it as a successful assignment
      markSuccess(item.id)
      bulkUserJobReconciliationService.reconcileBulkJob(item.bulkUserJob.id)
    } catch (e: Exception) {
      markError(item.id, SYSTEM_ISSUE)
      bulkUserJobReconciliationService.reconcileBulkJob(item.bulkUserJob.id)
      log.error("Role assignment failed for bulk user job item {}", item.id, e)
    }
  }

  private fun claimForAssignment(item: BulkUserJobItem): Boolean {
    // This might be a recovery so if the item was already started then no need to set the status as started,
    // otherwise claim by changing from published to started.
    val alreadyStarted = item.status == BulkUserJobItemStatus.STARTED
    if (!alreadyStarted) {
      val claimedFromPublished = bulkUserJobItemRepository.updateStatusIfCurrent(
        jobItemId = item.id,
        currentStatus = BulkUserJobItemStatus.PUBLISHED,
        newStatus = BulkUserJobItemStatus.STARTED,
      ) == 1
      if (claimedFromPublished) {
        return true
      }
    }
    return alreadyStarted
  }

  private fun markSuccess(jobItemId: UUID) {
    val updatedRows = bulkUserJobItemRepository.updateStatusAndResultIfCurrent(
      jobItemId = jobItemId,
      currentStatus = BulkUserJobItemStatus.STARTED,
      newStatus = BulkUserJobItemStatus.SUCCESS,
    )
    check(updatedRows == 1) { "Bulk user job item $jobItemId could not be marked as SUCCESS from STARTED" }
  }

  private fun markError(jobItemId: UUID, reason: String) {
    val updatedRows = bulkUserJobItemRepository.updateStatusAndResultIfCurrent(
      jobItemId = jobItemId,
      currentStatus = BulkUserJobItemStatus.STARTED,
      newStatus = BulkUserJobItemStatus.ERROR,
      result = reason,
    )
    check(updatedRows == 1) { "Bulk user job item $jobItemId could not be marked as ERROR from STARTED" }
  }

  private fun messageMatchesPersistedItem(message: BulkUserJobItemMessage, item: BulkUserJobItem): Boolean = message.jobId == item.bulkUserJob.id &&
    message.username.equals(item.username, ignoreCase = true) &&
    message.rolename.equals(item.rolename, ignoreCase = true)
}

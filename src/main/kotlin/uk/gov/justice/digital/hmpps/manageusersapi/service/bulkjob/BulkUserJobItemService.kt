package uk.gov.justice.digital.hmpps.manageusersapi.service.bulkjob

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.manageusersapi.event.BulkUserJobItemPublisher
import uk.gov.justice.digital.hmpps.manageusersapi.repository.BulkUserJobItemRepository
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJob
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItem
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItemStatus
import java.util.UUID

@Service
class BulkUserJobItemService(
  private val bulkUserJobItemRepository: BulkUserJobItemRepository,
  private val bulkUserJobItemPublisher: BulkUserJobItemPublisher,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  fun processJobItem(job: BulkUserJob, item: BulkUserJobItem) {
    if (!claimJobItem(item.id)) {
      log.info("Skipping bulk user job item {} because it has already been processed", item.id)
      return
    }

    try {
      bulkUserJobItemPublisher.publishBulkUserJobItemEvent(job, item)
    } catch (e: Exception) {
      releaseClaim(item.id)
      throw e
    }

    markPublished(item.id)
  }

  private fun claimJobItem(jobItemId: UUID): Boolean = bulkUserJobItemRepository.updateStatusIfCurrent(
    jobItemId = jobItemId,
    currentStatus = BulkUserJobItemStatus.CREATED,
    newStatus = BulkUserJobItemStatus.CLAIMED,
  ) == 1

  private fun releaseClaim(jobItemId: UUID) {
    bulkUserJobItemRepository.updateStatusIfCurrent(
      jobItemId = jobItemId,
      currentStatus = BulkUserJobItemStatus.CLAIMED,
      newStatus = BulkUserJobItemStatus.CREATED,
    )
  }

  private fun markPublished(jobItemId: UUID) {
    val updatedRows = bulkUserJobItemRepository.updateStatusIfCurrent(
      jobItemId = jobItemId,
      currentStatus = BulkUserJobItemStatus.CLAIMED,
      newStatus = BulkUserJobItemStatus.PUBLISHED,
    )
    check(updatedRows == 1) { "Bulk user job item $jobItemId could not be marked as PUBLISHED from CLAIMED" }
  }
}

package uk.gov.justice.digital.hmpps.manageusersapi.service.bulkjob

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.manageusersapi.event.BulkUserJobItemPublisher
import uk.gov.justice.digital.hmpps.manageusersapi.repository.BulkUserJobItemRepository
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJob
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItem
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItemStatus
import uk.gov.justice.digital.hmpps.manageusersapi.service.EntityNotFoundException
import java.util.UUID

@Service
class BulkUserJobItemService(
  private val bulkUserJobItemRepository: BulkUserJobItemRepository,
  private val bulkUserJobItemPublisher: BulkUserJobItemPublisher,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun processJobItem(job: BulkUserJob, item: BulkUserJobItem) {
    if (canPublish(item.id)) {
      bulkUserJobItemPublisher.publishBulkUserJobItemEvent(job, item)
      markPublished(item.id)
    } else {
      log.info("Skipping bulk user job item {} because it has already been processed", item.id)
    }
  }

  private fun canPublish(jobItemId: UUID): Boolean {
    val jobItem = bulkUserJobItemRepository.findById(jobItemId)
      .orElseThrow { EntityNotFoundException("Bulk user job item not found: $jobItemId") }
    return jobItem.status == BulkUserJobItemStatus.CREATED
  }

  private fun markPublished(jobItemId: UUID) {
    val jobItem = bulkUserJobItemRepository.findById(jobItemId)
      .orElseThrow { EntityNotFoundException("Bulk user job item not found: $jobItemId") }
    jobItem.status = BulkUserJobItemStatus.PUBLISHED
    bulkUserJobItemRepository.save(jobItem)
  }
}

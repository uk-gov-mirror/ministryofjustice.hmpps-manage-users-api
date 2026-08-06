package uk.gov.justice.digital.hmpps.manageusersapi.event

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.manageusersapi.repository.BulkUserJobRepository
import uk.gov.justice.digital.hmpps.manageusersapi.service.EntityNotFoundException
import uk.gov.justice.digital.hmpps.manageusersapi.service.bulkjob.BulkUserJobItemService

@Service
class BulkUserJobListener(
  private val bulkUserJobRepository: BulkUserJobRepository,
  private val bulkUserJobItemService: BulkUserJobItemService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(value = ["bulkuserjobqueue"], factory = "hmppsQueueContainerFactoryProxy")
  fun onBulkUserJobMessage(message: BulkUserJobEvent) {
    val job = bulkUserJobRepository.findWithJobItemsById(message.jobId)
      .orElseThrow { EntityNotFoundException("Bulk user job not found: ${message.jobId}") }

    val pendingItems = job.jobItems
    log.info("Processing bulk user job {} with {} pending item(s)", job.id, pendingItems.size)

    pendingItems.forEach { item -> bulkUserJobItemService.processJobItem(job, item) }
  }
}

package uk.gov.justice.digital.hmpps.manageusersapi.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJob
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItem
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Service
class BulkUserJobItemPublisher(
  hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
) {
  private val bulkUserJobItemQueue by lazy {
    hmppsQueueService.findByQueueId("bulkuserjobitemqueue") ?: throw QueueNotFoundException("bulkuserjobitemqueue")
  }
  private val bulkUserJobItemQueueUrl by lazy { bulkUserJobItemQueue.queueUrl }
  private val bulkUserJobItemSqsClient by lazy { bulkUserJobItemQueue.sqsClient }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun publishBulkUserJobItemEvent(job: BulkUserJob, jobItem: BulkUserJobItem) {
    val message = BulkUserJobItemMessage(
      jobId = job.id,
      jobItemId = jobItem.id,
      username = jobItem.username,
      rolename = jobItem.rolename,
      jiraReference = job.jiraReference,
      requestedBy = job.requestedBy,
    )

    val request = SendMessageRequest.builder()
      .queueUrl(bulkUserJobItemQueueUrl)
      .messageBody(objectMapper.writeValueAsString(message))
      .build()

    bulkUserJobItemSqsClient.sendMessage(request)
    log.info("Published bulk user job item event: jobId={}, jobItemId={}", job.id, jobItem.id)
  }
}

data class BulkUserJobItemMessage(
  val jobId: java.util.UUID,
  val jobItemId: java.util.UUID,
  val username: String,
  val rolename: String,
  val jiraReference: String,
  val requestedBy: String,
)

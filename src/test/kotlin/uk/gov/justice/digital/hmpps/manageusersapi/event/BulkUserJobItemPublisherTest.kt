package uk.gov.justice.digital.hmpps.manageusersapi.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJob
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItem
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobStatus
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.util.UUID
import java.util.concurrent.CompletableFuture

class BulkUserJobItemPublisherTest {
  private val hmppsQueueService: HmppsQueueService = mock()
  private val sqsClient: SqsAsyncClient = mock()
  private val publisher = BulkUserJobItemPublisher(hmppsQueueService, ObjectMapper())
  private val requestCaptor = argumentCaptor<SendMessageRequest>()

  @Test
  fun `should publish bulk user job item event to standard queue`() {
    val bulkJob = BulkUserJob(UUID.randomUUID(), "JIRA-123", BulkUserJobStatus.PENDING, "userabc")
    val bulkJobItem = BulkUserJobItem(username = "USER123", rolename = "ROLE_ONE", bulkUserJob = bulkJob)
    val queueName = "bulkuserjobitemqueue"
    val bulkUserJobQueue = HmppsQueue(UUID.randomUUID().toString(), sqsClient, queueName)
    whenever(hmppsQueueService.findByQueueId("bulkuserjobitemqueue")).thenReturn(bulkUserJobQueue)
    whenever(sqsClient.getQueueUrl(any<GetQueueUrlRequest>())).thenReturn(
      CompletableFuture.completedFuture(software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse.builder().queueUrl("sqs://bulkuserjobitemqueue").build()),
    )
    whenever(sqsClient.sendMessage(any<SendMessageRequest>())).thenReturn(
      CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("test-message-id").build()),
    )

    publisher.publishBulkUserJobItemEvent(bulkJob, bulkJobItem)

    verify(sqsClient).sendMessage(requestCaptor.capture())
    assertThat(requestCaptor.firstValue.queueUrl()).isEqualTo("sqs://bulkuserjobitemqueue")
    assertThat(requestCaptor.firstValue.messageGroupId()).isNull()
    assertThat(requestCaptor.firstValue.messageDeduplicationId()).isNull()
    assertThat(requestCaptor.firstValue.messageBody()).contains(
      "\"jobId\":\"${bulkJob.id}\"",
      "\"jobItemId\":\"${bulkJobItem.id}\"",
      "\"username\":\"USER123\"",
      "\"rolename\":\"ROLE_ONE\"",
      "\"jiraReference\":\"JIRA-123\"",
      "\"requestedBy\":\"userabc\"",
    )
  }

  @Test
  fun `should throw queue not found exception when not exists`() {
    val bulkJob = BulkUserJob(UUID.randomUUID(), "JIRA-123", BulkUserJobStatus.PENDING, "userabc")
    val bulkJobItem = BulkUserJobItem(username = "USER123", rolename = "ROLE_ONE", bulkUserJob = bulkJob)

    assertThatThrownBy { publisher.publishBulkUserJobItemEvent(bulkJob, bulkJobItem) }
      .isInstanceOf(QueueNotFoundException::class.java)
      .hasMessage("Queue with id bulkuserjobitemqueue does not exist")
  }

  @Test
  fun `should throw exception when send message fails`() {
    val bulkJob = BulkUserJob(UUID.randomUUID(), "JIRA-123", BulkUserJobStatus.PENDING, "userabc")
    val bulkJobItem = BulkUserJobItem(username = "USER123", rolename = "ROLE_ONE", bulkUserJob = bulkJob)
    val bulkUserJobQueue = HmppsQueue(UUID.randomUUID().toString(), sqsClient, "bulkuserjobitemqueue")
    whenever(hmppsQueueService.findByQueueId("bulkuserjobitemqueue")).thenReturn(bulkUserJobQueue)
    whenever(sqsClient.getQueueUrl(any<GetQueueUrlRequest>())).thenReturn(
      CompletableFuture.completedFuture(software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse.builder().queueUrl("sqs://bulkuserjobitemqueue").build()),
    )
    whenever(sqsClient.sendMessage(any<SendMessageRequest>())).thenReturn(
      CompletableFuture.failedFuture(RuntimeException("send failed")),
    )

    assertThatThrownBy { publisher.publishBulkUserJobItemEvent(bulkJob, bulkJobItem) }
      .isInstanceOf(java.util.concurrent.CompletionException::class.java)
      .hasRootCauseMessage("send failed")
  }
}

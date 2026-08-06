package uk.gov.justice.digital.hmpps.manageusersapi.service.bulkjob

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.manageusersapi.event.BulkUserJobItemPublisher
import uk.gov.justice.digital.hmpps.manageusersapi.repository.BulkUserJobItemRepository
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJob
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItem
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItemStatus
import java.util.UUID

class BulkUserJobItemServiceTest {
  private val bulkUserJobItemRepository: BulkUserJobItemRepository = mock()
  private val bulkUserJobItemPublisher: BulkUserJobItemPublisher = mock()
  private val service = BulkUserJobItemService(bulkUserJobItemRepository, bulkUserJobItemPublisher)

  @Test
  fun `processes job item when status is created`() {
    val job = BulkUserJob(jiraReference = "JIRA-123", requestedBy = "userabc")
    val item = BulkUserJobItem(username = "USER123", rolename = "ROLE_ONE", status = BulkUserJobItemStatus.CREATED, bulkUserJob = job)
    whenever(
      bulkUserJobItemRepository.updateStatusIfCurrent(
        item.id,
        BulkUserJobItemStatus.CREATED,
        BulkUserJobItemStatus.CLAIMED,
      ),
    ).thenReturn(1)
    whenever(
      bulkUserJobItemRepository.updateStatusIfCurrent(
        item.id,
        BulkUserJobItemStatus.CLAIMED,
        BulkUserJobItemStatus.PUBLISHED,
      ),
    ).thenReturn(1)

    service.processJobItem(job, item)

    verify(bulkUserJobItemPublisher).publishBulkUserJobItemEvent(job, item)
    verify(bulkUserJobItemRepository).updateStatusIfCurrent(item.id, BulkUserJobItemStatus.CREATED, BulkUserJobItemStatus.CLAIMED)
    verify(bulkUserJobItemRepository).updateStatusIfCurrent(item.id, BulkUserJobItemStatus.CLAIMED, BulkUserJobItemStatus.PUBLISHED)
  }

  @Test
  fun `skips processing when item cannot be claimed`() {
    val job = BulkUserJob(jiraReference = "JIRA-123", requestedBy = "userabc")
    val item = BulkUserJobItem(username = "USER123", rolename = "ROLE_ONE", status = BulkUserJobItemStatus.PUBLISHED, bulkUserJob = job)
    whenever(
      bulkUserJobItemRepository.updateStatusIfCurrent(
        item.id,
        BulkUserJobItemStatus.CREATED,
        BulkUserJobItemStatus.CLAIMED,
      ),
    ).thenReturn(0)

    service.processJobItem(job, item)

    verify(bulkUserJobItemPublisher, never()).publishBulkUserJobItemEvent(any(), any())
    verify(bulkUserJobItemRepository, never()).updateStatusIfCurrent(item.id, BulkUserJobItemStatus.CLAIMED, BulkUserJobItemStatus.PUBLISHED)
  }

  @Test
  fun `releases claim when publish fails`() {
    val job = BulkUserJob(jiraReference = "JIRA-123", requestedBy = "userabc")
    val item = BulkUserJobItem(username = "USER123", rolename = "ROLE_ONE", status = BulkUserJobItemStatus.CREATED, bulkUserJob = job)
    whenever(
      bulkUserJobItemRepository.updateStatusIfCurrent(
        item.id,
        BulkUserJobItemStatus.CREATED,
        BulkUserJobItemStatus.CLAIMED,
      ),
    ).thenReturn(1)
    whenever(bulkUserJobItemPublisher.publishBulkUserJobItemEvent(job, item)).thenThrow(RuntimeException("send failed"))

    assertThatThrownBy { service.processJobItem(job, item) }
      .isInstanceOf(RuntimeException::class.java)
      .hasMessage("send failed")

    verify(bulkUserJobItemRepository).updateStatusIfCurrent(item.id, BulkUserJobItemStatus.CLAIMED, BulkUserJobItemStatus.CREATED)
  }

  @Test
  fun `throws when item cannot be marked as published`() {
    val job = BulkUserJob(jiraReference = "JIRA-123", requestedBy = "userabc")
    val item = BulkUserJobItem(id = UUID.randomUUID(), username = "USER123", rolename = "ROLE_ONE", bulkUserJob = job)
    whenever(
      bulkUserJobItemRepository.updateStatusIfCurrent(
        item.id,
        BulkUserJobItemStatus.CREATED,
        BulkUserJobItemStatus.CLAIMED,
      ),
    ).thenReturn(1)
    whenever(
      bulkUserJobItemRepository.updateStatusIfCurrent(
        item.id,
        BulkUserJobItemStatus.CLAIMED,
        BulkUserJobItemStatus.PUBLISHED,
      ),
    ).thenReturn(0)

    assertThatThrownBy { service.processJobItem(job, item) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("Bulk user job item ${item.id} could not be marked as PUBLISHED from CLAIMED")
  }
}

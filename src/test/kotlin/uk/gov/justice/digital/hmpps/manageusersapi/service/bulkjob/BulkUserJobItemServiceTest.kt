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
import uk.gov.justice.digital.hmpps.manageusersapi.service.EntityNotFoundException
import java.util.Optional
import java.util.UUID

class BulkUserJobItemServiceTest {
  private val bulkUserJobItemRepository: BulkUserJobItemRepository = mock()
  private val bulkUserJobItemPublisher: BulkUserJobItemPublisher = mock()
  private val service = BulkUserJobItemService(bulkUserJobItemRepository, bulkUserJobItemPublisher)

  @Test
  fun `processes job item when status is created`() {
    val job = BulkUserJob(jiraReference = "JIRA-123", requestedBy = "userabc")
    val item = BulkUserJobItem(username = "USER123", rolename = "ROLE_ONE", status = BulkUserJobItemStatus.CREATED, bulkUserJob = job)
    whenever(bulkUserJobItemRepository.findById(item.id)).thenReturn(Optional.of(item))

    service.processJobItem(job, item)

    verify(bulkUserJobItemPublisher).publishBulkUserJobItemEvent(job, item)
    verify(bulkUserJobItemRepository).save(item)
  }

  @Test
  fun `skips processing when status is not created`() {
    val job = BulkUserJob(jiraReference = "JIRA-123", requestedBy = "userabc")
    val item = BulkUserJobItem(username = "USER123", rolename = "ROLE_ONE", status = BulkUserJobItemStatus.PUBLISHED, bulkUserJob = job)
    whenever(bulkUserJobItemRepository.findById(item.id)).thenReturn(Optional.of(item))

    service.processJobItem(job, item)

    verify(bulkUserJobItemPublisher, never()).publishBulkUserJobItemEvent(any(), any())
    verify(bulkUserJobItemRepository, never()).save(any())
  }

  @Test
  fun `throws exception when job item cannot be found`() {
    val job = BulkUserJob(jiraReference = "JIRA-123", requestedBy = "userabc")
    val item = BulkUserJobItem(id = UUID.randomUUID(), username = "USER123", rolename = "ROLE_ONE", bulkUserJob = job)
    whenever(bulkUserJobItemRepository.findById(item.id)).thenReturn(Optional.empty())

    assertThatThrownBy { service.processJobItem(job, item) }
      .isInstanceOf(EntityNotFoundException::class.java)
      .hasMessage("Bulk user job item not found: ${item.id}")
  }
}

package uk.gov.justice.digital.hmpps.manageusersapi.event

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.manageusersapi.repository.BulkUserJobRepository
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJob
import uk.gov.justice.digital.hmpps.manageusersapi.service.EntityNotFoundException
import uk.gov.justice.digital.hmpps.manageusersapi.service.bulkjob.BulkUserJobItemService
import java.util.Optional
import java.util.UUID

class BulkUserJobListenerTest {
  private val bulkUserJobRepository: BulkUserJobRepository = mock()
  private val bulkUserJobItemService: BulkUserJobItemService = mock()
  private val listener = BulkUserJobListener(bulkUserJobRepository, bulkUserJobItemService)

  @Test
  fun `should publish one message per created job item`() {
    val job = BulkUserJob(jiraReference = "JIRA-123", requestedBy = "userabc")
    job.addItem("USER123", "ROLE_ONE")
    job.addItem("USER456", "ROLE_TWO")
    whenever(bulkUserJobRepository.findWithJobItemsById(job.id)).thenReturn(Optional.of(job))

    listener.onBulkUserJobMessage(BulkUserJobEvent(job.id))

    verify(bulkUserJobItemService).processJobItem(job, job.jobItems.first())
    verify(bulkUserJobItemService).processJobItem(job, job.jobItems.last())
  }

  @Test
  fun `should throw exception when job not found`() {
    val jobId = UUID.randomUUID()
    whenever(bulkUserJobRepository.findWithJobItemsById(jobId)).thenReturn(Optional.empty())

    assertThatThrownBy { listener.onBulkUserJobMessage(BulkUserJobEvent(jobId)) }
      .isInstanceOf(EntityNotFoundException::class.java)
      .hasMessage("Bulk user job not found: $jobId")
  }
}

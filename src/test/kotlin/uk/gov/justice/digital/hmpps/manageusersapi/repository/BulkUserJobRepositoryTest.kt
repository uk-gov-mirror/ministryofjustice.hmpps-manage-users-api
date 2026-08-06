package uk.gov.justice.digital.hmpps.manageusersapi.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJob
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItem
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItemStatus
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobStatus
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
class BulkUserJobRepositoryTest {
  @Autowired
  lateinit var bulkUserJobRepository: BulkUserJobRepository

  companion object {
    private val requestTime = LocalDateTime.parse("2025-11-24T09:53:03")

    val pendingBulkUserJob = BulkUserJob(
      id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
      status = BulkUserJobStatus.PENDING,
      jiraReference = "ABC-123",
      requestedBy = "Test",
      requestDateTime = requestTime,
    )
    val pendingBulkUserJobTwo = BulkUserJob(
      id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
      status = BulkUserJobStatus.PENDING,
      jiraReference = "DEF-456",
      requestedBy = "Second",
      requestDateTime = requestTime.plusHours(1),
    )
    val completedBulkUserJob = BulkUserJob(
      id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
      status = BulkUserJobStatus.COMPLETE,
      jiraReference = "GHI-789",
      requestedBy = "Test",
      requestDateTime = requestTime.plusHours(2),
    )
  }

  @BeforeEach
  fun setup() {
    bulkUserJobRepository.deleteAll()
  }

  fun givenBulkJobsExist() {
    bulkUserJobRepository.save(pendingBulkUserJob)
    bulkUserJobRepository.save(pendingBulkUserJobTwo)
    bulkUserJobRepository.save(completedBulkUserJob)
  }

  @Test
  fun `persists bulk user job entity`() {
    val bulkUserJob = BulkUserJob(jiraReference = "JIRA-111", requestedBy = "user1")
    bulkUserJob.addItem("user-432", "role_test_one")
    bulkUserJob.addItem("user-765", "role_test_two")
    bulkUserJob.addItem("user-987", "role_test_three")
    bulkUserJobRepository.save(bulkUserJob)

    val result = bulkUserJobRepository.findById(bulkUserJob.id)

    assertThat(result).isPresent.hasValueSatisfying {
      assertThat(it).usingRecursiveComparison().ignoringFields("jobItems").isEqualTo(bulkUserJob)
      assertThat(it.jobItems).usingRecursiveFieldByFieldElementComparatorIgnoringFields("job")
        .containsExactlyInAnyOrderElementsOf(bulkUserJob.jobItems)
    }
  }

  @Nested
  inner class FindByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase {
    @Test
    fun `findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase returns only bulk jobs where the given string is contained within the jira reference`() {
      givenBulkJobsExist()

      val result =
        bulkUserJobRepository.findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase(
          "ABC",
          "ABC",
          Pageable.unpaged(Sort.by("RequestDateTime").descending()),
        ).content

      assertThat(bulkUserJobRepository.findAll()).hasSize(3)
      assertThat(result).usingRecursiveFieldByFieldElementComparator().containsExactly(pendingBulkUserJob)
    }

    @Test
    fun `findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase returns only bulk jobs where the given string is contained within requested by`() {
      givenBulkJobsExist()

      val result =
        bulkUserJobRepository.findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase(
          "Test",
          "Test",
          Pageable.unpaged(Sort.by("RequestDateTime").descending()),
        ).content

      assertThat(bulkUserJobRepository.findAll()).hasSize(3)
      assertThat(result).usingRecursiveFieldByFieldElementComparator()
        .containsExactly(completedBulkUserJob, pendingBulkUserJob)
    }

    @Test
    fun `findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase returns only bulk jobs for page when specified`() {
      givenBulkJobsExist()

      val result =
        bulkUserJobRepository.findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase(
          "",
          "",
          PageRequest.of(1, 1, Sort.by("RequestDateTime").descending()),
        ).content

      assertThat(bulkUserJobRepository.findAll()).hasSize(3)
      assertThat(result).usingRecursiveFieldByFieldElementComparator().containsExactly(pendingBulkUserJobTwo)
    }

    @Test
    fun `findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase returns only bulk jobs for page when specified and search provided`() {
      givenBulkJobsExist()

      val result =
        bulkUserJobRepository.findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase(
          "Test",
          "Test",
          PageRequest.of(0, 1, Sort.by("RequestDateTime").descending()),
        ).content

      assertThat(bulkUserJobRepository.findAll()).hasSize(3)
      assertThat(result).usingRecursiveFieldByFieldElementComparator().containsExactly(completedBulkUserJob)
    }

    @Test
    fun `findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase returns all bulk jobs when no search or pagination`() {
      givenBulkJobsExist()

      val result =
        bulkUserJobRepository.findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase(
          "",
          "",
          Pageable.unpaged(
            Sort.by("RequestDateTime").descending(),
          ),
        ).content

      assertThat(bulkUserJobRepository.findAll()).hasSize(3)
      assertThat(result).usingRecursiveFieldByFieldElementComparator()
        .containsExactly(completedBulkUserJob, pendingBulkUserJobTwo, pendingBulkUserJob)
    }

    @Test
    fun `findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase search is case insensitive`() {
      givenBulkJobsExist()

      val result =
        bulkUserJobRepository.findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase(
          "TEST",
          "TEST",
          Pageable.unpaged(Sort.by("RequestDateTime").descending()),
        ).content

      assertThat(bulkUserJobRepository.findAll()).hasSize(3)
      assertThat(result).usingRecursiveFieldByFieldElementComparator()
        .containsExactly(completedBulkUserJob, pendingBulkUserJob)
    }
  }

  @Nested
  inner class FindDetailsById {

    @Test
    fun `should return expected details values`() {
      val job = BulkUserJob(
        id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
        status = BulkUserJobStatus.PENDING,
        jiraReference = "GHI-789",
        requestedBy = "Test",
        requestDateTime = requestTime.plusHours(2),
      )
      job.jobItems.add(
        BulkUserJobItem(
          username = "user1",
          rolename = "role1",
          status = BulkUserJobItemStatus.CREATED,
          bulkUserJob = job,
        ),
      )
      job.jobItems.add(
        BulkUserJobItem(
          username = "user2",
          rolename = "role2",
          status = BulkUserJobItemStatus.SUCCESS,
          bulkUserJob = job,
        ),
      )
      job.jobItems.add(
        BulkUserJobItem(
          username = "user3",
          rolename = "role3",
          status = BulkUserJobItemStatus.ERROR,
          bulkUserJob = job,
        ),
      )
      job.jobItems.add(
        BulkUserJobItem(
          username = "user4",
          rolename = "role4",
          status = BulkUserJobItemStatus.SUCCESS,
          bulkUserJob = job,
        ),
      )

      bulkUserJobRepository.save(job)

      val actual = bulkUserJobRepository.findDetailsById(job.id)
      assertThat(actual).isNotNull

      assertThat(actual!!.id).isEqualTo(job.id)
      assertThat(actual.status).isEqualTo(BulkUserJobStatus.PENDING)
      assertThat(actual.jiraReference).isEqualTo("GHI-789")
      assertThat(actual.requestedBy).isEqualTo("Test")
      assertThat(actual.requestDateTime).isEqualTo(requestTime.plusHours(2))

      assertThat(actual.totalCount).isEqualTo(4)
      assertThat(actual.successCount).isEqualTo(2)
      assertThat(actual.errorCount).isEqualTo(1)
    }

    @Test
    fun `should return correct values when job id does not exist`() {
      assertThat(bulkUserJobRepository.findDetailsById(UUID.randomUUID())).isNull()
    }

    @Test
    fun `should return correct values when job has no items`() {
      val job = BulkUserJob(
        id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
        status = BulkUserJobStatus.PENDING,
        jiraReference = "GHI-789",
        requestedBy = "Test",
        requestDateTime = requestTime.plusHours(2),
      )
      bulkUserJobRepository.save(job)

      val actual = bulkUserJobRepository.findDetailsById(job.id)

      assertThat(actual!!.id).isEqualTo(job.id)
      assertThat(actual.status).isEqualTo(BulkUserJobStatus.PENDING)
      assertThat(actual.jiraReference).isEqualTo("GHI-789")
      assertThat(actual.requestedBy).isEqualTo("Test")
      assertThat(actual.requestDateTime).isEqualTo(requestTime.plusHours(2))
      assertThat(actual.totalCount).isEqualTo(0)
      assertThat(actual.successCount).isEqualTo(0)
      assertThat(actual.errorCount).isEqualTo(0)
    }
  }

  @Nested
  inner class FindCompletedJobById {

    @BeforeEach
    fun setup() {
      bulkUserJobRepository.deleteAll()
    }

    @AfterEach
    fun teardown() {
      bulkUserJobRepository.deleteAll()
    }

    @Test
    fun `should return ID when job exists and all items have status SUCCESS`() {
      val job = createBulkUserJobWithStatus(BulkUserJobStatus.COMPLETE)
      job.addJobItemWithStatus(1, BulkUserJobItemStatus.SUCCESS)
      job.addJobItemWithStatus(2, BulkUserJobItemStatus.SUCCESS)
      job.addJobItemWithStatus(3, BulkUserJobItemStatus.SUCCESS)

      bulkUserJobRepository.save(job)

      val actual = bulkUserJobRepository.findCompletedJobById(job.id)
      assertThat(actual).isNotNull
      assertThat(actual).isEqualTo(job.id)
    }

    @Test
    fun `should return ID when job exists and all items have status ERROR`() {
      val job = createBulkUserJobWithStatus(BulkUserJobStatus.COMPLETE)
      job.addJobItemWithStatus(1, BulkUserJobItemStatus.ERROR)
      job.addJobItemWithStatus(2, BulkUserJobItemStatus.ERROR)
      job.addJobItemWithStatus(3, BulkUserJobItemStatus.ERROR)

      bulkUserJobRepository.save(job)

      val actual = bulkUserJobRepository.findCompletedJobById(job.id)
      assertThat(actual).isNotNull
      assertThat(actual).isEqualTo(job.id)
    }

    @Test
    fun `should return id when job has status COMPLETE has zero items`() {
      val job = createBulkUserJobWithStatus(BulkUserJobStatus.COMPLETE)
      bulkUserJobRepository.save(job)

      val actual = bulkUserJobRepository.findCompletedJobById(job.id)
      assertThat(actual).isNotNull
      assertThat(actual).isEqualTo(job.id)
    }

    @Test
    fun `should return null when job has status PENDING but items have status SUCCESS`() {
      val job = createBulkUserJobWithStatus(BulkUserJobStatus.PENDING)
      job.addJobItemWithStatus(1, BulkUserJobItemStatus.SUCCESS)
      job.addJobItemWithStatus(2, BulkUserJobItemStatus.SUCCESS)
      job.addJobItemWithStatus(3, BulkUserJobItemStatus.SUCCESS)

      bulkUserJobRepository.save(job)

      val actual = bulkUserJobRepository.findCompletedJobById(job.id)
      assertThat(actual).isNull()
    }

    @Test
    fun `should return null when job has status PENDING but items have status ERROR`() {
      val job = createBulkUserJobWithStatus(BulkUserJobStatus.PENDING)
      job.addJobItemWithStatus(1, BulkUserJobItemStatus.ERROR)
      job.addJobItemWithStatus(2, BulkUserJobItemStatus.ERROR)
      job.addJobItemWithStatus(3, BulkUserJobItemStatus.ERROR)

      bulkUserJobRepository.save(job)

      val actual = bulkUserJobRepository.findCompletedJobById(job.id)
      assertThat(actual).isNull()
    }

    @Test
    fun `should return null when job has status PENDING but items have status CREATED`() {
      val job = createBulkUserJobWithStatus(BulkUserJobStatus.PENDING)
      job.addJobItemWithStatus(1, BulkUserJobItemStatus.CREATED)
      job.addJobItemWithStatus(2, BulkUserJobItemStatus.CREATED)
      job.addJobItemWithStatus(3, BulkUserJobItemStatus.CREATED)

      bulkUserJobRepository.save(job)

      val actual = bulkUserJobRepository.findCompletedJobById(job.id)
      assertThat(actual).isNull()
    }

    @Test
    fun `should return null when job has status PENDING but items have status STARTED`() {
      val job = createBulkUserJobWithStatus(BulkUserJobStatus.PENDING)
      job.addJobItemWithStatus(1, BulkUserJobItemStatus.STARTED)
      job.addJobItemWithStatus(2, BulkUserJobItemStatus.STARTED)
      job.addJobItemWithStatus(3, BulkUserJobItemStatus.STARTED)

      bulkUserJobRepository.save(job)

      val actual = bulkUserJobRepository.findCompletedJobById(job.id)
      assertThat(actual).isNull()
    }

    @Test
    fun `should return null when job has status COMPLETE but items have status CREATED`() {
      val job = createBulkUserJobWithStatus(BulkUserJobStatus.COMPLETE)
      job.addJobItemWithStatus(1, BulkUserJobItemStatus.CREATED)
      job.addJobItemWithStatus(2, BulkUserJobItemStatus.CREATED)
      job.addJobItemWithStatus(3, BulkUserJobItemStatus.CREATED)

      bulkUserJobRepository.save(job)

      val actual = bulkUserJobRepository.findCompletedJobById(job.id)
      assertThat(actual).isNull()
    }

    @Test
    fun `should return null when job has status COMPLETE but items have status STARTED`() {
      val job = createBulkUserJobWithStatus(BulkUserJobStatus.COMPLETE)
      job.addJobItemWithStatus(1, BulkUserJobItemStatus.STARTED)
      job.addJobItemWithStatus(2, BulkUserJobItemStatus.STARTED)
      job.addJobItemWithStatus(3, BulkUserJobItemStatus.STARTED)

      bulkUserJobRepository.save(job)

      val actual = bulkUserJobRepository.findCompletedJobById(job.id)
      assertThat(actual).isNull()
    }

    @Test
    fun `should return null when job has status COMPLETE but at least 1 item is NOT SUCCESS or ERROR`() {
      val job = createBulkUserJobWithStatus(BulkUserJobStatus.COMPLETE)
      job.addJobItemWithStatus(1, BulkUserJobItemStatus.STARTED)
      job.addJobItemWithStatus(2, BulkUserJobItemStatus.SUCCESS)
      job.addJobItemWithStatus(3, BulkUserJobItemStatus.ERROR)

      bulkUserJobRepository.save(job)

      val actual = bulkUserJobRepository.findCompletedJobById(job.id)
      assertThat(actual).isNull()
    }
  }

  private fun createBulkUserJobWithStatus(status: BulkUserJobStatus) = BulkUserJob(
    id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
    status = status,
    jiraReference = "GHI-789",
    requestedBy = "Test",
    requestDateTime = requestTime.plusHours(2),
  )

  internal fun BulkUserJob.addJobItemWithStatus(index: Int, status: BulkUserJobItemStatus) {
    this.jobItems.add(
      BulkUserJobItem(
        username = "user$index",
        rolename = "role$index",
        status = status,
        bulkUserJob = this,
      ),
    )
  }
}

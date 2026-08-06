package uk.gov.justice.digital.hmpps.manageusersapi.service.bulkjob

import jakarta.validation.ValidationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.firstValue
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.mock.web.MockMultipartFile
import uk.gov.justice.digital.hmpps.manageusersapi.event.BulkJobPublisher
import uk.gov.justice.digital.hmpps.manageusersapi.repository.BulkUserJobItemRepository
import uk.gov.justice.digital.hmpps.manageusersapi.repository.BulkUserJobRepository
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJob
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobDetails
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItem
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItemStatus
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItemStatus.CREATED
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobStatus
import uk.gov.justice.digital.hmpps.manageusersapi.resource.bulkjob.BulkUserRoleAdditionsRequest
import java.io.Writer
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit.SECONDS
import java.util.Optional
import java.util.UUID
import java.util.stream.Stream

@ExtendWith(MockitoExtension::class)
class BulkUserJobServiceTest {
  private val bulkUserJobRepository: BulkUserJobRepository = mock()
  private val bulkUserJobItemRepository: BulkUserJobItemRepository = mock()
  private val bulkJobPublisher: BulkJobPublisher = mock()
  private val bulkUserJobCaptor = argumentCaptor<BulkUserJob>()
  private val bulkUserJobService = BulkUserJobService(bulkUserJobRepository, bulkUserJobItemRepository, bulkJobPublisher)
  private var jiraReference: String = "JIRA-123"
  private var roles: List<String> = listOf("ROLE_ONE", "ROLE_TWO")

  @Nested
  inner class CreateBulkUserRoleAdditionsJob {
    @Test
    fun `Bulk user role additions job can be created`() {
      whenCreateBulkUserRoleAdditionsJobWithCsvContent("USER123\n  USER456  \nUSER789 ".toByteArray())

      verify(bulkUserJobRepository).save(bulkUserJobCaptor.capture())
      val bulkUserJob = bulkUserJobCaptor.firstValue
      assertThat(bulkUserJob).usingRecursiveComparison().ignoringFields("id", "requestDateTime", "jobItems").isEqualTo(
        BulkUserJob(
          jiraReference = "JIRA-123",
          status = BulkUserJobStatus.PENDING,
          requestedBy = "userone",
        ),
      )
      assertThat(bulkUserJob.id).isNotNull()
      assertThat(bulkUserJob.requestDateTime).isCloseTo(LocalDateTime.now(ZoneId.systemDefault()), within(5, SECONDS))
      assertThat(bulkUserJob.jobItems).usingRecursiveFieldByFieldElementComparatorIgnoringFields("id")
        .containsExactlyInAnyOrder(
          BulkUserJobItem(username = "USER123", rolename = "ROLE_ONE", status = CREATED, bulkUserJob = bulkUserJob),
          BulkUserJobItem(username = "USER123", rolename = "ROLE_TWO", status = CREATED, bulkUserJob = bulkUserJob),
          BulkUserJobItem(username = "USER456", rolename = "ROLE_ONE", status = CREATED, bulkUserJob = bulkUserJob),
          BulkUserJobItem(username = "USER456", rolename = "ROLE_TWO", status = CREATED, bulkUserJob = bulkUserJob),
          BulkUserJobItem(username = "USER789", rolename = "ROLE_ONE", status = CREATED, bulkUserJob = bulkUserJob),
          BulkUserJobItem(username = "USER789", rolename = "ROLE_TWO", status = CREATED, bulkUserJob = bulkUserJob),
        ).allSatisfy { assertThat(it.status).isNotNull() }
      verify(bulkJobPublisher).publishBulkUserJobEvent(bulkUserJob)
    }

    @Test
    fun `Bulk user role additions validation error when no data`() {
      assertThatThrownBy { whenCreateBulkUserRoleAdditionsJobWithCsvContent("".toByteArray()) }
        .isInstanceOf(ValidationException::class.java)
        .hasMessage("Users csv does not contain any rows")
    }

    @ParameterizedTest
    @ValueSource(strings = ["USER123,USER456", "USER123\nUSER456,USER789"])
    fun `Bulk user role additions validation error when not exactly one column`(csvContent: String) {
      assertThatThrownBy { whenCreateBulkUserRoleAdditionsJobWithCsvContent(csvContent.toByteArray()) }
        .isInstanceOf(ValidationException::class.java)
        .hasMessage("Users csv row does not have exactly 1 column")
    }

    private fun whenCreateBulkUserRoleAdditionsJobWithCsvContent(contentBytes: ByteArray): UUID = bulkUserJobService
      .createBulkUserRoleAdditionsJob(
        MockMultipartFile("users.csv", contentBytes),
        BulkUserRoleAdditionsRequest(jiraReference, roles),
        "userone",
      )
  }

  @Nested
  inner class GetBulkUserRoleAdditionsJobs {

    private val jobs = listOf(
      BulkUserJob(jiraReference = "ABC-123", requestedBy = "user1"),
      BulkUserJob(jiraReference = "DEF-456", requestedBy = "user2"),
    )

    @Test
    fun `Can get bulk user jobs with no search or pagination when no arguments are given`() {
      whenever(
        bulkUserJobRepository.findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase(
          "",
          "",
          Pageable.unpaged(Sort.by("RequestDateTime").descending()),
        ),
      ).thenReturn(
        PageImpl(jobs),
      )

      val result = bulkUserJobService.getBulkUserRoleAdditionsJobs("", null, null)

      verify(bulkUserJobRepository).findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase(
        "",
        "",
        Pageable.unpaged(Sort.by("RequestDateTime").descending()),
      )
      assertThat(result).isEqualTo(jobs)
    }

    @Test
    fun `Can get bulk user jobs when search string specified`() {
      whenever(
        bulkUserJobRepository.findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase(
          "test",
          "test",
          Pageable.unpaged(Sort.by("RequestDateTime").descending()),
        ),
      ).thenReturn(
        PageImpl(jobs),
      )

      val result = bulkUserJobService.getBulkUserRoleAdditionsJobs("test", null, null)

      verify(bulkUserJobRepository).findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase(
        "test",
        "test",
        Pageable.unpaged(Sort.by("RequestDateTime").descending()),
      )
      assertThat(result).isEqualTo(jobs)
    }

    @Test
    fun `Can get bulk user jobs when pagination specified`() {
      whenever(
        bulkUserJobRepository.findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase(
          "",
          "",
          PageRequest.of(0, 1, Sort.by("RequestDateTime").descending()),
        ),
      ).thenReturn(PageImpl(jobs))

      val result = bulkUserJobService.getBulkUserRoleAdditionsJobs("", 0, 1)

      verify(bulkUserJobRepository).findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase(
        "",
        "",
        PageRequest.of(0, 1, Sort.by("RequestDateTime").descending()),
      )
      assertThat(result).isEqualTo(jobs)
    }

    @Test
    fun `Can get bulk user jobs when search string and pagination specified`() {
      whenever(
        bulkUserJobRepository.findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase(
          "test",
          "test",
          PageRequest.of(0, 1, Sort.by("RequestDateTime").descending()),
        ),
      ).thenReturn(PageImpl(jobs))

      val result = bulkUserJobService.getBulkUserRoleAdditionsJobs("test", 0, 1)

      verify(bulkUserJobRepository).findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase(
        "test",
        "test",
        PageRequest.of(0, 1, Sort.by("RequestDateTime").descending()),
      )
      assertThat(result).isEqualTo(jobs)
    }
  }

  @Nested
  inner class GetBulkUserRoleAdditionsJobDetails {
    @Test
    fun `should return expected bulk user roles addition details`() {
      val details = BulkUserJobDetails(
        id = UUID.randomUUID(),
        status = BulkUserJobStatus.PENDING,
        jiraReference = "GHI-789",
        requestedBy = "Test",
        requestDateTime = LocalDateTime.now(),
        totalCount = 4,
        successCount = 3,
        errorCount = 1,
      )

      whenever(bulkUserJobRepository.findDetailsById(details.id)).thenReturn(details)

      val actual = bulkUserJobService.getBulkUserRoleAdditionsJobDetails(details.id)

      assertThat(actual).isEqualTo(details)
      verify(bulkUserJobRepository, times(1)).findDetailsById(details.id)
    }

    @Test
    fun `should return null when job details not found`() {
      val id = UUID.randomUUID()

      whenever(bulkUserJobRepository.findDetailsById(id)).thenReturn(null)

      val actual = bulkUserJobService.getBulkUserRoleAdditionsJobDetails(id)

      assertThat(actual).isNull()
      verify(bulkUserJobRepository, times(1)).findDetailsById(id)
    }
  }


  @Nested
  inner class WriteJobResultsToCsv {

    @Captor
    private lateinit var writerCaptor: ArgumentCaptor<String>

    private val writer: Writer = mock()
    private val job: BulkUserJob = BulkUserJob(jiraReference = "ABC-123", requestedBy = "user1")

    private val jobId = UUID.randomUUID()

    @Test
    fun `should throw exception when job does not exist`() {
      whenever(bulkUserJobRepository.findById(jobId))
        .thenReturn(Optional.empty())

      val actual = assertThrows<BulkUserJobNotFoundException> {
        bulkUserJobService.writeJobResultsToCsv(writer, jobId)
      }

      assertThat(actual).isNotNull
      assertThat(actual.message).isEqualTo("Bulk user job $jobId not found")

      verify(bulkUserJobRepository, times(1)).findById(jobId)
      verifyNoInteractions(writer, bulkUserJobItemRepository)
    }

    @Test
    fun `should throw exception when job exists but is not complete`() {
      whenever(bulkUserJobRepository.findById(jobId))
        .thenReturn(Optional.of(job))

      whenever(bulkUserJobRepository.findCompletedJobById(jobId))
        .thenReturn(null)

      val actual = assertThrows<BulkUserJobNotCompleteException> {
        bulkUserJobService.writeJobResultsToCsv(writer, jobId)
      }

      assertThat(actual).isNotNull
      assertThat(actual.message).isEqualTo("unable to generate bulk user download csv: job $jobId is not complete")

      verify(bulkUserJobRepository, times(1)).findById(jobId)
      verify(bulkUserJobRepository, times(1)).findCompletedJobById(jobId)
      verifyNoInteractions(writer)
    }

    @Test
    fun `should write header when no items exist for job`() {
      whenever(bulkUserJobRepository.findById(jobId))
        .thenReturn(Optional.of(job))

      whenever(bulkUserJobRepository.findCompletedJobById(jobId))
        .thenReturn(jobId)

      whenever(bulkUserJobItemRepository.streamByBulkUserJobId(jobId))
        .thenReturn(Stream.empty())

      bulkUserJobService.writeJobResultsToCsv(writer, jobId)

      verify(bulkUserJobRepository, times(1)).findById(jobId)
      verify(bulkUserJobRepository, times(1)).findCompletedJobById(jobId)
      verify(writer).write(writerCaptor.capture())
      verify(writer, atLeast(1)).flush()

      assertThat(writerCaptor.allValues).hasSize(1)
      assertThat(writerCaptor.firstValue).isEqualTo("userId,roleCode,status,reason\n")
    }

    @Test
    fun `should write header when items stream is null`() {
      whenever(bulkUserJobRepository.findById(jobId))
        .thenReturn(Optional.of(job))

      whenever(bulkUserJobRepository.findCompletedJobById(jobId))
        .thenReturn(jobId)

      whenever(bulkUserJobItemRepository.streamByBulkUserJobId(jobId))
        .thenReturn(null)

      bulkUserJobService.writeJobResultsToCsv(writer, jobId)

      verify(bulkUserJobRepository, times(1)).findById(jobId)
      verify(bulkUserJobRepository, times(1)).findCompletedJobById(jobId)
      verify(writer).write(writerCaptor.capture())
      verify(writer, atLeast(1)).flush()

      assertThat(writerCaptor.allValues).hasSize(1)
      assertThat(writerCaptor.firstValue).isEqualTo("userId,roleCode,status,reason\n")
    }

    @Test
    fun `should write all items when items stream is not empty`() {
      whenever(bulkUserJobRepository.findById(jobId))
        .thenReturn(Optional.of(job))

      whenever(bulkUserJobRepository.findCompletedJobById(jobId))
        .thenReturn(jobId)

      whenever(bulkUserJobItemRepository.streamByBulkUserJobId(jobId))
        .thenReturn(
          Stream.of(
            BulkUserJobItem(
              id = UUID.randomUUID(),
              username = "user1",
              rolename = "role1",
              status = BulkUserJobItemStatus.SUCCESS,
              result = null,
              bulkUserJob = job,
            ),
            BulkUserJobItem(
              id = UUID.randomUUID(),
              username = "user2",
              rolename = "role2",
              status = BulkUserJobItemStatus.SUCCESS,
              result = null,
              bulkUserJob = job,
            ),
            BulkUserJobItem(
              id = UUID.randomUUID(),
              username = "user3",
              rolename = "role3",
              status = BulkUserJobItemStatus.ERROR,
              result = "role already assigned",
              bulkUserJob = job,
            ),
          ),
        )

      bulkUserJobService.writeJobResultsToCsv(writer, jobId)

      verify(bulkUserJobRepository, times(1)).findById(jobId)
      verify(bulkUserJobRepository, times(1)).findCompletedJobById(jobId)
      verify(writer, times(4)).write(writerCaptor.capture())
      verify(writer, atLeast(1)).flush()

      assertThat(writerCaptor.allValues).hasSize(4)
      assertThat(writerCaptor.allValues[0]).isEqualTo("userId,roleCode,status,reason\n")
      assertThat(writerCaptor.allValues[1]).isEqualTo("user1,role1,SUCCESS,\n")
      assertThat(writerCaptor.allValues[2]).isEqualTo("user2,role2,SUCCESS,\n")
      assertThat(writerCaptor.allValues[3]).isEqualTo("user3,role3,ERROR,role already assigned\n")
    }
  }
}

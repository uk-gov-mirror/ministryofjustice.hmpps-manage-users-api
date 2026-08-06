package uk.gov.justice.digital.hmpps.manageusersapi.resource.bulkjob

import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.BodyInserters
import uk.gov.justice.digital.hmpps.manageusersapi.config.SqsConfig
import uk.gov.justice.digital.hmpps.manageusersapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.manageusersapi.repository.BulkUserJobRepository
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJob
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItem
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItemStatus
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobStatus
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit.SECONDS
import java.util.UUID
import java.util.stream.Stream

@Import(SqsConfig::class)
class BulkJobsControllerIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var bulkUserJobRepository: BulkUserJobRepository

  @Autowired
  protected lateinit var hmppsQueueService: HmppsQueueService

  internal val itemQueue by lazy { hmppsQueueService.findByQueueId("bulkuserjobitemqueue")!! }

  companion object {
    @JvmStatic
    fun createBulkJobInvalidScenarios(): Stream<Arguments> = Stream.of(
      Arguments.of(MultipartBuilder(), "Required part 'userCsv' is missing"),
      Arguments.of(MultipartBuilder().usersCsv(), "Required part 'bulkJobDetails' is missing"),
      Arguments.of(MultipartBuilder().usersCsv(filename = "users.txt").bulkJobDetailsJson(), "Validation failure: Uploaded users file is not a CSV file"),
      Arguments.of(MultipartBuilder().usersCsv(content = "").bulkJobDetailsJson(), "Validation failure: Uploaded users file is empty"),
      Arguments.of(MultipartBuilder().usersCsv(content = "USER123,USER654").bulkJobDetailsJson(), "Validation failure: Users csv row does not have exactly 1 column"),
      Arguments.of(MultipartBuilder().usersCsv().bulkJobDetailsJson("{\"jiraReference\":\"JIRA\"}"), "Validation failure: roles must be supplied"),
      Arguments.of(MultipartBuilder().usersCsv().bulkJobDetailsJson("{\"roles\":[\"ROLE123\"]}"), "Validation failure: jiraReference must be supplied"),
      Arguments.of(MultipartBuilder().usersCsv().bulkJobDetailsJson("{\"jiraReference\":\"JIR\",\"roles\":[\"ROLE123\"]}"), "Validation failure: jiraReference must be between 4 and 266 characters"),
      Arguments.of(MultipartBuilder().usersCsv().bulkJobDetailsJson("{\"jiraReference\":\"${"J".repeat(267)}\",\"roles\":[\"ROLE123\"]}"), "Validation failure: jiraReference must be between 4 and 266 characters"),
      Arguments.of(MultipartBuilder().usersCsv().bulkJobDetailsJson("{\"jiraReference\":\"JIRA\",\"roles\":[]}"), "Validation failure: roles must be supplied"),
    )

    private fun MultipartBuilder.usersCsv(content: String = "USER123\nUSER654", filename: String = "users.csv"): MultipartBuilder {
      this.addPart("userCsv", ByteArrayResource(content.toByteArray()), filename)
      return this
    }

    private fun MultipartBuilder.bulkJobDetailsJson(
      content: String = """
                  {
                    "jiraReference": "JIRA-1234",
                    "roles": ["ROLE_ONE","ROLE_FOUR"]
                  }""",
    ): MultipartBuilder {
      this.addPart(
        "bulkJobDetails",
        HttpEntity(content, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      )
      return this
    }
  }

  @Nested
  open inner class CreateBulkUserAdditionsJob {
    @Test
    fun `access forbidden when no authority`() {
      webTestClient.post().uri("/bulk-jobs/user-role-additions")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(buildValidMultipart())
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.post().uri("/bulk-jobs/user-role-additions")
        .headers(setAuthorisation(roles = listOf()))
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(buildValidMultipart())
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden when wrong role`() {
      webTestClient.post().uri("/bulk-jobs/user-role-additions")
        .headers(setAuthorisation(roles = listOf("ROLE_AUDIT")))
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(buildValidMultipart())
        .exchange()
        .expectStatus().isForbidden
    }

    @ParameterizedTest
    @MethodSource("uk.gov.justice.digital.hmpps.manageusersapi.resource.bulkjob.BulkJobsControllerIntTest#createBulkJobInvalidScenarios")
    open fun `bad request when invalid input`(multipart: MultipartBuilder, expectedMessage: String) {
      webTestClient.post().uri("/bulk-jobs/user-role-additions")
        .headers(setAuthorisation(user = "TEST_USR", roles = listOf("ROLE_MANAGE_USER_BULK_JOBS")))
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(multipart.build())
        .exchange()
        .expectStatus().isBadRequest
        .expectBody().jsonPath("$.userMessage").isEqualTo(expectedMessage)
    }

    @Transactional
    @Test
    open fun `bulk user additions job accepted`() {
      val response = webTestClient.post().uri("/bulk-jobs/user-role-additions")
        .headers(setAuthorisation(user = "TEST_USR", roles = listOf("ROLE_MANAGE_USER_BULK_JOBS")))
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(buildValidMultipart())
        .exchange()
        .expectStatus().isAccepted
        .returnResult(BulkUserRoleAdditionsResponse::class.java)
        .responseBody.blockFirst()

      assertThat(response).isNotNull
      await.untilAsserted {
        assertThat(itemQueue.sqsClient.countAllMessagesOnQueue(itemQueue.queueUrl).get()).isEqualTo(4)
      }

      val bulkJob = bulkUserJobRepository.findById(response!!.id)
      assertThat(bulkJob).isPresent.hasValueSatisfying {
        assertThat(it).usingRecursiveComparison().ignoringFields("jobItems", "requestDateTime").isEqualTo(
          BulkUserJob(response.id, "JIRA-1234", BulkUserJobStatus.PENDING, "TEST_USR"),
        )
        assertThat(it.requestDateTime).isCloseTo(LocalDateTime.now(ZoneId.systemDefault()), within(5, SECONDS))
        assertThat(it.jobItems).usingRecursiveFieldByFieldElementComparatorIgnoringFields("id").containsExactlyInAnyOrder(
          BulkUserJobItem(username = "USER123", rolename = "ROLE_ONE", status = BulkUserJobItemStatus.PUBLISHED, bulkUserJob = it),
          BulkUserJobItem(username = "USER654", rolename = "ROLE_ONE", status = BulkUserJobItemStatus.PUBLISHED, bulkUserJob = it),
          BulkUserJobItem(username = "USER123", rolename = "ROLE_FOUR", status = BulkUserJobItemStatus.PUBLISHED, bulkUserJob = it),
          BulkUserJobItem(username = "USER654", rolename = "ROLE_FOUR", status = BulkUserJobItemStatus.PUBLISHED, bulkUserJob = it),
        )
      }
    }

    private fun buildValidMultipart(): BodyInserters.MultipartInserter = MultipartBuilder().usersCsv().bulkJobDetailsJson().build()
  }

  @Nested
  open inner class GetBulkUserAdditionsJobs {

    private val bulkJobOne = BulkUserJob(
      id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
      status = BulkUserJobStatus.PENDING,
      jiraReference = "ABC-123",
      requestedBy = "Test",
      requestDateTime = LocalDateTime.parse("2026-06-01T11:11:11"),
    )
    private val bulkJobTwo = BulkUserJob(
      id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
      status = BulkUserJobStatus.COMPLETE,
      jiraReference = "DEF-456",
      requestedBy = "TestTwo",
      requestDateTime = LocalDateTime.parse("2026-06-02T12:12:12"),
    )
    private val bulkJobThree = BulkUserJob(
      id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
      status = BulkUserJobStatus.PENDING,
      jiraReference = "GHI-789",
      requestedBy = "ABC",
      requestDateTime = LocalDateTime.parse("2026-06-02T13:13:13"),
    )
    private val bulkJobFour = BulkUserJob(
      id = UUID.fromString("44444444-4444-4444-4444-444444444444"),
      status = BulkUserJobStatus.COMPLETE,
      jiraReference = "ABC-789",
      requestedBy = "TestThree",
      requestDateTime = LocalDateTime.parse("2026-06-02T14:14:14"),
    )

    @BeforeEach
    fun setUp() {
      bulkUserJobRepository.deleteAll()
    }

    @Test
    fun `access forbidden when no authority`() {
      webTestClient.get().uri("/bulk-jobs/user-role-additions")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.get().uri("/bulk-jobs/user-role-additions")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden when wrong role`() {
      webTestClient.get().uri("/bulk-jobs/user-role-additions")
        .headers(setAuthorisation(roles = listOf("ROLE_AUDIT")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `bad request when invalid pageNumber query parameter`() {
      webTestClient.get()
        .uri { builder -> builder.path("/bulk-jobs/user-role-additions").queryParam("pageNumber", "ABC").build() }
        .headers(setAuthorisation(user = "TEST_USR", roles = listOf("ROLE_MANAGE_USER_BULK_JOBS")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody().jsonPath("$.userMessage").isEqualTo("Validation failure: Parameter 'pageNumber' must be a valid integer")
    }

    @Test
    fun `bad request when invalid pageSize query parameter`() {
      webTestClient.get()
        .uri { builder -> builder.path("/bulk-jobs/user-role-additions").queryParam("pageSize", "ABC").build() }
        .headers(setAuthorisation(user = "TEST_USR", roles = listOf("ROLE_MANAGE_USER_BULK_JOBS")))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody().jsonPath("$.userMessage").isEqualTo("Validation failure: Parameter 'pageSize' must be a valid integer")
    }

    @Test
    fun `returns bulk user additions jobs when exist`() {
      bulkUserJobRepository.saveAll(listOf(bulkJobOne, bulkJobTwo))

      val response = webTestClient.get().uri("/bulk-jobs/user-role-additions")
        .headers(setAuthorisation(user = "TEST_USR", roles = listOf("ROLE_MANAGE_USER_BULK_JOBS")))
        .exchange()
        .expectStatus().isOk
        .returnResult(object : ParameterizedTypeReference<List<BulkUserRoleAdditionsJobSummary>>() {})
        .responseBody.blockFirst()

      assertThat(response).containsExactly(
        BulkUserRoleAdditionsJobSummary(
          id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
          jiraReference = "DEF-456",
          status = "COMPLETE",
          requestedBy = "TestTwo",
          requestDateTime = LocalDateTime.parse("2026-06-02T12:12:12"),
        ),
        BulkUserRoleAdditionsJobSummary(
          id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
          jiraReference = "ABC-123",
          status = "PENDING",
          requestedBy = "Test",
          requestDateTime = LocalDateTime.parse("2026-06-01T11:11:11"),
        ),
      )
    }

    @Test
    fun `returns bulk user additions jobs with search parameters and pagination`() {
      bulkUserJobRepository.saveAll(listOf(bulkJobOne, bulkJobTwo, bulkJobThree, bulkJobFour))

      val response = webTestClient.get().uri { builder ->
        builder.path("/bulk-jobs/user-role-additions")
          .queryParam("search", "ABC")
          .queryParam("pageNumber", "1")
          .queryParam("pageSize", "2").build()
      }
        .headers(setAuthorisation(user = "TEST_USR", roles = listOf("ROLE_MANAGE_USER_BULK_JOBS")))
        .exchange()
        .expectStatus().isOk
        .returnResult(object : ParameterizedTypeReference<List<BulkUserRoleAdditionsJobSummary>>() {})
        .responseBody.blockFirst()

      assertThat(response).containsExactly(
        BulkUserRoleAdditionsJobSummary(
          id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
          jiraReference = "ABC-123",
          status = "PENDING",
          requestedBy = "Test",
          requestDateTime = LocalDateTime.parse("2026-06-01T11:11:11"),
        ),
      )
    }

    @Test
    fun `returns empty list when no results found`() {
      bulkUserJobRepository.saveAll(listOf(bulkJobOne, bulkJobTwo, bulkJobThree, bulkJobFour))

      val response = webTestClient.get()
        .uri { builder -> builder.path("/bulk-jobs/user-role-additions").queryParam("search", "98765").build() }
        .headers(setAuthorisation(user = "TEST_USR", roles = listOf("ROLE_MANAGE_USER_BULK_JOBS")))
        .exchange()
        .expectStatus().isOk
        .returnResult(object : ParameterizedTypeReference<List<BulkUserRoleAdditionsJobSummary>>() {})
        .responseBody.blockFirst()

      assertThat(response).isEmpty()
    }
  }

  @Nested
  inner class GetBulkUserAdditionsDetails {

    @BeforeEach
    internal fun setUp() {
      bulkUserJobRepository.deleteAll()
    }

    @AfterEach
    internal fun tearDown() {
      bulkUserJobRepository.deleteAll()
    }

    @Test
    fun `access forbidden when no authority`() {
      webTestClient.get()
        .uri("/bulk-jobs/user-role-additions/${UUID.randomUUID()}")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.get()
        .uri("/bulk-jobs/user-role-additions/${UUID.randomUUID()}")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden when wrong role`() {
      webTestClient.get().uri("/bulk-jobs/user-role-additions/${UUID.randomUUID()}")
        .headers(setAuthorisation(roles = listOf("ROLE_AUDIT")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should return the expected job details`() {
      val requestTime = LocalDateTime.parse("2026-06-01T11:11:11")

      val job = BulkUserJob(
        id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
        status = BulkUserJobStatus.PENDING,
        jiraReference = "GHI-789",
        requestedBy = "Test",
        requestDateTime = requestTime,
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

      bulkUserJobRepository.saveAndFlush(job)

      webTestClient.get().uri("/bulk-jobs/user-role-additions/${job.id}")
        .headers(setAuthorisation(user = "TEST_USR", roles = listOf("ROLE_MANAGE_USER_BULK_JOBS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.id").isEqualTo(job.id)
        .jsonPath("$.jiraReference").isEqualTo("GHI-789")
        .jsonPath("$.status").isEqualTo("PENDING")
        .jsonPath("$.requestedBy").isEqualTo("Test")
        .jsonPath("$.requestDateTime").isEqualTo(requestTime.toString())
        .jsonPath("$.totalCount").isEqualTo(4)
        .jsonPath("$.successCount").isEqualTo(2)
        .jsonPath("$.errorCount").isEqualTo(1)
    }

    @Test
    fun `should return status not found if job id does not exist`() {
      webTestClient.get().uri("/bulk-jobs/user-role-additions/${UUID.randomUUID()}")
        .headers(setAuthorisation(user = "TEST_USR", roles = listOf("ROLE_MANAGE_USER_BULK_JOBS")))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `should return expected value when job has no items`() {
      val requestTime = LocalDateTime.parse("2026-06-01T11:11:11")

      val job = bulkUserJobRepository.saveAndFlush(
        BulkUserJob(
          id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
          status = BulkUserJobStatus.PENDING,
          jiraReference = "GHI-789",
          requestedBy = "Test",
          requestDateTime = requestTime,
        ),
      )

      webTestClient.get().uri("/bulk-jobs/user-role-additions/${job.id}")
        .headers(setAuthorisation(user = "TEST_USR", roles = listOf("ROLE_MANAGE_USER_BULK_JOBS")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.id").isEqualTo(job.id)
        .jsonPath("$.jiraReference").isEqualTo("GHI-789")
        .jsonPath("$.status").isEqualTo("PENDING")
        .jsonPath("$.requestedBy").isEqualTo("Test")
        .jsonPath("$.requestDateTime").isEqualTo(requestTime)
        .jsonPath("$.totalCount").isEqualTo(0)
        .jsonPath("$.successCount").isEqualTo(0)
        .jsonPath("$.errorCount").isEqualTo(0)
    }
  }
}

class MultipartBuilder {

  private val multipartBodyBuilder = MultipartBodyBuilder()

  fun addPart(name: String, content: Any, filename: String? = null): MultipartBuilder {
    val part = multipartBodyBuilder.part(name, content)
    if (filename != null) {
      part.filename(filename)
    }
    return this
  }

  fun build(): BodyInserters.MultipartInserter = BodyInserters.fromMultipartData(multipartBodyBuilder.build())
}

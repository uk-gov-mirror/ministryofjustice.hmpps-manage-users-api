package uk.gov.justice.digital.hmpps.manageusersapi.resource.bulkjob

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.GroupSequence
import jakarta.validation.ValidationException
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobDetails
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobStatus
import uk.gov.justice.digital.hmpps.manageusersapi.resource.swagger.AcceptedApiResponses
import uk.gov.justice.digital.hmpps.manageusersapi.resource.swagger.StandardApiResponses
import uk.gov.justice.digital.hmpps.manageusersapi.service.bulkjob.BulkUserJobService
import java.time.LocalDateTime
import java.util.UUID

@RestController
@RequestMapping("/bulk-jobs")
class BulkJobsController(private val bulkUserJobService: BulkUserJobService) {
  @PostMapping(path = ["/user-role-additions"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
  @PreAuthorize("hasRole('ROLE_MANAGE_USER_BULK_JOBS')")
  @Operation(
    summary = "Create a bulk user role additions job.",
    description = "Create a bulk user role additions job.",
  )
  @AcceptedApiResponses
  @ResponseStatus(HttpStatus.ACCEPTED)
  fun createUserRoleAdditionsJob(
    @RequestPart("userCsv", required = true) userCsv: MultipartFile,
    @Validated(ValidationOrder::class) @RequestPart("bulkJobDetails", required = true) bulkJobDetails: BulkUserRoleAdditionsRequest,
    authentication: Authentication,
  ): ResponseEntity<BulkUserRoleAdditionsResponse> {
    validateCsvFile(userCsv)
    val id = bulkUserJobService.createBulkUserRoleAdditionsJob(userCsv, bulkJobDetails, authentication.name)
    return ResponseEntity.accepted().body(BulkUserRoleAdditionsResponse(id = id))
  }

  @GetMapping(path = ["/user-role-additions"])
  @PreAuthorize("hasRole('ROLE_MANAGE_USER_BULK_JOBS')")
  @Operation(
    summary = "Get bulk user role additions jobs.",
    description = "Returns a list of bulk user role additions jobs.",
  )
  @StandardApiResponses
  @Parameter(name = "search", description = "If provided, only results containing this string in the JIRA reference number or requested by will be returned.", required = false, example = "ABC-123")
  @Parameter(name = "pageNumber", description = "The number of the page requested.", required = false, example = "1")
  @Parameter(name = "pageSize", description = "The number of results that make up a single page.", required = false, example = "20")
  @ResponseStatus(HttpStatus.OK)
  fun getUserRoleAdditionsJobs(
    @RequestParam(required = false, name = "search") search: String = "",
    @RequestParam(required = false, name = "pageNumber") pageNumber: Int? = null,
    @RequestParam(required = false, name = "pageSize") pageSize: Int? = null,
  ): List<BulkUserRoleAdditionsJobSummary> {
    val jobs = bulkUserJobService.getBulkUserRoleAdditionsJobs(search, pageNumber, pageSize)
    return jobs.map {
      BulkUserRoleAdditionsJobSummary(
        id = it.id,
        jiraReference = it.jiraReference,
        status = it.status.name,
        requestedBy = it.requestedBy,
        requestDateTime = it.requestDateTime,
      )
    }
  }

  @GetMapping(path = ["/user-role-additions/{id}"])
  @PreAuthorize("hasRole('ROLE_MANAGE_USER_BULK_JOBS')")
  @Operation(
    summary = "Get bulk user role additions job details.",
    description = "Returns a bulk user role additions job details.",
  )
  @StandardApiResponses
  fun getUserRoleAdditionsJob(
    @PathVariable("id") id: UUID,
  ): ResponseEntity<BulkUserRolesAdditionsDetails> = bulkUserJobService.getBulkUserRoleAdditionsJobDetails(id)?.let {
    ResponseEntity.ok(it.toBulkUserRolesAdditionsDetails())
  } ?: ResponseEntity.notFound().build()


  @GetMapping(path = ["/user-role-additions/{id}/download"])
  @PreAuthorize("hasRole('ROLE_MANAGE_USER_BULK_JOBS')")
  @Operation(
    summary = "Get bulk user role additions job details.",
    description = "Returns a bulk user role additions job details.",
  )
  @StandardApiResponses
  fun getUserRoleAdditionsCsvDownload(
    @PathVariable("id") id: UUID,
    response: HttpServletResponse,
  ): ResponseEntity<BulkUserRolesAdditionsDetails>  {
    response.contentType = "text/csv"
    response.setHeader("Content-Disposition", "attachment; filename=bulk-roles-assignments-${id}.csv")
    response.writer.use { writer ->

    }
    return ResponseEntity.noContent().build()
  }

  private fun validateCsvFile(file: MultipartFile) {
    if (file.isEmpty) {
      throw ValidationException("Uploaded users file is empty")
    }
    if (!(file.originalFilename ?: "").endsWith(".csv", ignoreCase = true)) {
      throw ValidationException("Uploaded users file is not a CSV file")
    }
  }

  internal fun BulkUserJobDetails.toBulkUserRolesAdditionsDetails() = BulkUserRolesAdditionsDetails(
    id = this.id,
    status = this.status,
    jiraReference = this.jiraReference,
    requestedBy = this.requestedBy,
    requestDateTime = this.requestDateTime,
    totalCount = this.totalCount,
    successCount = this.successCount,
    errorCount = this.errorCount,
  )
}

@Schema(description = "Bulk user role additions request")
data class BulkUserRoleAdditionsRequest(
  @Schema(required = true, description = "JIRA reference", example = "ABC-1234")
  @field:NotBlank(message = "must be supplied", groups = [Required::class])
  @field:Size(min = 4, max = 266, message = "must be between 4 and 266 characters", groups = [Format::class])
  @JsonProperty("jiraReference")
  val jiraReference: String = "",

  @Schema(required = true, description = "JIRA reference", example = "ABC-1234")
  @field:NotEmpty(message = "must be supplied", groups = [Required::class])
  @JsonProperty("roles")
  val roles: List<String> = emptyList(),
)

@Schema(description = "Bulk user role additions response")
data class BulkUserRoleAdditionsResponse(
  @Schema(
    required = true,
    description = "Id of the bulk user role additions job",
    example = "123e4567-e89b-12d3-a456-426614174000",
  )
  val id: UUID,
)

@Schema(description = "Bulk user role additions job summary")
data class BulkUserRoleAdditionsJobSummary(
  @Schema(
    required = true,
    description = "Id of the bulk user role additions job",
    example = "123e4567-e89b-12d3-a456-426614174000",
  )
  val id: UUID,

  @Schema(
    required = true,
    description = "The JIRA reference of the user role additions job",
    example = "ABC-1234",
  )
  val jiraReference: String,

  @Schema(
    required = true,
    description = "The status of the user role additions job",
    example = "PENDING",
  )
  val status: String,

  @Schema(
    required = true,
    description = "The user who requested the user role additions job",
    example = "USER ONE",
  )
  val requestedBy: String,

  @Schema(
    required = true,
    description = "The date and time when the user role additions job was requested",
    example = "2026-05-11T16:32:05",
  )
  val requestDateTime: LocalDateTime,
)

@Schema(description = "Bulk user role additions job details")
data class BulkUserRolesAdditionsDetails(
  @Schema(
    required = true,
    description = "The unique identifier of the bulk user role additions job",
    example = "123e4567-e89b-12d3-a456-426614174000",
  )
  val id: UUID,

  @Schema(
    required = true,
    description = "The Jira Reference for the bulk user role additions job",
    example = "Jira1234",
  )
  val jiraReference: String,

  @Schema(
    required = true,
    description = "The current status of the bulk user role additions job",
    example = "PENDING | COMPLETE",
  )
  val status: BulkUserJobStatus,

  @Schema(
    required = true,
    description = "The user that requested the bulk user role additions job",
    example = "someUsername",
  )
  val requestedBy: String,

  @Schema(
    required = true,
    description = "The date time the bulk user role additions job was submitted",
    example = "2026-06-01T11:11:11",
  )
  val requestDateTime: LocalDateTime,

  @Schema(
    required = true,
    description = "The total number of assignments for the user role additions job",
    example = "10",
  )
  val totalCount: Long = 0,

  @Schema(
    required = true,
    description = "The current number of successful assignments for the user role additions job",
    example = "1",
  )
  val successCount: Long = 0,

  @Schema(
    required = true,
    description = "The current number of errored assignments for the user role additions job",
    example = "1",
  )
  val errorCount: Long = 0,
)

interface Required
interface Format

@GroupSequence(Required::class, Format::class)
interface ValidationOrder

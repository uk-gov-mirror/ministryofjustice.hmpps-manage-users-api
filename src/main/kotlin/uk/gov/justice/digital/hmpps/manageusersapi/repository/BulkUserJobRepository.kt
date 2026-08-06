package uk.gov.justice.digital.hmpps.manageusersapi.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJob
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobDetails
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItemStatus
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobStatus
import java.util.UUID

@Repository
interface BulkUserJobRepository : JpaRepository<BulkUserJob, UUID> {

  fun findByJiraReferenceContainingIgnoreCaseOrRequestedByContainingIgnoreCase(
    jiraReference: String,
    requestedBy: String,
    pageable: Pageable,
  ): Page<BulkUserJob>

  @Query(
    """
      SELECT new uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobDetails(
        j.id,
        j.jiraReference,
        j.status,
        j.requestedBy,
        j.requestDateTime,
        COUNT(i.id),
        SUM(CASE WHEN i.status = 'SUCCESS' THEN 1L ELSE 0L END),
        SUM(CASE WHEN i.status = 'ERROR' THEN 1L ELSE 0L END)
      )
      FROM BulkUserJob j 
        LEFT JOIN j.jobItems i 
        WHERE j.id = :jobId 
      GROUP BY 
          j.id,
          j.jiraReference,
          j.status,
          j.requestedBy,
          j.requestDateTime
    """,
  )
  fun findDetailsById(@Param("jobId") jobId: UUID): BulkUserJobDetails?

  @Query(
    """
    SELECT b.id FROM BulkUserJob b
    WHERE b.id = :jobId
    AND b.status = :statusComplete
    AND NOT EXISTS (
        SELECT i FROM BulkUserJobItem i
        WHERE i.bulkUserJob.id = b.id
        AND i.status NOT IN :jobItemStatuses
    )
  """,
  )
  fun findCompletedJobById(
    @Param("jobId") jobId: UUID,
    @Param("statusComplete") status: BulkUserJobStatus = BulkUserJobStatus.COMPLETE,
    @Param("jobItemStatuses") jobItemStatuses: Set<BulkUserJobItemStatus> = setOf(
      BulkUserJobItemStatus.SUCCESS, BulkUserJobItemStatus.ERROR,
    ),
  ): UUID?
}

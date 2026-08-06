package uk.gov.justice.digital.hmpps.manageusersapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItem
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItemStatus
import java.util.UUID

@Repository
interface BulkUserJobItemRepository : JpaRepository<BulkUserJobItem, UUID> {
  @Transactional
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
    """
      UPDATE BulkUserJobItem i
      SET i.status = :newStatus
      WHERE i.id = :jobItemId AND i.status = :currentStatus
    """,
  )
  fun updateStatusIfCurrent(
    @Param("jobItemId") jobItemId: UUID,
    @Param("currentStatus") currentStatus: BulkUserJobItemStatus,
    @Param("newStatus") newStatus: BulkUserJobItemStatus,
  ): Int
}

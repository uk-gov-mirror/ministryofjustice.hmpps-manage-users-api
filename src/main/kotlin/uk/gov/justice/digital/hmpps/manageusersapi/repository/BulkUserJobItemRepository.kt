package uk.gov.justice.digital.hmpps.manageusersapi.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.manageusersapi.repository.model.BulkUserJobItem
import java.util.UUID
import java.util.stream.Stream

@Repository
interface BulkUserJobItemRepository : JpaRepository<BulkUserJobItem, UUID> {

  fun streamByBulkUserJobId(jobId: UUID): Stream<BulkUserJobItem>?
}

package uk.gov.justice.digital.hmpps.manageusersapi.repository.model

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "BULK_USER_JOB_ITEM")
data class BulkUserJobItem(

  @Id
  val id: UUID = UUID.randomUUID(),
  val username: String,
  val rolename: String,
  @Enumerated(EnumType.STRING)
  var status: BulkUserJobItemStatus = BulkUserJobItemStatus.CREATED,
  val result: String? = null,

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "bulk_user_job_id", referencedColumnName = "id", nullable = false)
  val bulkUserJob: BulkUserJob,
)

enum class BulkUserJobItemStatus {
  CREATED,
  CLAIMED,
  PUBLISHED,
  STARTED,
  SUCCESS,
  ERROR,
}

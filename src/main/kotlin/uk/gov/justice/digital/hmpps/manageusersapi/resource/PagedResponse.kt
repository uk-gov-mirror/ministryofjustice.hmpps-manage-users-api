package uk.gov.justice.digital.hmpps.manageusersapi.resource

import org.springframework.data.domain.Page

data class PagedResponse<T>(
  val content: List<T>,
  val pageable: PageDetails,
  val last: Boolean,
  val totalPages: Int,
  val totalElements: Long,
  val size: Int,
  val number: Int,
  val sort: PageSort,
  val numberOfElements: Int,
  val first: Boolean,
  val empty: Boolean,
)

data class PageDetails(
  val sort: PageSort,
  val offset: Int,
  val pageNumber: Int,
  val pageSize: Int,
  val paged: Boolean,
  val unpaged: Boolean,
)

data class PageSort(
  val sorted: Boolean,
  val unsorted: Boolean,
  val empty: Boolean,
)

fun <T : Any> Page<T>.toPagedResponse() = PagedResponse(
  content = content,
  pageable = PageDetails(
    sort = PageSort(
      sorted = pageable.sort.isSorted,
      unsorted = pageable.sort.isUnsorted,
      empty = pageable.sort.isEmpty,
    ),
    offset = pageable.takeIf { it.isPaged }?.offset?.toInt() ?: 0,
    pageNumber = pageable.takeIf { it.isPaged }?.pageNumber ?: 0,
    pageSize = pageable.takeIf { it.isPaged }?.pageSize ?: 0,
    paged = pageable.isPaged,
    unpaged = pageable.isUnpaged,
  ),
  last = isLast,
  totalPages = totalPages,
  totalElements = totalElements,
  size = size,
  number = number,
  sort = PageSort(
    sorted = sort.isSorted,
    unsorted = sort.isUnsorted,
    empty = sort.isEmpty,
  ),
  numberOfElements = numberOfElements,
  first = isFirst,
  empty = isEmpty,
)

package fc.ul.scrimfinder.dto.response;

import java.util.List;

public record PaginatedResponseDto<T>(List<T> data, int currentPage, int totalPages, long totalElements) {
}

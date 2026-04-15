package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.Queue;
import fc.ul.scrimfinder.dto.response.QueueDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta")
public interface QueueMapper {
    QueueDTO toDTO(Queue queue);
}

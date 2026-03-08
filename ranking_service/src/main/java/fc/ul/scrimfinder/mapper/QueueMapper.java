package fc.ul.scrimfinder.mapper;

import fc.ul.scrimfinder.domain.QueueEntity;
import fc.ul.scrimfinder.dto.response.QueueDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface QueueMapper {

    QueueEntity toEntity(QueueDTO queueDTO);

    QueueDTO toDTO(QueueEntity queue);
}

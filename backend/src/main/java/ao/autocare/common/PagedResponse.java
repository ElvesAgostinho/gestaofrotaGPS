package ao.autocare.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Envelope de paginação estável para as respostas da API
 * (evita serializar {@code PageImpl} diretamente).
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}

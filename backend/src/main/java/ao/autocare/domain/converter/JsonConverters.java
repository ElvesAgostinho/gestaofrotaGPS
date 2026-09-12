package ao.autocare.domain.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/**
 * Conversores JSON <-> TEXT. Guardar JSON como texto mantém as migrações
 * portáveis (H2 usa {@code JSON}, PostgreSQL usa {@code jsonb} — tipos diferentes).
 */
public final class JsonConverters {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonConverters() {}

    private static String write(Object value) {
        if (value == null) return null;
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar JSON", e);
        }
    }

    private static <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            return fallback;
        }
    }

    @Converter
    public static class StringListConverter implements AttributeConverter<List<String>, String> {
        @Override
        public String convertToDatabaseColumn(List<String> attribute) {
            return write(attribute);
        }

        @Override
        public List<String> convertToEntityAttribute(String dbData) {
            return read(dbData, new TypeReference<>() {}, List.of());
        }
    }

    @Converter
    public static class IntListConverter implements AttributeConverter<List<Integer>, String> {
        @Override
        public String convertToDatabaseColumn(List<Integer> attribute) {
            return write(attribute);
        }

        @Override
        public List<Integer> convertToEntityAttribute(String dbData) {
            return read(dbData, new TypeReference<>() {}, List.of());
        }
    }
}

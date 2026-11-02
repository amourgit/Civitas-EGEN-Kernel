package africa.civitas.egen.api.contract;

import africa.civitas.egen.api.dto.ServiceManifestDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Niveau 1 (docs 17). Le schema JSON du manifeste ({@code egen-contracts}) est le contrat publie aux
 * equipes de services (docs 06 : « schema JSON/YAML strict ») ; les DTO de l'API sont ce que le Kernel
 * lit reellement. Ce test echoue des qu'un bloc ou un champ existe d'un cote et pas de l'autre : un champ
 * accepte par le code mais absent du schema serait une promesse non publiee, un champ du schema que le
 * code ignore serait accepte puis perdu en silence.
 */
class ManifestSchemaConsistencyTest {

    private static final String SCHEMA = "/schema/service-manifest.schema.json";

    @Test
    void everyObjectOfTheSchemaHasExactlyTheFieldsOfItsDto() throws IOException {
        JsonNode schema;
        try (InputStream in = ManifestSchemaConsistencyTest.class.getResourceAsStream(SCHEMA)) {
            assertTrue(in != null, "le schema doit etre sur le classpath de test : " + SCHEMA);
            schema = new ObjectMapper().readTree(in);
        }

        List<String> differences = new ArrayList<>();
        compare("$", schema, ServiceManifestDto.class, differences);

        assertTrue(differences.isEmpty(), "schema et DTO divergent :\n  " + String.join("\n  ", differences));
    }

    private static void compare(String path, JsonNode schemaNode, Class<?> dto, List<String> differences) {
        JsonNode properties = schemaNode.get("properties");
        if (properties == null) {
            return; // map libre ou scalaire : rien a comparer champ par champ
        }
        TreeSet<String> schemaNames = new TreeSet<>();
        Iterator<String> names = properties.fieldNames();
        while (names.hasNext()) {
            schemaNames.add(names.next());
        }
        TreeSet<String> dtoNames = new TreeSet<>();
        for (Field field : fieldsOf(dto)) {
            dtoNames.add(jsonName(field));
        }
        TreeSet<String> onlyInSchema = new TreeSet<>(schemaNames);
        onlyInSchema.removeAll(dtoNames);
        TreeSet<String> onlyInDto = new TreeSet<>(dtoNames);
        onlyInDto.removeAll(schemaNames);
        if (!onlyInSchema.isEmpty()) {
            differences.add(path + " : dans le schema mais pas dans " + dto.getSimpleName() + " -> " + onlyInSchema);
        }
        if (!onlyInDto.isEmpty()) {
            differences.add(path + " : dans " + dto.getSimpleName() + " mais pas dans le schema -> " + onlyInDto);
        }

        for (Field field : fieldsOf(dto)) {
            JsonNode child = properties.get(jsonName(field));
            if (child == null) {
                continue;
            }
            if ("array".equals(child.path("type").asText()) && child.has("items")) {
                Class<?> element = elementType(field);
                if (element != null) {
                    compare(path + "." + jsonName(field) + "[]", child.get("items"), element, differences);
                }
            } else {
                compare(path + "." + jsonName(field), child, field.getType(), differences);
            }
        }
    }

    private static List<Field> fieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers()) && !Modifier.isStatic(field.getModifiers())) {
                fields.add(field);
            }
        }
        return fields;
    }

    private static String jsonName(Field field) {
        JsonProperty annotation = field.getAnnotation(JsonProperty.class);
        return annotation != null && !annotation.value().isEmpty() ? annotation.value() : field.getName();
    }

    private static Class<?> elementType(Field field) {
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
            return element;
        }
        return null;
    }
}

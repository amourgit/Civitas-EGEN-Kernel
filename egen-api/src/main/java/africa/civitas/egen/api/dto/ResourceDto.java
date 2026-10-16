package africa.civitas.egen.api.dto;

import java.util.Map;

/** Entree de {@code dependencies.resources} (voir docs/architecture/06) : referencee, jamais geree en V1. */
public class ResourceDto {
    public String name;
    public String type;
    public Map<String, String> params;
}

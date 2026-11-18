package africa.civitas.egen.api.dto;

/** Bloc {@code deployment.security} du manifeste (docs/architecture/06, constat K9). Champs tous facultatifs. */
public class SecurityDto {
    public Boolean runAsNonRoot;
    public Integer runAsUser;
    public Boolean readOnlyRootFilesystem;
    public Boolean dropAllCapabilities;
}

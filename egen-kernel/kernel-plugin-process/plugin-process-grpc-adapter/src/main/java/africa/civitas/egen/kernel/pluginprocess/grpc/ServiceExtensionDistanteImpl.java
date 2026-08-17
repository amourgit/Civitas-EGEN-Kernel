package africa.civitas.egen.kernel.pluginprocess.grpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;

/**
 * Cote processus plugin : recoit chaque {@link RequeteInvocation}, retrouve
 * l'instance d'extension reellement chargee correspondant a {@code
 * point_extension}, invoque reflectivement la methode demandee, et renvoie le
 * resultat serialise en JSON.
 *
 * <p>Deux categories d'echec, deliberement traitees differemment : un point
 * d'extension ou une methode introuvable est une anomalie du dispatcher lui-meme —
 * remonte comme erreur gRPC ({@link StreamObserver#onError}), traduite cote hote en
 * {@code PluginProcessException} par l'echec du transport. Une exception levee PAR
 * l'extension elle-meme, elle, est le comportement normal d'un appel qui a
 * parfaitement fonctionne au niveau transport — encodee dans {@link
 * ReponseInvocation#getEchec()}, une reponse normale, jamais une erreur gRPC.
 */
final class ServiceExtensionDistanteImpl extends ServiceExtensionDistanteGrpc.ServiceExtensionDistanteImplBase {

    private final Map<String, Object> extensionsParPointExtension;
    private final ObjectMapper objectMapper;

    ServiceExtensionDistanteImpl(Map<String, Object> extensionsParPointExtension, ObjectMapper objectMapper) {
        this.extensionsParPointExtension =
                Objects.requireNonNull(extensionsParPointExtension, "extensionsParPointExtension ne peut pas etre nul.");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper ne peut pas etre nul.");
    }

    @Override
    public void invoquer(RequeteInvocation requete, StreamObserver<ReponseInvocation> responseObserver) {
        try {
            responseObserver.onNext(traiter(requete));
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription(e.getMessage()).withCause(e).asRuntimeException());
        }
    }

    @Override
    public void sonderSante(RequeteSonde requete, StreamObserver<ReponseSonde> responseObserver) {
        responseObserver.onNext(ReponseSonde.newBuilder().setVivant(true).build());
        responseObserver.onCompleted();
    }

    private ReponseInvocation traiter(RequeteInvocation requete) {
        Object extension = extensionsParPointExtension.get(requete.getPointExtension());
        if (extension == null) {
            throw new IllegalStateException(
                    "Aucune extension chargee pour le point '" + requete.getPointExtension() + "'.");
        }

        Method methode = trouverMethode(extension.getClass(), requete);

        try {
            Object[] arguments = deserialiserArguments(methode, requete);
            Object resultat = methode.invoke(extension, arguments);
            String resultatJson = methode.getReturnType() == void.class
                    ? ""
                    : objectMapper.writeValueAsString(resultat);
            return ReponseInvocation.newBuilder().setValeurRetourJson(resultatJson).build();
        } catch (InvocationTargetException e) {
            Throwable causeReelle = e.getCause() != null ? e.getCause() : e;
            return ReponseInvocation.newBuilder()
                    .setEchec(EchecInvocation.newBuilder()
                            .setTypeExceptionDistant(causeReelle.getClass().getName())
                            .setMessageDistant(String.valueOf(causeReelle.getMessage()))
                            .build())
                    .build();
        } catch (IllegalAccessException | JsonProcessingException e) {
            throw new IllegalStateException(
                    "Echec d'invocation de '" + requete.getMethode() + "' : " + e.getMessage(), e);
        }
    }

    private Method trouverMethode(Class<?> classeExtension, RequeteInvocation requete) {
        Class<?>[] typesParametres = new Class<?>[requete.getTypesParametresCount()];
        for (int i = 0; i < typesParametres.length; i++) {
            typesParametres[i] = chargerClasse(requete.getTypesParametres(i));
        }
        try {
            return classeExtension.getMethod(requete.getMethode(), typesParametres);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "Methode '" + requete.getMethode() + "' introuvable sur " + classeExtension.getName()
                            + " avec ces types de parametres.", e);
        }
    }

    private Object[] deserialiserArguments(Method methode, RequeteInvocation requete) throws JsonProcessingException {
        Type[] typesGeneriques = methode.getGenericParameterTypes();
        Object[] arguments = new Object[typesGeneriques.length];
        for (int i = 0; i < typesGeneriques.length; i++) {
            arguments[i] = objectMapper.readValue(
                    requete.getArgumentsJson(i), objectMapper.getTypeFactory().constructType(typesGeneriques[i]));
        }
        return arguments;
    }

    private static Class<?> chargerClasse(String nomBinaire) {
        try {
            return Class.forName(nomBinaire);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Type de parametre introuvable : " + nomBinaire, e);
        }
    }
}

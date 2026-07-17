/**
 * Contrat public du Kernel EGEN.
 *
 * Ce module ne depend d'aucun autre module (ni du Kernel, ni d'un framework) — c'est
 * precisement ce qui lui permet d'etre un veritable module JPMS, sans la friction que
 * rencontrerait un module portant du CDI, de la persistance ou de la reflexion dynamique.
 *
 * Tout ce qu'un module metier externe a le droit de connaitre du Kernel transite par
 * l'un des quatre paquets exportes ici. Rien d'autre, dans tout le Kernel, ne doit etre
 * considere comme un contrat stable au meme titre que ce module.
 */
module africa.civitas.egen.kernel.sdk {
    exports africa.civitas.egen.kernel.sdk.extension;
    exports africa.civitas.egen.kernel.sdk.event;
    exports africa.civitas.egen.kernel.sdk.contexte;
    exports africa.civitas.egen.kernel.sdk.manifest;
}

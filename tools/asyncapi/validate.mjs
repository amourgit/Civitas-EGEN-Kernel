// Valide un document AsyncAPI avec l'analyseur officiel. Usage : node validate.mjs <fichier.yaml>
import { readFileSync } from "node:fs";
import { Parser } from "@asyncapi/parser";

const fichier = process.argv[2];
if (!fichier) {
  console.error("usage : node validate.mjs <fichier.yaml>");
  process.exit(2);
}
const parser = new Parser();
const { document, diagnostics } = await parser.parse(readFileSync(fichier, "utf-8"));
const erreurs = diagnostics.filter((d) => d.severity === 0);
for (const d of diagnostics) {
  console.error(`${["erreur", "avertissement", "info", "indice"][d.severity]} ${d.code} ${d.path.join("/")} : ${d.message}`);
}
if (!document || erreurs.length > 0) {
  console.error(`INVALIDE : ${erreurs.length} erreur(s)`);
  process.exit(1);
}
const version = document.version();
const canaux = document.channels().all().length;
console.log(`VALIDE : AsyncAPI ${version}, ${canaux} canaux`);

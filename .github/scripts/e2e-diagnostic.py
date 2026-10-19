"""Diagnostic du test de niveau 5 : lit /tmp/e2e.log et ecrit /tmp/payload-e2e.json (corps d'un commentaire de commit)."""
import json
import os
import re

outcome = os.environ["OUTCOME"]
run_url = os.environ["RUN_URL"]
try:
    content = open("/tmp/e2e.log", "r", errors="replace").read()
except OSError:
    content = "(aucun journal : l'etape Maven n'a pas demarre)"
lines = content.splitlines()

if outcome != "failure":
    summary = [l for l in lines if "Tests run:" in l or "BUILD SUCCESS" in l]
    body = ("### [e2e] Resume automatique - test de niveau 5 reussi\n\n"
            f"Run : {run_url}\n\n```\n" + "\n".join(summary) + "\n```")
else:
    # 1. Recapitulatif Surefire : un bloc par test en echec, chacun borne.
    failures = []
    inside = False
    for line in lines:
        if line.startswith("[ERROR] Failures:") or line.startswith("[ERROR] Errors:"):
            inside = True
            continue
        if inside and line.startswith("[ERROR] Tests run:"):
            inside = False
        if inside:
            failures.append(line)
    summary = "\n".join(failures)
    blocks = re.split(r"(?m)^\[ERROR\]   ", summary)
    summary = "\n".join(("[ERROR]   " + b)[:1800] for b in blocks if b.strip())[:14000]

    # 2. Ce que le Kernel a reellement observe : Conditions DISTINCTES par service, dans l'ordre d'apparition.
    pattern = re.compile(r'serviceId=(\S+) .*?condition=(\S+) status=(\S+) reason="([^"]*)"(?: message="([^"]*)")?')
    seen, conditions = set(), []
    for line in lines:
        m = pattern.search(line)
        if m:
            key = m.groups()[:4]
            if key not in seen:
                seen.add(key)
                conditions.append(" | ".join(g or "" for g in m.groups())[:320])
    conditions_text = "\n".join(conditions[:140])

    # 3. Erreurs et avertissements du Kernel (hors bruit de l'exporteur OTLP, sans collecteur dans cet environnement).
    noise = re.compile(r"4317|OtlpGrpc|opentelemetry|SpanExporter|io\.opentelemetry", re.I)
    kernel_problems = [l[:300] for l in lines if re.search(r"\b(WARN|ERROR|SEVERE)\b", l) and not noise.search(l)
                       and "Tests run" not in l and "[ERROR]" not in l[:9]]
    problems_text = "\n".join(dict.fromkeys(kernel_problems))[:9000]

    body = ("### [e2e] Diagnostic automatique - echec du test de niveau 5\n\n"
            f"Run : {run_url}\n\n**Tests en echec**\n```\n{summary}\n```\n\n"
            f"**Conditions distinctes observees par le Kernel** (service | condition | status | reason | message)\n"
            f"```\n{conditions_text}\n```\n\n**Avertissements et erreurs du Kernel**\n```\n{problems_text}\n```")

json.dump({"body": body[:64000]}, open("/tmp/payload-e2e.json", "w"))

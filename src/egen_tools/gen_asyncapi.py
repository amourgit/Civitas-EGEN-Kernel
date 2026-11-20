"""Génère le document AsyncAPI 3.0.0 des événements C5 à partir du catalogue ``contracts/catalog/event-types.json``."""
from __future__ import annotations

import argparse
import copy
import sys
from pathlib import Path

import yaml

from .contracts import Contracts
from .deref import deref, deref_document

ENTETE = "# Généré par egen-gen-asyncapi depuis contracts/catalog/event-types.json et contracts/schemas/.\n# Ne pas modifier à la main : relancer le générateur.\n"


def _id(event_type: str) -> str:
    parts = event_type.removeprefix("africa.civitas.egen.").removesuffix(".v1").replace("-", ".").split(".")
    return parts[0] + "".join(p.capitalize() for p in parts[1:])


def build(contracts: Contracts) -> dict:
    envelope = deref_document("cloudevent.schema.json", contracts)
    data_doc = contracts.schemas["event-data.schema.json"]
    channels, messages, operations = {}, {}, {}
    for etype, entry in contracts.event_types.items():
        ident = _id(etype)
        payload = copy.deepcopy(envelope)
        payload["properties"]["type"] = {"const": etype}
        payload["properties"]["data"] = deref(
            {"$ref": f"event-data.schema.json#/$defs/{entry['dataSchema']}"}, data_doc["$id"], contracts
        )
        messages[ident] = {
            "name": etype,
            "title": entry["description"],
            "contentType": "application/cloudevents+json",
            "payload": payload,
        }
        channels[ident] = {"address": etype, "messages": {ident: {"$ref": f"#/components/messages/{ident}"}}}
        operations["send" + ident[0].upper() + ident[1:]] = {
            "action": "send",
            "channel": {"$ref": f"#/channels/{ident}"},
            "summary": entry["description"],
        }
    return {
        "asyncapi": "3.0.0",
        "info": {
            "title": "Contrats EGEN — événements C5",
            "version": (contracts.root / "VERSION").read_text().strip(),
            "description": "Événements publiés par un Control Plane (format CloudEvents 1.0, transport NATS JetStream par défaut).",
        },
        "defaultContentType": "application/cloudevents+json",
        "servers": {"nats": {"host": "nats.example:4222", "protocol": "nats",
                             "description": "Transport par défaut (NATS JetStream)"}},
        "channels": channels,
        "operations": operations,
        "components": {"messages": messages},
    }


def render(contracts: Contracts) -> str:
    return ENTETE + yaml.safe_dump(build(contracts), sort_keys=False, allow_unicode=True, width=100)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--contracts", type=Path, default=None)
    ap.add_argument("--check", action="store_true", help="échoue si le fichier committé diffère de la sortie générée")
    args = ap.parse_args(argv)
    contracts = Contracts(args.contracts)
    target = contracts.root / "asyncapi" / "egen-events-v1.asyncapi.yaml"
    out = render(contracts)
    if args.check:
        if not target.exists() or target.read_text(encoding="utf-8") != out:
            print(f"ÉCART : {target} diffère de la sortie du générateur", file=sys.stderr)
            return 1
        print("AsyncAPI à jour")
        return 0
    target.write_text(out, encoding="utf-8")
    print(f"écrit : {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

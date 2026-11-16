#!/usr/bin/env python3
"""Vérifie que egen_control.proto reste compatible avec le descripteur de référence (version v1).

Usage : python3 egen-contracts/tools/check_proto_compat.py        (depuis la racine du dépôt)
Prérequis : pip install grpcio-tools

Règles (docs/architecture/13, « Surface gRPC », compatibilité) : on peut AJOUTER des messages, des champs
(avec un nouveau numéro) et des méthodes. On ne peut JAMAIS supprimer ni renommer un message, un champ, une
méthode ou un service, ni changer le numéro, le type ou la cardinalité d'un champ, ni la signature d'une méthode.
Une évolution incompatible crée un nouveau paquet (v2). Code de sortie : 0 = compatible, 1 = incompatible.
"""
import os
import subprocess
import sys
import tempfile

from google.protobuf import descriptor_pb2

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, ".."))
PROTO_DIR = os.path.join(ROOT, "src", "main", "resources", "proto")
BASELINE = os.path.join(ROOT, "baseline", "egen_control.v1.descriptor")


def load(path):
    fds = descriptor_pb2.FileDescriptorSet()
    with open(path, "rb") as f:
        fds.ParseFromString(f.read())
    return fds.file[0]


def compile_current():
    out = os.path.join(tempfile.mkdtemp(), "current.descriptor")
    subprocess.run([sys.executable, "-m", "grpc_tools.protoc", "-I" + PROTO_DIR,
                    "--descriptor_set_out=" + out, os.path.join(PROTO_DIR, "egen_control.proto")], check=True)
    return load(out)


def type_name(field):
    return field.type_name if field.type_name else descriptor_pb2.FieldDescriptorProto.Type.Name(field.type)


def compare(base, cur):
    errors = []
    if base.package != cur.package:
        errors.append(f"le paquet a change : {base.package} -> {cur.package} (une evolution incompatible cree un paquet v2)")
    cur_msgs = {m.name: m for m in cur.message_type}
    for bm in base.message_type:
        cm = cur_msgs.get(bm.name)
        if cm is None:
            errors.append(f"message supprime ou renomme : {bm.name}")
            continue
        cur_fields = {f.number: f for f in cm.field}
        for bf in bm.field:
            cf = cur_fields.get(bf.number)
            if cf is None:
                errors.append(f"{bm.name} : champ {bf.name} (numero {bf.number}) supprime")
            elif cf.name != bf.name:
                errors.append(f"{bm.name} : le champ numero {bf.number} est renomme {bf.name} -> {cf.name}")
            elif type_name(cf) != type_name(bf):
                errors.append(f"{bm.name}.{bf.name} : type change {type_name(bf)} -> {type_name(cf)}")
            elif cf.label != bf.label:
                errors.append(f"{bm.name}.{bf.name} : cardinalite changee (repeated/optional)")
    cur_services = {s.name: s for s in cur.service}
    for bs in base.service:
        cs = cur_services.get(bs.name)
        if cs is None:
            errors.append(f"service supprime ou renomme : {bs.name}")
            continue
        cur_methods = {m.name: m for m in cs.method}
        for bmeth in bs.method:
            cmeth = cur_methods.get(bmeth.name)
            if cmeth is None:
                errors.append(f"{bs.name} : methode {bmeth.name} supprimee ou renommee")
            elif (cmeth.input_type, cmeth.output_type, cmeth.client_streaming, cmeth.server_streaming) != \
                    (bmeth.input_type, bmeth.output_type, bmeth.client_streaming, bmeth.server_streaming):
                errors.append(f"{bs.name}.{bmeth.name} : signature changee")
    return errors


def main():
    errors = compare(load(BASELINE), compile_current())
    if errors:
        print("CONTRAT INCOMPATIBLE avec la reference v1 :")
        for e in errors:
            print("  -", e)
        return 1
    print("Contrat compatible avec la reference v1.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

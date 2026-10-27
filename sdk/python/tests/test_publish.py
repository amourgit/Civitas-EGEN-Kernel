"""``publish`` contre un VRAI serveur HTTP local qui joue le Kernel (docs/architecture/12, "Resilience de la
publication d'evenements"). Les memes scenarios, sous les memes noms, sont rejoues par le SDK Java."""
import json
import re
import socket
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from egen_sdk import (CallFailedError, CircuitOpenError, EgenClient, EgenError, PublishReceipt,
                      PublishRejectedError, ResiliencyPolicy)
from egen_sdk.policy import CircuitBreakerSpec, Retry

TYPE = "africa.civitas.news.article.created.v1"
RECEIPT = json.dumps({"id": "evt-1", "type": TYPE, "egen_correlation_id": "c-1"})
UUID_RE = r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"


def policy(max_attempts, threshold, timeout_millis):
    return ResiliencyPolicy(timeout_millis, Retry(max_attempts, "fixed", 50), CircuitBreakerSpec(threshold, 60000))


class PublishTest(unittest.TestCase):
    def setUp(self):
        self.seen = []
        self.statuses = []
        self.delay = 0.0
        self.raw_receipt = None
        self.sleeps = []
        test = self

        class Kernel(BaseHTTPRequestHandler):
            def do_POST(self):
                length = int(self.headers.get("Content-Length") or 0)
                body = self.rfile.read(length).decode("utf-8") if length else ""
                test.seen.append({"method": self.command, "path": self.path,
                                  "headers": {k.lower(): v for k, v in self.headers.items()}, "body": body})
                if test.delay:
                    time.sleep(test.delay)
                status = test.statuses.pop(0) if test.statuses else 202
                if status == 202:
                    payload = (test.raw_receipt if test.raw_receipt is not None else RECEIPT).encode()
                else:
                    payload = f"refus-{status}".encode()
                self.send_response(status)
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                try:
                    self.wfile.write(payload)
                except OSError:
                    pass

            def log_message(self, *args):
                pass

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Kernel)
        threading.Thread(target=self.server.serve_forever, kwargs={"poll_interval": 0.01}, daemon=True).start()
        self.url = f"http://127.0.0.1:{self.server.server_address[1]}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()

    def client(self, **kwargs):
        kwargs.setdefault("event_source", "/news-service")
        return EgenClient(self.url, sleeper=self.sleeps.append, **kwargs)

    # ------------------------------------------------------------------ nominal

    def test_publish_relays_an_envelope_and_returns_the_receipt(self):
        receipt = self.client(token_supplier=lambda: "jeton-123").publish(TYPE, {"articleId": "42"})

        self.assertEqual(PublishReceipt("evt-1", TYPE, "c-1"), receipt)
        self.assertEqual(1, len(self.seen))
        request = self.seen[0]
        self.assertEqual("POST", request["method"])
        self.assertEqual("/api/v1/events/publish", request["path"])
        self.assertEqual("Bearer jeton-123", request["headers"]["authorization"])
        self.assertTrue(request["headers"]["content-type"].startswith("application/json"))
        self.assertRegex(request["headers"]["traceparent"], r"^00-[0-9a-f]{32}-[0-9a-f]{16}-01$")
        envelope = json.loads(request["body"])
        self.assertEqual("1.0", envelope["specversion"])
        self.assertEqual(TYPE, envelope["type"])
        self.assertEqual("/news-service", envelope["source"])
        self.assertEqual({"articleId": "42"}, envelope["data"])
        # L'egen_correlation_id du corps a la MEME valeur que l'en-tete (genere une seule fois).
        self.assertEqual(request["headers"]["egen-correlation-id"], envelope["egen_correlation_id"])
        self.assertRegex(request["headers"]["idempotency-key"], "^" + UUID_RE + "$")

    def test_data_is_opaque_and_never_rewritten(self):
        data = {"titre": 'Caf\u00e9 "cr\u00e8me"', "tags": [1, 2.5, None, True], "nested": {"a": []}}

        self.client().publish(TYPE, data)

        self.assertEqual(data, json.loads(self.seen[0]["body"])["data"])
        self.assertIn('"data":' + json.dumps(data, ensure_ascii=False, separators=(",", ":")), self.seen[0]["body"])

    def test_a_per_call_source_overrides_the_clients_source(self):
        self.client().publish(TYPE, {}, source="/autre-service")

        self.assertEqual("/autre-service", json.loads(self.seen[0]["body"])["source"])

    def test_a_caller_supplied_context_and_idempotency_key_are_propagated_unchanged(self):
        traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"

        self.client().publish(TYPE, {}, traceparent=traceparent, correlation_id="corr-123",
                              causation_id="cause-9", idempotency_key="cle-fournie")

        request = self.seen[0]
        self.assertEqual(traceparent, request["headers"]["traceparent"])
        self.assertEqual("corr-123", request["headers"]["egen-correlation-id"])
        self.assertEqual("cle-fournie", request["headers"]["idempotency-key"])
        envelope = json.loads(request["body"])
        self.assertEqual("corr-123", envelope["egen_correlation_id"])
        self.assertEqual("cause-9", envelope["egen_causation_id"])

    # ------------------------------------------------------------------ resilience (docs/architecture/12)

    def test_the_same_idempotency_key_and_context_are_sent_on_every_attempt(self):
        self.statuses.extend([503, 503, 202])

        receipt = self.client(publish_policy=policy(3, 100, 5000)).publish(TYPE, {})

        self.assertEqual("evt-1", receipt.id)
        self.assertEqual(3, len(self.seen))
        self.assertEqual([50, 50], self.sleeps)
        for header in ("idempotency-key", "egen-correlation-id", "traceparent"):
            self.assertEqual(1, len({s["headers"][header] for s in self.seen}), header)
        self.assertEqual(1, len({s["body"] for s in self.seen}))

    def test_a_definitive_response_is_never_retried(self):
        self.statuses.append(403)

        with self.assertRaises(PublishRejectedError) as ctx:
            self.client(publish_policy=policy(3, 100, 5000)).publish(TYPE, {})

        self.assertEqual(403, ctx.exception.status)
        self.assertEqual(1, ctx.exception.attempts)
        self.assertEqual(1, len(self.seen))
        self.assertEqual([], self.sleeps)

    def test_a_persistent_server_failure_is_rejected_after_all_attempts(self):
        self.statuses.extend([503, 503, 503])

        with self.assertRaises(PublishRejectedError) as ctx:
            self.client(publish_policy=policy(3, 100, 5000)).publish(TYPE, {})

        self.assertEqual(503, ctx.exception.status)
        self.assertEqual(3, ctx.exception.attempts)
        self.assertEqual(3, len(self.seen))
        self.assertIn("refus-503", str(ctx.exception))

    def test_by_default_there_is_a_single_attempt(self):
        self.statuses.extend([503, 202])

        with self.assertRaises(PublishRejectedError):
            self.client().publish(TYPE, {})

        self.assertEqual(1, len(self.seen))

    def test_no_response_at_all_is_a_call_failed(self):
        with socket.socket() as s:
            s.bind(("127.0.0.1", 0))
            closed_port = s.getsockname()[1]
        client = EgenClient(f"http://127.0.0.1:{closed_port}", event_source="/s", sleeper=self.sleeps.append,
                            publish_policy=policy(2, 100, 5000))

        with self.assertRaises(CallFailedError) as ctx:
            client.publish(TYPE, {})

        self.assertEqual("network", ctx.exception.reason)
        self.assertEqual(2, ctx.exception.attempts)

    def test_a_timeout_is_a_call_failed_with_reason_timeout(self):
        self.delay = 0.4

        with self.assertRaises(CallFailedError) as ctx:
            self.client(publish_policy=policy(1, 100, 100)).publish(TYPE, {})

        self.assertEqual("timeout", ctx.exception.reason)

    def test_the_dedicated_circuit_opens_and_rejects_without_any_network_access(self):
        self.statuses.extend([503, 503])
        client = self.client(publish_policy=policy(1, 2, 5000))

        with self.assertRaises(PublishRejectedError):
            client.publish(TYPE, {})
        with self.assertRaises(PublishRejectedError):
            client.publish(TYPE, {})
        with self.assertRaises(CircuitOpenError):
            client.publish(TYPE, {})

        self.assertEqual(2, len(self.seen))

    # ------------------------------------------------------------------ validation locale : rien n'est envoye

    def test_a_missing_type_source_or_invalid_data_is_rejected_before_any_network_access(self):
        no_source = EgenClient(self.url)

        with self.assertRaises(ValueError):
            self.client().publish(" ", {})
        with self.assertRaises(ValueError):
            no_source.publish(TYPE, {})
        with self.assertRaises(ValueError):
            self.client().publish(TYPE, {"x": object()})
        with self.assertRaises(ValueError):
            self.client().publish(TYPE, {"x": float("nan")})

        self.assertEqual([], self.seen)

    def test_an_unreadable_receipt_is_an_explicit_error(self):
        self.raw_receipt = "pas du json"

        with self.assertRaises(EgenError):
            self.client().publish(TYPE, {})


if __name__ == "__main__":
    unittest.main()

"""Le client contre de VRAIS serveurs HTTP locaux : un plan de controle simule et des instances cibles."""
import json
import re
import socket
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from egen_sdk import (CallFailedError, CircuitOpenError, EgenClient, EgenError, EgenRequest, NoInstanceError,
                      ResiliencyPolicy)


class Target:
    """Instance cible scriptee : statuts renvoyes dans l'ordre (200 par defaut), tout est enregistre."""

    def __init__(self):
        self.seen = []
        self.statuses = []
        self.delay = 0.0
        target = self

        class Handler(BaseHTTPRequestHandler):
            def _handle(self):
                length = int(self.headers.get("Content-Length") or 0)
                body = self.rfile.read(length).decode("utf-8") if length else ""
                target.seen.append({"method": self.command, "path": self.path,
                                    "headers": {k.lower(): v for k, v in self.headers.items()}, "body": body})
                if target.delay:
                    import time
                    time.sleep(target.delay)
                status = target.statuses.pop(0) if target.statuses else 200
                payload = f"reponse-{status}".encode()
                self.send_response(status)
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                try:
                    self.wfile.write(payload)
                except OSError:
                    pass

            do_GET = do_POST = do_PUT = do_DELETE = _handle

            def log_message(self, *args):
                pass

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.port = self.server.server_address[1]
        threading.Thread(target=self.server.serve_forever, kwargs={"poll_interval": 0.01}, daemon=True).start()

    def respond(self, *codes):
        self.statuses.extend(codes)
        return self

    def stop(self):
        self.server.shutdown()
        self.server.server_close()


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def resiliency(max_attempts, threshold, timeout_millis):
    return {"timeoutMillis": timeout_millis,
            "retry": {"maxAttempts": max_attempts, "backoff": "fixed", "baseDelayMillis": 50},
            "circuitBreaker": {"failureThreshold": threshold, "openDurationMillis": 60000}}


class ClientTest(unittest.TestCase):
    def setUp(self):
        self.discover_hits = 0
        self.discover_json = None
        self.discover_status = 200
        self.authorizations = []
        self.targets = []
        self.clock = [1_000_000]
        self.sleeps = []
        test = self

        class ControlPlane(BaseHTTPRequestHandler):
            def do_GET(self):
                test.discover_hits += 1
                test.authorizations.append(str(self.headers.get("Authorization")))
                payload = (test.discover_json or "{}").encode()
                self.send_response(test.discover_status)
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

            def log_message(self, *args):
                pass

        self.cp = ThreadingHTTPServer(("127.0.0.1", 0), ControlPlane)
        threading.Thread(target=self.cp.serve_forever, kwargs={"poll_interval": 0.01}, daemon=True).start()
        self.cp_url = f"http://127.0.0.1:{self.cp.server_address[1]}"

    def tearDown(self):
        self.cp.shutdown()
        self.cp.server_close()
        for t in self.targets:
            t.stop()

    def target(self):
        t = Target()
        self.targets.append(t)
        return t

    def publish(self, policy, *instances):
        body = {"serviceId": "news-service",
                "instances": [{"instanceId": f"inst-{i}", "address": "127.0.0.1", "port": t.port}
                              for i, t in enumerate(instances)]}
        if policy is not None:
            body["resiliency"] = policy
        self.discover_json = json.dumps(body)

    def client(self, **kwargs):
        kwargs.setdefault("clock_millis", lambda: self.clock[0])
        kwargs.setdefault("sleeper", self.sleeps.append)
        return EgenClient(self.cp_url, **kwargs)

    # ------------------------------------------------------------------ appel nominal

    def test_call_resolves_then_forwards_method_path_and_body_and_returns_the_response_as_is(self):
        news = self.target()
        self.publish(None, news)

        response = self.client().call("news-service", EgenRequest.post("/internal/validate", '{"id":42}'))

        self.assertEqual(200, response.status)
        self.assertEqual("reponse-200", response.text)
        self.assertTrue(response.is_success)
        self.assertEqual(1, len(news.seen))
        self.assertEqual("POST", news.seen[0]["method"])
        self.assertEqual("/internal/validate", news.seen[0]["path"])
        self.assertEqual('{"id":42}', news.seen[0]["body"])

    def test_generates_w3c_traceparent_and_correlation_id_when_absent(self):
        news = self.target()
        self.publish(None, news)

        self.client().call("news-service", EgenRequest.get("/x"))

        headers = news.seen[0]["headers"]
        self.assertRegex(headers["traceparent"], r"^00-[0-9a-f]{32}-[0-9a-f]{16}-01$")
        self.assertRegex(headers["egen-correlation-id"],
                         r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    def test_propagates_the_callers_context_unchanged_and_keeps_it_across_retries(self):
        news = self.target().respond(503, 200)
        self.publish(resiliency(3, 100, 5000), news)
        traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"

        self.client().call("news-service", EgenRequest.get("/x").with_traceparent(traceparent)
                           .with_correlation_id("corr-123").with_header("X-Custom", "v"))

        self.assertEqual(2, len(news.seen))
        for seen in news.seen:
            self.assertEqual(traceparent, seen["headers"]["traceparent"])
            self.assertEqual("corr-123", seen["headers"]["egen-correlation-id"])
            self.assertEqual("v", seen["headers"]["x-custom"])

    def test_the_control_plane_token_goes_to_the_control_plane_only_never_to_the_target(self):
        news = self.target()
        self.publish(None, news)

        self.client(token_supplier=lambda: "secret-token").call("news-service", EgenRequest.get("/x"))

        self.assertEqual(["Bearer secret-token"], self.authorizations)
        self.assertNotIn("authorization", news.seen[0]["headers"])

    # ------------------------------------------------------------------ resilience

    def test_a_retry_targets_the_next_instance_not_the_failing_one(self):
        broken = self.target().respond(503, 503, 503)
        healthy = self.target()
        self.publish(resiliency(3, 100, 5000), broken, healthy)

        response = self.client().call("news-service", EgenRequest.get("/x"))

        self.assertEqual(200, response.status)
        self.assertEqual(1, len(broken.seen), "l'instance defaillante n'est pas retentee")
        self.assertEqual(1, len(healthy.seen))
        self.assertEqual([50], self.sleeps)

    def test_successive_calls_rotate_across_instances(self):
        a, b = self.target(), self.target()
        self.publish(None, a, b)
        client = self.client()

        for _ in range(4):
            client.call("news-service", EgenRequest.get("/x"))

        self.assertEqual(2, len(a.seen))
        self.assertEqual(2, len(b.seen))

    def test_a_definitive_client_error_is_returned_as_is_and_never_retried(self):
        news = self.target().respond(404, 200)
        self.publish(resiliency(3, 100, 5000), news)

        response = self.client().call("news-service", EgenRequest.get("/x"))

        self.assertEqual(404, response.status)
        self.assertEqual(1, len(news.seen))
        self.assertEqual([], self.sleeps)

    def test_exhausted_retries_on_a_5xx_return_the_last_response_instead_of_raising(self):
        news = self.target().respond(503, 502, 500)
        self.publish(resiliency(3, 100, 5000), news)

        response = self.client().call("news-service", EgenRequest.get("/x"))

        self.assertEqual(500, response.status)
        self.assertEqual(3, len(news.seen))
        self.assertEqual([50, 50], self.sleeps)

    def test_the_circuit_opens_after_the_threshold_and_later_calls_never_reach_the_network(self):
        news = self.target().respond(503, 503)
        self.publish(resiliency(1, 2, 5000), news)
        client = self.client()

        client.call("news-service", EgenRequest.get("/x"))
        client.call("news-service", EgenRequest.get("/x"))
        with self.assertRaises(CircuitOpenError) as ctx:
            client.call("news-service", EgenRequest.get("/x"))

        self.assertEqual("news-service", ctx.exception.service)
        self.assertEqual(2, len(news.seen), "le troisieme appel est rejete sans acces reseau")

    def test_the_circuit_closes_again_after_an_admitted_probe_succeeds(self):
        news = self.target().respond(503)
        self.publish(resiliency(1, 1, 5000), news)
        client = self.client()
        client.call("news-service", EgenRequest.get("/x"))  # ouvre le circuit (seuil 1)
        with self.assertRaises(CircuitOpenError):
            client.call("news-service", EgenRequest.get("/x"))

        self.clock[0] += 60_000  # openDuration ecoulee -> essai semi-ouvert admis

        self.assertEqual(200, client.call("news-service", EgenRequest.get("/x")).status)
        self.assertEqual(200, client.call("news-service", EgenRequest.get("/x")).status)

    def test_a_slow_target_ends_in_a_timeout_failure_after_all_attempts(self):
        news = self.target()
        news.delay = 0.6
        self.publish(resiliency(2, 100, 150), news)

        with self.assertRaises(CallFailedError) as ctx:
            self.client().call("news-service", EgenRequest.get("/x"))

        self.assertEqual("timeout", ctx.exception.reason)
        self.assertEqual(2, ctx.exception.attempts)

    def test_an_unreachable_instance_ends_in_a_network_failure(self):
        self.discover_json = json.dumps({
            "serviceId": "news-service",
            "instances": [{"instanceId": "i", "address": "127.0.0.1", "port": free_port()}],
            "resiliency": resiliency(2, 100, 2000)})

        with self.assertRaises(CallFailedError) as ctx:
            self.client().call("news-service", EgenRequest.get("/x"))

        self.assertEqual("network", ctx.exception.reason)
        self.assertEqual(2, ctx.exception.attempts)

    # ------------------------------------------------------------------ resolution

    def test_no_instance_fails_fast_and_is_never_cached(self):
        self.discover_json = json.dumps({"serviceId": "news-service", "instances": []})
        client = self.client()
        with self.assertRaises(NoInstanceError):
            client.call("news-service", EgenRequest.get("/x"))

        news = self.target()
        self.publish(None, news)

        self.assertEqual(200, client.call("news-service", EgenRequest.get("/x")).status)
        self.assertEqual(2, self.discover_hits, "un resultat vide n'est pas garde en cache")

    def test_resolution_is_cached_until_the_ttl_expires(self):
        news = self.target()
        self.publish(None, news)
        client = self.client(resolve_cache_ttl=5.0)

        client.call("news-service", EgenRequest.get("/x"))
        client.call("news-service", EgenRequest.get("/x"))
        self.assertEqual(1, self.discover_hits)

        self.clock[0] += 5_000
        client.call("news-service", EgenRequest.get("/x"))
        self.assertEqual(2, self.discover_hits)

    def test_a_resolution_without_a_resiliency_block_uses_the_defaults(self):
        news = self.target()
        self.publish(None, news)

        self.assertEqual(ResiliencyPolicy.defaults(), self.client().resolve("news-service").resiliency)

    def test_a_control_plane_error_is_reported_explicitly(self):
        self.discover_status = 500
        with self.assertRaisesRegex(EgenError, "HTTP 500"):
            self.client().resolve("news-service")

    def test_an_unreachable_control_plane_is_reported_explicitly(self):
        with self.assertRaises(EgenError):
            EgenClient(f"http://127.0.0.1:{free_port()}").resolve("news-service")

    def test_an_unreadable_resolution_is_reported_explicitly(self):
        self.discover_json = "ceci n'est pas du json"
        with self.assertRaises(EgenError):
            self.client().resolve("news-service")

    def test_targets_are_addressed_in_https_only_when_an_ssl_context_is_configured(self):
        import ssl
        self.assertEqual("http", self.client().target_scheme)
        self.assertEqual("https", self.client(ssl_context=ssl.create_default_context()).target_scheme)

    def test_request_validation(self):
        with self.assertRaises(ValueError):
            EgenRequest.get("sans-slash")
        with self.assertRaises(ValueError):
            EgenClient(" ")
        self.assertIsNone(EgenRequest.get("/x").body)


if __name__ == "__main__":
    unittest.main()

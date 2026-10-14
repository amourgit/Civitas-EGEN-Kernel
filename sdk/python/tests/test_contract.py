"""Rejoue TOUS les scenarios de ``sdk/contract/resiliency-contract.json`` — le meme fichier que
rejouent le Kernel (scope=breaker) et le SDK Java (docs/architecture/12, "Semantique normative")."""
import json
import os
import unittest
from pathlib import Path

from egen_sdk.breaker import CircuitBreaker
from egen_sdk.executor import Outcome, ResilienceExecutor
from egen_sdk.policy import MAX_BACKOFF_MILLIS, ResiliencyPolicy


def contract_path() -> Path:
    override = os.environ.get("EGEN_CONTRACT_PATH")
    if override:
        return Path(override)
    return Path(__file__).resolve().parents[2] / "contract" / "resiliency-contract.json"


CONTRACT = json.loads(contract_path().read_text(encoding="utf-8"))


def outcome(code: str) -> Outcome:
    return Outcome(status=int(code)) if code.isdigit() else Outcome(error=code)


class ContractTest(unittest.TestCase):
    def test_the_contract_carries_enough_scenarios(self):
        self.assertGreaterEqual(len(CONTRACT["scenarios"]), 20)

    def test_contract_defaults_match_the_sdk_defaults(self):
        self.assertEqual(ResiliencyPolicy.defaults(), ResiliencyPolicy.from_json(CONTRACT["defaults"]))
        self.assertEqual(MAX_BACKOFF_MILLIS, CONTRACT["maxBackoffMillis"])


def make_test(scenario):
    def test(self):
        policy = ResiliencyPolicy.from_json(scenario["policy"])
        clock = [1_000_000]
        sleeps = []
        breaker = CircuitBreaker(policy.circuit_breaker, lambda: clock[0])
        # Le sleeper n'avance PAS l'horloge : seul "advanceMillis" le fait (determinisme inter-langages).
        executor = ResilienceExecutor(policy.retry, breaker, sleeps.append)
        for index, step in enumerate(scenario["steps"], start=1):
            if "advanceMillis" in step:
                clock[0] += step["advanceMillis"]
                continue
            script, expect = step["call"], step["expect"]
            context = f'{scenario["name"]} / etape {index}'
            sleeps.clear()

            def send(attempt, script=script, context=context):
                self.assertLess(attempt, len(script), f"{context} : tentative {attempt} hors script")
                return outcome(script[attempt])

            result = executor.run(send)
            self.assertEqual(expect["result"], result.render(), context)
            self.assertEqual(expect["attemptsMade"], result.attempts_made, context)
            self.assertEqual(expect["sleepsMillis"], sleeps, context)
            self.assertEqual(expect["breaker"], breaker.state, context)
    return test


for _scenario in CONTRACT["scenarios"]:
    _name = "test_" + _scenario["name"].replace("-", "_") + "_" + _scenario["scope"]
    setattr(ContractTest, _name, make_test(_scenario))

if __name__ == "__main__":
    unittest.main()

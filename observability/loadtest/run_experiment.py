#!/usr/bin/env python3
"""Collect k6 + Prometheus numbers for the AI concurrency experiment. Does not call real external APIs."""

from __future__ import annotations

import json
import math
import os
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

VUS_LIST = [1, 2, 4, 8, 16, 32]


def env(name: str, default: str) -> str:
    value = os.environ.get(name)
    return default if value is None or value == "" else value


def fetch(url: str) -> str:
    with urllib.request.urlopen(url, timeout=10) as response:
        return response.read().decode("utf-8")


def parse_prometheus(text: str) -> dict[str, list[tuple[dict[str, str], float]]]:
    parsed: dict[str, list[tuple[dict[str, str], float]]] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        metric, value_s = split_metric_line(line)
        name, labels = parse_metric(metric)
        parsed.setdefault(name, []).append((labels, float(value_s)))
    return parsed


def split_metric_line(line: str) -> tuple[str, str]:
    idx = line.rfind(" ")
    return line[:idx], line[idx + 1 :]


def parse_metric(metric: str) -> tuple[str, dict[str, str]]:
    if "{" not in metric:
        return metric, {}
    name, rest = metric.split("{", 1)
    labels = {}
    body = rest[:-1] if rest.endswith("}") else rest
    for part in body.split(","):
        if "=" not in part:
            continue
        key, raw = part.split("=", 1)
        labels[key.strip()] = raw.strip().strip('"')
    return name, labels


def metric_value(parsed: dict, name: str, **required: str) -> float:
    total = 0.0
    found = False
    for labels, value in parsed.get(name, []):
        if all(labels.get(k) == v for k, v in required.items()):
            total += value
            found = True
    return total if found else 0.0


def bucket_map(parsed: dict, name: str) -> dict[str, float]:
    buckets = {}
    for labels, value in parsed.get(name, []):
        le = labels.get("le")
        if le is None:
            continue
        buckets[le] = buckets.get(le, 0.0) + value
    return buckets


def delta_buckets(after: dict[str, float], before: dict[str, float]) -> dict[str, float]:
    keys = set(after) | set(before)
    return {key: after.get(key, 0.0) - before.get(key, 0.0) for key in keys}


def histogram_quantile(q: float, buckets: dict[str, float]) -> float | None:
    items = []
    for le, count in buckets.items():
        bound = math.inf if le == "+Inf" else float(le)
        items.append((bound, count))
    items.sort(key=lambda item: item[0])
    if not items:
        return None
    total = items[-1][1]
    if total <= 0:
        return None
    rank = q * total
    prev_le = 0.0
    prev_count = 0.0
    for le, count in items:
        if count >= rank:
            if math.isinf(le):
                return prev_le
            if count == prev_count:
                return le
            return prev_le + (le - prev_le) * (rank - prev_count) / (count - prev_count)
        prev_le = le if not math.isinf(le) else prev_le
        prev_count = count
    return prev_le


def k6_values(summary: dict, metric: str) -> dict:
    return summary.get("metrics", {}).get(metric, {}).get("values", {})


def run_k6(script: Path, vus: int, duration: str, base_url: str, summary_path: Path) -> dict:
    summary_path.parent.mkdir(parents=True, exist_ok=True)
    env_vars = os.environ.copy()
    env_vars.update(
        {
            "VUS": str(vus),
            "DURATION": duration,
            "BASE_URL": base_url,
            "SUMMARY_PATH": str(summary_path),
        }
    )
    subprocess.run(
        ["k6", "run", str(script)],
        check=True,
        env=env_vars,
    )
    return json.loads(summary_path.read_text(encoding="utf-8"))


def post(url: str) -> None:
    request = urllib.request.Request(url, method="POST", data=b"")
    with urllib.request.urlopen(request, timeout=10) as response:
        response.read()


def main() -> int:
    base_url = env("BASE_URL", "http://127.0.0.1:18080")
    duration = env("DURATION", "20s")
    root = Path(__file__).resolve().parents[2]
    script = root / "observability/loadtest/k6/ai-concurrency.js"
    results_dir = root / "observability/loadtest/results"
    rows = []

    for vus in VUS_LIST:
        post(f"{base_url}/internal/ai-loadtest/reset")
        before = parse_prometheus(fetch(f"{base_url}/actuator/prometheus"))
        started = time.time()
        summary = run_k6(script, vus, duration, base_url, results_dir / f"k6-vus-{vus}.json")
        elapsed = max(time.time() - started, 0.001)
        after = parse_prometheus(fetch(f"{base_url}/actuator/prometheus"))
        stats = json.loads(fetch(f"{base_url}/internal/ai-loadtest/stats"))

        duration_buckets = delta_buckets(
            bucket_map(after, "ai_provider_request_duration_seconds_bucket"),
            bucket_map(before, "ai_provider_request_duration_seconds_bucket"),
        )
        wait_buckets = delta_buckets(
            bucket_map(after, "ai_concurrency_wait_duration_seconds_bucket"),
            bucket_map(before, "ai_concurrency_wait_duration_seconds_bucket"),
        )
        requests = k6_values(summary, "http_reqs").get("count", 0)
        rps = k6_values(summary, "http_reqs").get("rate", requests / elapsed)
        duration_values = k6_values(summary, "http_req_duration")
        timeouts = k6_values(summary, "acquire_timeouts").get("count", 0)
        over_capacity = k6_values(summary, "provider_over_capacity").get("count", 0)
        successes = requests - timeouts - over_capacity
        error_rate = 0.0 if requests == 0 else (timeouts + over_capacity) / requests
        success_rate = 0.0 if requests == 0 else successes / requests
        acquire_failures = metric_value(
            after, "ai_concurrency_acquire_failures_total", provider="featherless"
        ) - metric_value(before, "ai_concurrency_acquire_failures_total", provider="featherless")

        rows.append(
            {
                "vus": vus,
                "requests": int(requests),
                "rps": rps,
                "success_rate": success_rate,
                "error_rate": error_rate,
                "p50_ms": duration_values.get("p(50)", duration_values.get("med", 0.0)),
                "p95_ms": duration_values.get("p(95)", 0.0),
                "p99_ms": duration_values.get("p(99)", 0.0),
                "provider_p95_ms": (histogram_quantile(0.95, duration_buckets) or 0.0) * 1000.0,
                "wait_p95_ms": (histogram_quantile(0.95, wait_buckets) or 0.0) * 1000.0,
                "acquire_timeouts": int(timeouts),
                "acquire_failures": acquire_failures,
                "max_in_flight": stats.get("max_in_flight", 0),
                "over_capacity": int(over_capacity),
            }
        )
        print(json.dumps(rows[-1], ensure_ascii=False), flush=True)

    results_dir.mkdir(parents=True, exist_ok=True)
    (results_dir / "summary.json").write_text(
        json.dumps(rows, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    print(render_table(rows))
    return 0


def render_table(rows: list[dict]) -> str:
    lines = [
        "| 동시성 | 요청 수 | RPS | 성공률 | 오류율 | p50 | p95 | p99 | Provider p95 | 대기 p95 | 획득 타임아웃 |",
        "| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for row in rows:
        lines.append(
            "| {vus} | {requests} | {rps:.2f} | {success:.1f}% | {error:.1f}% | {p50:.0f}ms | {p95:.0f}ms | {p99:.0f}ms | {pp95:.0f}ms | {wait:.0f}ms | {timeouts} |".format(
                vus=row["vus"],
                requests=row["requests"],
                rps=row["rps"],
                success=row["success_rate"] * 100,
                error=row["error_rate"] * 100,
                p50=row["p50_ms"],
                p95=row["p95_ms"],
                p99=row["p99_ms"],
                pp95=row["provider_p95_ms"],
                wait=row["wait_p95_ms"],
                timeouts=row["acquire_timeouts"],
            )
        )
    return "\n".join(lines)


if __name__ == "__main__":
    sys.exit(main())

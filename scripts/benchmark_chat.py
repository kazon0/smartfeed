#!/usr/bin/env python3
import argparse
import asyncio
import json
import math
import os
import ssl
import statistics
import time
from urllib.parse import quote, urlparse, urlunparse

import requests
import websockets


def request_json(
    base_url: str,
    path: str,
    *,
    token: str | None = None,
    payload: dict | None = None,
    timeout: int = 180,
) -> dict:
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    response = requests.request(
        "POST" if payload is not None else "GET",
        f"{base_url.rstrip('/')}{path}",
        headers=headers,
        json=payload,
        timeout=timeout,
    )
    response.raise_for_status()
    return response.json()


def websocket_url(base_url: str, token: str) -> str:
    parsed = urlparse(base_url)
    scheme = "wss" if parsed.scheme == "https" else "ws"
    return urlunparse((scheme, parsed.netloc, "/ws/chat", "", f"token={quote(token)}", ""))


def login(base_url: str, email: str, password: str, timeout: int) -> str:
    response = request_json(
        base_url,
        "/auth/login",
        payload={"email": email, "password": password},
        timeout=timeout,
    )
    return response["access_token"]


def benchmark_http(
    base_url: str,
    token: str,
    payload: dict,
    timeout: int,
) -> dict:
    started = time.perf_counter()
    response = request_json(
        base_url,
        "/chat",
        token=token,
        payload=payload,
        timeout=timeout,
    )
    elapsed_ms = (time.perf_counter() - started) * 1000
    debug = response.get("debug", {})
    return {
        "total_ms": round(elapsed_ms, 2),
        "server_timings_ms": debug.get("timings_ms", {}),
        "langchain_timings_ms": debug.get("langchain_timings_ms", {}),
        "pipeline": debug.get("rag_pipeline", ""),
        "source_type": response.get("source_type", ""),
        "answer_chars": len(response.get("answer", "")),
    }


async def benchmark_websocket(
    base_url: str,
    token: str,
    payload: dict,
    timeout: int,
) -> dict:
    connect_started = time.perf_counter()
    first_event_ms = None
    first_delta_ms = None
    delta_count = 0
    completed_response = {}
    ssl_context = None
    if base_url.startswith("https://"):
        ssl_context = ssl.create_default_context(cafile=requests.certs.where())

    async with websockets.connect(
        websocket_url(base_url, token),
        open_timeout=timeout,
        close_timeout=10,
        ssl=ssl_context,
        proxy=None,
    ) as websocket:
        while True:
            event = json.loads(await asyncio.wait_for(websocket.recv(), timeout=timeout))
            if event.get("type") == "status" and event.get("stage") == "authenticated":
                break

        connected_ms = (time.perf_counter() - connect_started) * 1000
        request_started = time.perf_counter()
        await websocket.send(json.dumps(payload, ensure_ascii=False))

        while True:
            event = json.loads(await asyncio.wait_for(websocket.recv(), timeout=timeout))
            if first_event_ms is None:
                first_event_ms = (time.perf_counter() - request_started) * 1000
            event_type = event.get("type")
            if event_type == "delta":
                delta_count += 1
                if first_delta_ms is None:
                    first_delta_ms = (time.perf_counter() - request_started) * 1000
            elif event_type == "completed":
                completed_response = event.get("response", {})
                break
            elif event_type == "error":
                raise RuntimeError(event.get("message", "WebSocket benchmark failed."))

        total_ms = (time.perf_counter() - request_started) * 1000

    debug = completed_response.get("debug", {})
    return {
        "connect_ms": round(connected_ms, 2),
        "first_event_ms": round(first_event_ms, 2) if first_event_ms is not None else None,
        "ttft_ms": round(first_delta_ms, 2) if first_delta_ms is not None else None,
        "total_ms": round(total_ms, 2),
        "delta_count": delta_count,
        "server_timings_ms": debug.get("timings_ms", {}),
        "langchain_timings_ms": debug.get("langchain_timings_ms", {}),
        "pipeline": debug.get("rag_pipeline", ""),
        "source_type": completed_response.get("source_type", ""),
        "answer_chars": len(completed_response.get("answer", "")),
    }


def metric_summary(values: list[float | None]) -> dict:
    clean_values = sorted(value for value in values if value is not None)
    if not clean_values:
        return {"count": 0, "median": None, "p95": None, "min": None, "max": None}
    p95_index = max(0, min(len(clean_values) - 1, math.ceil(len(clean_values) * 0.95) - 1))
    return {
        "count": len(clean_values),
        "median": round(statistics.median(clean_values), 2),
        "p95": round(clean_values[p95_index], 2),
        "min": round(clean_values[0], 2),
        "max": round(clean_values[-1], 2),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Benchmark SmartFeed HTTP and WebSocket chat.")
    parser.add_argument(
        "--base-url",
        default="https://lxfxyunzhlxi.sealoshzh.site",
    )
    parser.add_argument("--email", default=os.getenv("SMARTFEED_BENCH_EMAIL", ""))
    parser.add_argument("--password", default=os.getenv("SMARTFEED_BENCH_PASSWORD", ""))
    parser.add_argument("--query", default="根据我的知识库解释 Kotlin Flow")
    parser.add_argument("--url", default="")
    parser.add_argument("--runs", type=int, default=1)
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--output", default="")
    return parser.parse_args()


async def run_benchmark(args: argparse.Namespace) -> dict:
    if not args.email or not args.password:
        raise SystemExit(
            "Set SMARTFEED_BENCH_EMAIL and SMARTFEED_BENCH_PASSWORD, "
            "or pass --email and --password."
        )
    if args.runs < 1:
        raise SystemExit("--runs must be at least 1.")

    token = login(args.base_url, args.email, args.password, args.timeout)
    stats = request_json(args.base_url, "/stats", token=token, timeout=args.timeout)
    payload = {
        "query": args.query,
        "mode": "page" if args.url else "global",
        "url": args.url or None,
        "history": [],
    }
    http_runs = []
    websocket_runs = []
    for _ in range(args.runs):
        http_runs.append(benchmark_http(args.base_url, token, payload, args.timeout))
        websocket_runs.append(
            await benchmark_websocket(args.base_url, token, payload, args.timeout)
        )

    return {
        "base_url": args.base_url,
        "query": args.query,
        "runs": args.runs,
        "knowledge_base": {
            "total_articles": stats.get("total_articles", 0),
            "total_chunks": stats.get("total_chunks", 0),
        },
        "summary": {
            "http_total_ms": metric_summary([run["total_ms"] for run in http_runs]),
            "websocket_connect_ms": metric_summary(
                [run["connect_ms"] for run in websocket_runs]
            ),
            "websocket_first_event_ms": metric_summary(
                [run["first_event_ms"] for run in websocket_runs]
            ),
            "websocket_ttft_ms": metric_summary([run["ttft_ms"] for run in websocket_runs]),
            "websocket_total_ms": metric_summary([run["total_ms"] for run in websocket_runs]),
        },
        "http_runs": http_runs,
        "websocket_runs": websocket_runs,
    }


def main() -> int:
    args = parse_args()
    report = asyncio.run(run_benchmark(args))
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        with open(args.output, "w", encoding="utf-8") as output_file:
            output_file.write(rendered + "\n")
    print(rendered)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
import argparse
import json
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


ALGORITHM_URL = "https://cloud.tencent.com/developer/article/2352039"
HEALTH_URL = "https://news.99.com.cn/minsheng/20260605/2386221.htm"
UNKNOWN_URL = "https://example.com/not-uploaded-for-smartfeed-eval"


@dataclass
class EvalCase:
    name: str
    payload: dict
    expected_source_type: str
    answer_keywords: tuple[str, ...] = ()
    source_url: str | None = None
    forbidden_source_preview_terms: tuple[str, ...] = ()


CASES = [
    EvalCase(
        name="current page list question uses clean algorithm page sources",
        payload={"query": "十大算法是什么", "url": ALGORITHM_URL},
        expected_source_type="page",
        answer_keywords=("冒泡排序", "快速排序", "二分查找", "最长公共子序列"),
        source_url=ALGORITHM_URL,
        forbidden_source_preview_terms=("作者相关精选", "相关推荐", "推荐文章", "原创声明"),
    ),
    EvalCase(
        name="specific current page question stays on current URL",
        payload={"query": "二分查找怎么理解", "url": ALGORITHM_URL},
        expected_source_type="page",
        answer_keywords=("二分查找",),
        source_url=ALGORITHM_URL,
        forbidden_source_preview_terms=("作者相关精选", "相关推荐", "推荐文章"),
    ),
    EvalCase(
        name="global health query finds saved mushroom poisoning article",
        payload={"query": "吃菌子中毒会怎么样"},
        expected_source_type="knowledge_base",
        answer_keywords=("中毒",),
        source_url=HEALTH_URL,
    ),
    EvalCase(
        name="article reference without URL asks for page context",
        payload={"query": "这篇文章讲了什么"},
        expected_source_type="need_page_context",
        answer_keywords=("无法确定",),
    ),
    EvalCase(
        name="unknown current page does not answer from unrelated knowledge",
        payload={"query": "这篇文章讲了什么", "url": UNKNOWN_URL},
        expected_source_type="page_not_found_with_suggestions",
        answer_keywords=("当前网页没有找到",),
    ),
    EvalCase(
        name="realtime query without knowledge does not guess",
        payload={"query": "今天星期几"},
        expected_source_type="unsupported_realtime",
        answer_keywords=("实时信息",),
    ),
]


def post_json(base_url: str, path: str, payload: dict, timeout: int) -> dict:
    request = Request(
        f"{base_url.rstrip('/')}{path}",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def upload_url(base_url: str, url: str, timeout: int) -> dict:
    return post_json(base_url, "/upload", {"url": url}, timeout)


def chat(base_url: str, payload: dict, timeout: int) -> dict:
    return post_json(base_url, "/chat", payload, timeout)


def check_case(case: EvalCase, response: dict) -> list[str]:
    failures = []
    source_type = response.get("source_type")
    answer = response.get("answer", "") or ""
    sources = response.get("sources", []) or []

    if source_type != case.expected_source_type:
        failures.append(
            f"expected source_type={case.expected_source_type}, got {source_type}"
        )

    for keyword in case.answer_keywords:
        if keyword not in answer:
            failures.append(f"answer missing keyword: {keyword}")

    if case.source_url:
        if not sources:
            failures.append("expected non-empty sources")
        wrong_urls = [
            source.get("url", "")
            for source in sources
            if source.get("url", "") != case.source_url
        ]
        if wrong_urls:
            failures.append(f"sources include unexpected URLs: {wrong_urls}")

    previews = "\n".join(source.get("content_preview", "") for source in sources)
    for term in case.forbidden_source_preview_terms:
        if term in previews:
            failures.append(f"source preview contains noise term: {term}")

    return failures


def source_summary(response: dict) -> str:
    sources = response.get("sources", []) or []
    if not sources:
        return "none"
    return ", ".join(
        f"{source.get('display_title') or source.get('title') or source.get('url')}#{source.get('chunk_index')}"
        for source in sources[:3]
    )


def render_report(results: list[dict], uploads: list[dict], base_url: str) -> str:
    lines = [
        "# SmartFeed RAG Real Eval Report",
        "",
        f"- Time: {datetime.now().isoformat(timespec='seconds')}",
        f"- Base URL: `{base_url}`",
        "",
        "## Uploads",
        "",
    ]

    if uploads:
        for upload in uploads:
            status = upload.get("status", "unknown")
            stored_chunks = upload.get("stored_chunks", "unknown")
            url = upload.get("url", "")
            error = upload.get("error", "")
            suffix = f" error={error}" if error else ""
            lines.append(f"- `{status}` chunks={stored_chunks} url={url}{suffix}")
    else:
        lines.append("- skipped")

    lines.extend(["", "## Cases", ""])
    for result in results:
        mark = "PASS" if result["passed"] else "FAIL"
        response = result["response"]
        lines.extend(
            [
                f"### {mark}: {result['name']}",
                "",
                f"- source_type: `{response.get('source_type')}`",
                f"- status: `{response.get('status')}`",
                f"- sources: {source_summary(response)}",
                f"- answer preview: {response.get('answer', '')[:180]}",
            ]
        )
        if result["failures"]:
            lines.append("- failures:")
            for failure in result["failures"]:
                lines.append(f"  - {failure}")
        lines.append("")

    passed = sum(1 for result in results if result["passed"])
    lines.extend(
        [
            "## Summary",
            "",
            f"- Passed: {passed}/{len(results)}",
        ]
    )
    return "\n".join(lines).strip() + "\n"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run SmartFeed real RAG evaluation cases.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8000")
    parser.add_argument("--timeout", type=int, default=120)
    parser.add_argument("--skip-upload", action="store_true")
    parser.add_argument("--output", default="")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    uploads = []

    try:
        if not args.skip_upload:
            for url in (ALGORITHM_URL, HEALTH_URL):
                started = time.time()
                response = upload_url(args.base_url, url, args.timeout)
                uploads.append(
                    {
                        "url": url,
                        "status": response.get("status", ""),
                        "stored_chunks": response.get("stored_chunks", 0),
                        "error": response.get("error", "") or response.get("data", {}).get("error", ""),
                        "seconds": round(time.time() - started, 2),
                    }
                )

        results = []
        for case in CASES:
            response = chat(args.base_url, case.payload, args.timeout)
            failures = check_case(case, response)
            results.append(
                {
                    "name": case.name,
                    "passed": not failures,
                    "failures": failures,
                    "response": response,
                }
            )

        report = render_report(results, uploads, args.base_url)
        if args.output:
            with open(args.output, "w", encoding="utf-8") as file:
                file.write(report)
        else:
            print(report)

        return 0 if all(result["passed"] for result in results) else 1

    except HTTPError as error:
        print(f"HTTP error: {error.code} {error.reason}", file=sys.stderr)
        return 2
    except URLError as error:
        print(f"Cannot connect to backend: {error.reason}", file=sys.stderr)
        return 2
    except TimeoutError:
        print("Request timed out.", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())

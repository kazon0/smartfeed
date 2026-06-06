import re

import requests
from bs4 import BeautifulSoup


class WebParserService:
    CHUNK_SIZE = 500
    CHUNK_OVERLAP = 50
    JINA_READER_PREFIX = "https://r.jina.ai/"
    JINA_METADATA_PREFIXES = (
        "Title:",
        "URL Source:",
        "Published Time:",
        "Markdown Content:",
    )
    NOISE_KEYWORDS = (
        "nav",
        "footer",
        "header",
        "sidebar",
        "related",
        "recommend",
        "hot",
        "comment",
        "share",
        "ad",
        "tool",
        "login",
    )
    CONTENT_SELECTORS = (
        "article",
        "main",
        "div[class*='content']",
        "div[class*='article']",
        "div[class*='detail']",
        "div[id*='content']",
        "div[id*='article']",
    )

    def __init__(self) -> None:
        self.session = requests.Session()
        self.session.headers.update(
            {
                "User-Agent": (
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    "AppleWebKit/537.36 (KHTML, like Gecko) "
                    "Chrome/125.0 Safari/537.36"
                ),
                "Accept": (
                    "text/html,application/xhtml+xml,application/xml;"
                    "q=0.9,image/avif,image/webp,*/*;q=0.8"
                ),
                "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
            }
        )

    def prepare(self, url: str) -> dict:
        jina_result = self._prepare_with_jina(url)
        if jina_result and jina_result["chunks"]:
            return jina_result

        return self._prepare_with_html(url)

    def _prepare_with_jina(self, url: str) -> dict | None:
        try:
            response = self.session.get(self._jina_url(url), timeout=20)
            response.encoding = response.apparent_encoding or "utf-8"

            if response.status_code >= 400:
                return None

            title, raw_content = self._parse_jina_text(response.text)
            sections = self._extract_markdown_sections(raw_content, title)
            content = self._sections_to_text(sections)
            chunks, chunk_metadata = self._chunk_sections(sections)

            return {
                "url": url,
                "title": title,
                "content": content,
                "chunks": chunks,
                "sections": sections,
                "chunk_metadata": chunk_metadata,
                "metadata": {
                    "source": "web",
                    "parser": "jina",
                    "length": len(content),
                },
            }
        except Exception:
            return None

    def _prepare_with_html(self, url: str) -> dict:
        try:
            response = self.session.get(url, timeout=10)
            response.encoding = response.apparent_encoding or "utf-8"

            if response.status_code >= 400:
                return {
                    "error": f"Failed to fetch page: HTTP {response.status_code}",
                    "url": url,
                }

            soup = BeautifulSoup(response.text, "html.parser")

            title = self._extract_title(soup)
            sections = self._extract_html_sections(soup, title)
            content = self._sections_to_text(sections)
            chunks, chunk_metadata = self._chunk_sections(sections)

            return {
                "url": url,
                "title": title,
                "content": content,
                "chunks": chunks,
                "sections": sections,
                "chunk_metadata": chunk_metadata,
                "metadata": {
                    "source": "web",
                    "parser": "html_fallback",
                    "length": len(content),
                },
            }
        except requests.RequestException as exc:
            return {
                "error": f"Request failed: {exc}",
                "url": url,
            }
        except Exception as exc:
            return {
                "error": f"Parse failed: {exc}",
                "url": url,
            }

    def _jina_url(self, url: str) -> str:
        if url.startswith(self.JINA_READER_PREFIX):
            return url
        return f"{self.JINA_READER_PREFIX}{url}"

    def _parse_jina_text(self, text: str) -> tuple[str, str]:
        title = ""
        content_lines = []
        in_content = False

        for raw_line in text.splitlines():
            line = raw_line.strip()

            if line.startswith("Title:"):
                title = line.removeprefix("Title:").strip()
                continue

            if line.startswith("Markdown Content:"):
                in_content = True
                continue

            if not in_content and line.startswith("#") and not title:
                title = line.lstrip("#").strip()

            if in_content:
                content_lines.append(raw_line)

        if not content_lines:
            content_lines = [
                line
                for line in text.splitlines()
                if not line.strip().startswith(self.JINA_METADATA_PREFIXES)
            ]

        return title, "\n".join(content_lines)

    def _clean_markdown_text(self, text: str) -> str:
        text = re.sub(r"!\[[^\]]*]\([^)]*\)", "", text)
        text = re.sub(r"\[([^\]]+)]\([^)]*\)", r"\1", text)
        text = re.sub(r"^#{1,6}\s*", "", text, flags=re.MULTILINE)
        text = re.sub(r"[*_`>]+", "", text)
        lines = self._clean_lines(text)
        return "\n".join(lines)

    def _extract_markdown_sections(self, text: str, title: str) -> list[dict]:
        sections = []
        current_title = title or "正文"
        current_lines = []

        for raw_line in text.splitlines():
            line = raw_line.strip()
            if not line:
                continue

            heading_match = re.match(r"^(#{1,6})\s+(.+)$", line)
            if heading_match:
                self._append_section(sections, current_title, current_lines)
                current_title = self._clean_markdown_inline(heading_match.group(2))
                current_lines = []
                continue

            cleaned_line = self._clean_markdown_inline(line)
            if cleaned_line:
                current_lines.append(cleaned_line)

        self._append_section(sections, current_title, current_lines)
        return self._normalize_sections(sections, title)

    def _clean_markdown_inline(self, line: str) -> str:
        line = re.sub(r"!\[[^\]]*]\([^)]*\)", "", line)
        line = re.sub(r"\[([^\]]+)]\([^)]*\)", r"\1", line)
        line = re.sub(r"^[-*+]\s+", "", line)
        line = re.sub(r"^\d+[.)]\s+", "", line)
        line = re.sub(r"[*_>]+", "", line)
        return line.strip()

    def _extract_title(self, soup: BeautifulSoup) -> str:
        if soup.title and soup.title.string:
            return soup.title.string.strip()
        return ""

    def _extract_html_sections(self, soup: BeautifulSoup, title: str) -> list[dict]:
        for tag in soup(["script", "style", "noscript", "nav", "header", "footer", "aside", "form", "iframe"]):
            tag.decompose()

        for tag in soup.find_all(self._is_noise_node):
            tag.decompose()

        content_root = self._find_content_root(soup)
        sections = []
        current_title = title or "正文"
        current_lines = []

        for node in content_root.find_all(["h1", "h2", "h3", "h4", "p", "li", "pre", "code"], recursive=True):
            text = node.get_text(separator="\n", strip=True)
            if not text:
                continue

            if node.name in ("h1", "h2", "h3", "h4"):
                self._append_section(sections, current_title, current_lines)
                current_title = text.strip()
                current_lines = []
            else:
                current_lines.extend(text.splitlines())

        self._append_section(sections, current_title, current_lines)

        if not sections:
            text = content_root.get_text(separator="\n", strip=True)
            sections = [
                {
                    "index": 0,
                    "title": title or "正文",
                    "content": "\n".join(self._clean_lines(text)),
                }
            ]

        return self._normalize_sections(sections, title)

    def _extract_content(self, soup: BeautifulSoup) -> str:
        for tag in soup(["script", "style", "noscript", "nav", "header", "footer", "aside", "form", "iframe"]):
            tag.decompose()

        for tag in soup.find_all(self._is_noise_node):
            tag.decompose()

        content_root = self._find_content_root(soup)
        text = content_root.get_text(separator="\n", strip=True)

        lines = self._clean_lines(text)
        return "\n".join(lines)

    def _append_section(self, sections: list[dict], title: str, lines: list[str]) -> None:
        content_lines = self._clean_lines("\n".join(lines))
        content = "\n".join(content_lines)
        if not content:
            return

        sections.append(
            {
                "index": len(sections),
                "title": title.strip() or "正文",
                "content": content,
            }
        )

    def _normalize_sections(self, sections: list[dict], fallback_title: str) -> list[dict]:
        normalized = []
        for section in sections:
            content = section.get("content", "").strip()
            if not content:
                continue
            normalized.append(
                {
                    "index": len(normalized),
                    "title": section.get("title", "").strip() or fallback_title or "正文",
                    "content": content,
                }
            )
        return normalized

    def _sections_to_text(self, sections: list[dict]) -> str:
        parts = []
        for section in sections:
            title = section.get("title", "").strip()
            content = section.get("content", "").strip()
            if title:
                parts.append(title)
            if content:
                parts.append(content)
        return "\n".join(parts)

    def _chunk_sections(self, sections: list[dict]) -> tuple[list[str], list[dict]]:
        chunks = []
        chunk_metadata = []

        for section in sections:
            section_text = section.get("content", "")
            section_chunks = self._chunk_text(section_text)
            for section_chunk_index, chunk in enumerate(section_chunks):
                chunks.append(chunk)
                chunk_metadata.append(
                    {
                        "section_index": section.get("index", 0),
                        "section_title": section.get("title", ""),
                        "section_chunk_index": section_chunk_index,
                    }
                )

        return chunks, chunk_metadata

    def _chunk_text(self, text: str) -> list[str]:
        if not text:
            return []

        chunks = []
        start = 0
        step = self.CHUNK_SIZE - self.CHUNK_OVERLAP

        while start < len(text):
            end = start + self.CHUNK_SIZE
            chunk = text[start:end].strip()
            if chunk and not self._is_noise_chunk(chunk):
                chunks.append(chunk)
            start += step

        return chunks

    def _find_content_root(self, soup: BeautifulSoup):
        candidates = []
        for selector in self.CONTENT_SELECTORS:
            candidates.extend(soup.select(selector))

        if not candidates:
            return soup.body or soup

        return max(candidates, key=lambda node: len(node.get_text(strip=True)))

    def _is_noise_node(self, tag) -> bool:
        values = []
        node_id = tag.get("id")
        node_class = tag.get("class")

        if node_id:
            values.append(str(node_id).lower())
        if node_class:
            values.extend(str(value).lower() for value in node_class)

        value_text = " ".join(values)
        return any(
            self._matches_noise_keyword(value_text, keyword)
            for keyword in self.NOISE_KEYWORDS
        )

    def _matches_noise_keyword(self, value_text: str, keyword: str) -> bool:
        if keyword == "ad":
            return bool(
                re.search(
                    r"(^|[-_\s])(ad|ads|advert|advertisement)([-_\s]|$)",
                    value_text,
                )
            )
        return keyword in value_text

    def _clean_lines(self, text: str) -> list[str]:
        lines = []
        seen = set()

        for raw_line in text.splitlines():
            line = raw_line.strip()
            if not line or line in seen or self._is_noise_line(line):
                continue

            seen.add(line)
            lines.append(line)

        return lines

    def _is_noise_line(self, line: str) -> bool:
        lowered = line.lower()
        if len(line) <= 1:
            return True

        if any(
            keyword in line
            for keyword in ("ICP备", "公网安备", "版权", "版权所有", "免责声明")
        ):
            return True

        if any(
            keyword in lowered
            for keyword in (
                "copyright",
                "hotsearch",
                "heat_score",
                "linkurl",
                "function",
                "display:",
                "color:",
                "window.",
                "document.",
            )
        ):
            return True

        if re.search(r"[{};]{3,}", line):
            return True

        if re.search(r"\b(var|let|const)\s+\w+\s*=", lowered):
            return True

        if re.search(r"^\s*[\[{].*[\]}]\s*$", line) and len(line) > 40:
            return True

        if line.count("http") >= 3:
            return True

        nav_words = ("首页", "登录", "注册", "分享", "评论", "相关阅读", "相关推荐")
        if len(line) < 30 and any(word in line for word in nav_words):
            return True

        return False

    def _is_noise_chunk(self, chunk: str) -> bool:
        lowered = chunk.lower()
        noise_tokens = (
            "{",
            "}",
            "function",
            "var ",
            "display:",
            "color:",
            "px",
            "linkurl",
            "hotsearch",
            "heat_score",
            "ICP备",
            "公网安备",
            "Copyright",
        )
        hit_count = sum(1 for token in noise_tokens if token in chunk or token in lowered)

        if hit_count >= 3:
            return True

        symbol_count = sum(chunk.count(symbol) for symbol in ("{", "}", ";"))
        return symbol_count > max(8, len(chunk) // 30)

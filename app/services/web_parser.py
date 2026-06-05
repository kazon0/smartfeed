import requests
from bs4 import BeautifulSoup


class WebParserService:
    CHUNK_SIZE = 500
    CHUNK_OVERLAP = 50

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
            content = self._extract_content(soup)
            chunks = self._chunk_text(content)

            return {
                "url": url,
                "title": title,
                "content": content,
                "chunks": chunks,
                "metadata": {
                    "source": "web",
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

    def _extract_title(self, soup: BeautifulSoup) -> str:
        if soup.title and soup.title.string:
            return soup.title.string.strip()
        return ""

    def _extract_content(self, soup: BeautifulSoup) -> str:
        for tag in soup(["script", "style", "noscript"]):
            tag.decompose()

        article = soup.find("article")
        content_root = article if article else soup.body or soup
        text = content_root.get_text(separator="\n", strip=True)

        lines = [line.strip() for line in text.splitlines() if line.strip()]
        return "\n".join(lines)

    def _chunk_text(self, text: str) -> list[str]:
        if not text:
            return []

        chunks = []
        start = 0
        step = self.CHUNK_SIZE - self.CHUNK_OVERLAP

        while start < len(text):
            end = start + self.CHUNK_SIZE
            chunks.append(text[start:end])
            start += step

        return chunks

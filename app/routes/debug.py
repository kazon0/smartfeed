from fastapi import APIRouter
from fastapi.responses import HTMLResponse

router = APIRouter()


@router.get("/debug", response_class=HTMLResponse)
def debug_page():
    return """
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>SmartFeed Debug</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 0; background: #f6f7f9; color: #1f2937; }
    main { max-width: 1080px; margin: 0 auto; padding: 24px; }
    h1 { font-size: 24px; margin: 0 0 20px; }
    section { background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
    label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 6px; }
    input, textarea { width: 100%; box-sizing: border-box; border: 1px solid #d1d5db; border-radius: 6px; padding: 10px; font: inherit; }
    textarea { min-height: 84px; resize: vertical; }
    button { border: 0; border-radius: 6px; padding: 10px 14px; background: #111827; color: #fff; font-weight: 600; cursor: pointer; }
    button:disabled { opacity: 0.55; cursor: not-allowed; }
    .row { display: grid; grid-template-columns: 1fr auto; gap: 12px; align-items: end; }
    .meta { display: flex; flex-wrap: wrap; gap: 8px; margin: 12px 0; }
    .pill { background: #eef2ff; color: #3730a3; border-radius: 999px; padding: 4px 8px; font-size: 12px; }
    pre { white-space: pre-wrap; word-break: break-word; background: #111827; color: #f9fafb; border-radius: 8px; padding: 12px; max-height: 360px; overflow: auto; }
    details { border-top: 1px solid #e5e7eb; padding: 10px 0; }
    summary { cursor: pointer; font-weight: 600; }
    .muted { color: #6b7280; font-size: 13px; }
  </style>
</head>
<body>
<main>
  <h1>SmartFeed Debug</h1>

  <section>
    <h2>Upload / Parse</h2>
    <div class="row">
      <div>
        <label for="url">URL</label>
        <input id="url" placeholder="https://example.com">
      </div>
      <button id="uploadButton" onclick="upload()">Upload</button>
    </div>
    <div id="uploadResult"></div>
  </section>

  <section>
    <h2>Chat</h2>
    <label for="query">Query</label>
    <textarea id="query" placeholder="Ask a question"></textarea>
    <p class="muted">If URL is filled above, chat will prefer that page. Leave URL empty for global knowledge base search.</p>
    <button id="chatButton" onclick="chat()">Chat</button>
    <div id="chatResult"></div>
  </section>
</main>

<script>
let lastUpload = null;

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function pill(label, value) {
  return `<span class="pill">${escapeHtml(label)}: ${escapeHtml(value)}</span>`;
}

function renderChunks(chunks) {
  if (!chunks || chunks.length === 0) return "<p class='muted'>No chunks.</p>";
  return chunks.map((chunk, index) => `
    <details>
      <summary>Chunk ${index + 1}</summary>
      <pre>${escapeHtml(typeof chunk === "string" ? chunk : chunk.content)}</pre>
    </details>
  `).join("");
}

function renderSources(sources) {
  if (!sources || sources.length === 0) return "<p class='muted'>No sources.</p>";
  return sources.map((source, index) => `
    <details>
      <summary>Source ${index + 1} | score ${escapeHtml(source.score)}</summary>
      <div class="meta">
        ${pill("title", source.title)}
        ${pill("url", source.url)}
        ${pill("chunk", source.chunk_index)}
      </div>
      <pre>${escapeHtml(source.content)}</pre>
    </details>
  `).join("");
}

async function upload() {
  const button = document.getElementById("uploadButton");
  const result = document.getElementById("uploadResult");
  const url = document.getElementById("url").value.trim();
  if (!url) return;

  button.disabled = true;
  result.innerHTML = "<p class='muted'>Uploading...</p>";

  try {
    const response = await fetch("/upload", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ url })
    });
    const data = await response.json();
    lastUpload = data;

    const parsed = data.data || {};
    const metadata = parsed.metadata || {};
    result.innerHTML = `
      <div class="meta">
        ${pill("status", data.status)}
        ${pill("parser", metadata.parser || "n/a")}
        ${pill("stored_chunks", data.stored_chunks ?? 0)}
        ${pill("length", metadata.length ?? 0)}
      </div>
      <h3>${escapeHtml(parsed.title || "No title")}</h3>
      <h4>Summary</h4>
      <pre>${escapeHtml(data.summary || data.error || "")}</pre>
      <h4>Chunks</h4>
      ${renderChunks(parsed.chunks)}
    `;
  } catch (error) {
    result.innerHTML = `<pre>${escapeHtml(error)}</pre>`;
  } finally {
    button.disabled = false;
  }
}

async function chat() {
  const button = document.getElementById("chatButton");
  const result = document.getElementById("chatResult");
  const query = document.getElementById("query").value.trim();
  const url = document.getElementById("url").value.trim();
  if (!query) return;

  button.disabled = true;
  result.innerHTML = "<p class='muted'>Asking...</p>";

  try {
    const payload = url ? { query, url } : { query };
    const response = await fetch("/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    const data = await response.json();

    result.innerHTML = `
      <div class="meta">
        ${pill("source_type", data.source_type)}
        ${pill("sources", (data.sources || []).length)}
      </div>
      <h4>Answer</h4>
      <pre>${escapeHtml(data.answer)}</pre>
      <h4>Sources</h4>
      ${renderSources(data.sources)}
    `;
  } catch (error) {
    result.innerHTML = `<pre>${escapeHtml(error)}</pre>`;
  } finally {
    button.disabled = false;
  }
}
</script>
</body>
</html>
"""

from fastapi import FastAPI

from app.routes.articles import router as articles_router
from app.routes.auth import router as auth_router
from app.routes.chat import router as chat_router
from app.routes.conversations import router as conversations_router
from app.routes.debug import router as debug_router
from app.routes.insights import router as insights_router
from app.routes.search import router as search_router
from app.routes.stats import router as stats_router
from app.routes.upload import router as upload_router

app = FastAPI()

app.include_router(articles_router)
app.include_router(auth_router)
app.include_router(chat_router)
app.include_router(conversations_router)
app.include_router(debug_router)
app.include_router(insights_router)
app.include_router(search_router)
app.include_router(stats_router)
app.include_router(upload_router)


@app.get("/")
def read_root():
    return {"status": "ok"}

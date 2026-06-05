from fastapi import FastAPI

from app.routes.chat import router as chat_router
from app.routes.debug import router as debug_router
from app.routes.search import router as search_router
from app.routes.upload import router as upload_router

app = FastAPI()

app.include_router(chat_router)
app.include_router(debug_router)
app.include_router(search_router)
app.include_router(upload_router)


@app.get("/")
def read_root():
    return {"status": "ok"}

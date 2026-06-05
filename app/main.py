from fastapi import FastAPI

from app.routes.search import router as search_router
from app.routes.upload import router as upload_router

app = FastAPI()

app.include_router(search_router)
app.include_router(upload_router)


@app.get("/")
def read_root():
    return {"status": "ok"}

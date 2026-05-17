from fastapi import FastAPI, APIRouter
from .routers import classify
app = FastAPI()


app.include_router(classify.router, prefix="/classifier",tags=["classifier"])

@app.get("/")
def root():
    return {"status":"API status OK"}




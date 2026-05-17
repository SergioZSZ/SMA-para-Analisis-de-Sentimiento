from fastapi import APIRouter, HTTPException, status
from app.schemas import ClassifierInput, ClassifierOutput, Tipo
from app.services import classify_sentiment


router = APIRouter()



@router.post("/classify",response_model= ClassifierOutput, status_code = status.HTTP_200_OK)
async def classify(comment: ClassifierInput):
    tipo: Tipo
    score: float
    
    tipo, score = classify_sentiment(comment.text)
    
    response = ClassifierOutput(tipo = tipo, score = score)
    return response
    
from pydantic import BaseModel
from enum import Enum
class Tipo(str, Enum):
    POS = "POS"
    NEG = "NEG"
    NEU = "NEU"
    


class ClassifierInput(BaseModel):
    text: str 
    
    
class ClassifierOutput(BaseModel):
    tipo : Tipo
    score: float
    
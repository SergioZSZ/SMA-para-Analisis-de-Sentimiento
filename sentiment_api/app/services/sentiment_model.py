from functools import lru_cache
from pysentimiento import create_analyzer
from app.schemas import Tipo

# etiquetas robertuito
LABEL_MAPPING = {
    "POS": Tipo.POS,
    "NEG": Tipo.NEG,
    "NEU": Tipo.NEU
}

# carga el modelo una sola vez y lo reutiliza en cada petición.
@lru_cache(maxsize=1)   
def get_analyzer():
    return create_analyzer(task="sentiment", lang="es")

def classify_sentiment(text: str)-> tuple[Tipo, float]:
    analyzer = get_analyzer()
    result = analyzer.predict(text)
    
    tipo = LABEL_MAPPING.get(result.output)
    score = result.probas[result.output]
            
    return tipo, score
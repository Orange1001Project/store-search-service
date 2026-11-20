"""임베딩 추론 서버(IR-10, docs/ARCHITECTURE.md ③).

POST /embed  문장 목록(검색 1개, 갱신 때 최대 64개) → 1024차원 float32 벡터(L2 정규화), 모델 버전
GET  /health 모델이 올라왔는지, 모델 버전

모델은 저장본(fp16)을 CPU에서 fp32로 올린다(명시하지 않으면 저장된 fp16으로 올라감, LOCAL_DEV_SETUP 5절).
"""

from __future__ import annotations

import json
import logging
import os
import time
from contextlib import asynccontextmanager
from pathlib import Path

import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer

MODEL_DIR = Path(os.environ.get("MODEL_DIR", "/models/model"))
MAX_BATCH = 64          # IR-10: 갱신 때 최대 64개 묶음
MAX_TEXT_LENGTH = 300   # store_snapshot.doc_text varchar(300), 검색어는 50자
MAX_SEQ_LENGTH = 128    # 학습·평가 설정(model_manifest.json serving.max_seq_length)
DIMENSION = 1024

log = logging.getLogger("embedding")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")


def model_version(model_dir: Path) -> str:
    """model_manifest.json의 tag(예: bge_m3_ft_mj_20261014_0743). 없으면 폴더 이름."""
    manifest = model_dir / "model_manifest.json"
    if manifest.exists():
        tag = json.loads(manifest.read_text(encoding="utf-8")).get("tag")
        if tag:
            return tag
    return os.environ.get("MODEL_VERSION") or model_dir.name


class State:
    model: SentenceTransformer | None = None
    version: str = ""


state = State()


def load_model() -> None:
    if not (MODEL_DIR / "modules.json").exists():
        raise RuntimeError(f"모델 폴더가 없거나 비었어요: {MODEL_DIR} (infra/.env의 EMBEDDING_MODEL_DIR 확인)")
    threads = int(os.environ.get("TORCH_THREADS", "0"))
    if threads > 0:
        torch.set_num_threads(threads)
    started = time.perf_counter()
    model = SentenceTransformer(str(MODEL_DIR), device="cpu", model_kwargs={"dtype": torch.float32})
    model.max_seq_length = MAX_SEQ_LENGTH
    model.eval()
    if model.get_sentence_embedding_dimension() != DIMENSION:
        raise RuntimeError(f"벡터 차원이 {DIMENSION}이 아니에요: {model.get_sentence_embedding_dimension()}")
    model.encode(["예열"], normalize_embeddings=True)   # 첫 요청이 느리지 않게
    state.model = model
    state.version = model_version(MODEL_DIR)
    log.info("모델 %s 올림 (%.1fs, dtype=%s, threads=%d)", state.version, time.perf_counter() - started,
             next(model.parameters()).dtype, torch.get_num_threads())


@asynccontextmanager
async def lifespan(_: FastAPI):
    load_model()
    yield


app = FastAPI(title="storesearch embedding", lifespan=lifespan)


class EmbedRequest(BaseModel):
    texts: list[str] = Field(min_length=1, max_length=MAX_BATCH)


class EmbedResponse(BaseModel):
    model: str
    dimension: int
    vectors: list[list[float]]


@app.post("/embed", response_model=EmbedResponse)
def embed(request: EmbedRequest) -> EmbedResponse:
    for i, text in enumerate(request.texts):
        if not text.strip():
            raise HTTPException(status_code=422, detail=f"texts[{i}]가 비었어요")
        if len(text) > MAX_TEXT_LENGTH:
            raise HTTPException(status_code=422, detail=f"texts[{i}]가 {MAX_TEXT_LENGTH}자를 넘어요")
    if state.model is None:
        raise HTTPException(status_code=503, detail="모델을 올리는 중이에요")
    with torch.inference_mode():
        vectors = state.model.encode(request.texts, batch_size=MAX_BATCH, normalize_embeddings=True,
                                     convert_to_numpy=True).astype("float32")
    return EmbedResponse(model=state.version, dimension=DIMENSION, vectors=vectors.tolist())


@app.get("/health")
def health() -> dict:
    if state.model is None:
        raise HTTPException(status_code=503, detail="모델을 올리는 중이에요")
    return {"status": "UP", "model": state.version, "dimension": DIMENSION}

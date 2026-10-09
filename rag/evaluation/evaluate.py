"""
BIS RAG Evaluation using Ragas
================================
Measures: faithfulness, answer_relevancy, context_precision, context_recall
Run with: python rag/evaluation/evaluate.py

Requires OPENAI_API_KEY and a running RAG service.
"""
import asyncio
import json
import httpx
from datasets import Dataset
from ragas import evaluate
from ragas.metrics import (
    faithfulness,
    answer_relevancy,
    context_precision,
    context_recall,
)
from ragas.llms import LangchainLLMWrapper
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from config import settings

# ── Ground truth Q&A pairs (extend this for thorough eval) ───

EVAL_DATASET = [
    {
        "question": "Which Indian Standard covers hot water bottles?",
        "ground_truth": "IS 2902:2006 covers hot water bottles for household use.",
    },
    {
        "question": "How do I get BIS ISI mark certification for my product?",
        "ground_truth": (
            "Apply online at services.bis.gov.in under Scheme-I. "
            "BIS will inspect the factory and test samples at a recognised lab."
        ),
    },
    {
        "question": "What is the standard for BIS hallmarking of gold jewellery?",
        "ground_truth": (
            "IS 1417:2016 specifies grades of gold and gold alloys. "
            "IS 15820:2009 covers Assaying and Hallmarking Centres."
        ),
    },
    {
        "question": "What is the IS number for LPG cylinders?",
        "ground_truth": "LPG cylinders are covered under IS 3196 and IS 18841:2023.",
    },
    {
        "question": "Which scheme covers IT equipment like routers?",
        "ground_truth": (
            "IT and electronic equipment are covered under the Compulsory Registration "
            "Scheme (CRS). Routers fall under IS 13252."
        ),
    },
]


async def get_rag_response(question: str) -> dict:
    """Call the running RAG service and return answer + contexts."""
    async with httpx.AsyncClient(timeout=60) as client:
        resp = await client.post(
            f"{settings.rag_service_url if hasattr(settings, 'rag_service_url') else 'http://localhost:8000'}/rag/query",
            json={"query": question, "lang": "en", "history": [], "max_results": 5},
        )
        resp.raise_for_status()
        data = resp.json()
    return {
        "answer": data["answer"],
        "contexts": [chunk["excerpt"] for chunk in data.get("chunks", [])],
    }


async def build_eval_dataset():
    questions, answers, contexts, ground_truths = [], [], [], []

    for item in EVAL_DATASET:
        print(f"Evaluating: {item['question'][:60]}…")
        try:
            result = await get_rag_response(item["question"])
            questions.append(item["question"])
            answers.append(result["answer"])
            contexts.append(result["contexts"])
            ground_truths.append(item["ground_truth"])
        except Exception as e:
            print(f"  ERROR: {e}")

    return Dataset.from_dict({
        "question": questions,
        "answer": answers,
        "contexts": contexts,
        "ground_truth": ground_truths,
    })


def run_evaluation():
    llm = ChatOpenAI(
        model="gpt-4o",
        openai_api_key=settings.openai_api_key,
        openai_api_base=settings.openai_api_base,
    )
    emb = OpenAIEmbeddings(
        model=settings.embedding_model,
        openai_api_key=settings.openai_api_key,
        openai_api_base=settings.openai_api_base,
    )

    dataset = asyncio.run(build_eval_dataset())

    if len(dataset) == 0:
        print("No results to evaluate — is the RAG service running?")
        return

    print(f"\nEvaluating {len(dataset)} samples…")
    result = evaluate(
        dataset=dataset,
        metrics=[faithfulness, answer_relevancy, context_precision, context_recall],
        llm=LangchainLLMWrapper(llm),
        embeddings=emb,
    )

    print("\n" + "="*60)
    print("BIS RAG Evaluation Results")
    print("="*60)
    df = result.to_pandas()
    print(df[["question", "faithfulness", "answer_relevancy",
              "context_precision", "context_recall"]].to_string(index=False))
    print("\nMean scores:")
    for col in ["faithfulness", "answer_relevancy", "context_precision", "context_recall"]:
        if col in df.columns:
            print(f"  {col:<25} {df[col].mean():.3f}")

    # Save results
    output = "rag/evaluation/results.json"
    result_dict = {col: float(df[col].mean()) for col in df.columns if df[col].dtype == "float64"}
    with open(output, "w") as f:
        json.dump(result_dict, f, indent=2)
    print(f"\nResults saved to {output}")


if __name__ == "__main__":
    run_evaluation()

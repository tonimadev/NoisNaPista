"""
NoisNaPista - analise exploratoria + treino de um classificador binario
"deteccao real x alarme falso" a partir das janelas de acelerometro
capturadas e rotuladas manualmente no app (tela de Debug).

Fonte dos dados: banco Room 'noisnapista.db' puxado do celular via
'adb ... run-as digital.tonima.noisnapista cat databases/noisnapista.db'
e exportado para JSON com sqlite3 -json (tabelas potholes + sensor_windows).
"""
import json
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import LeaveOneOut, cross_val_predict
from sklearn.metrics import (
    confusion_matrix,
    balanced_accuracy_score,
    classification_report,
)
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline
from sklearn.tree import DecisionTreeClassifier, export_text
import joblib

HERE = Path(__file__).parent
RAW_PATH = HERE / "raw_export.json"
OUT_DIR = HERE
MODEL_DIR = HERE / "model"
MODEL_DIR.mkdir(exist_ok=True)


def load_rows():
    return json.loads(RAW_PATH.read_text())


def extract_features(samples: list[dict]) -> dict:
    df = pd.DataFrame(samples).sort_values("offsetMs")
    t = df["offsetMs"].to_numpy(dtype=float)
    x = df["x"].to_numpy(dtype=float)
    y = df["y"].to_numpy(dtype=float)
    z = df["z"].to_numpy(dtype=float)
    mag = np.sqrt(x**2 + y**2 + z**2)

    duration_ms = float(t.max() - t.min()) if len(t) > 1 else 0.0
    n = len(t)
    sample_rate_hz = (n / (duration_ms / 1000.0)) if duration_ms > 0 else np.nan

    # posicao (dentro da janela) da amostra com maior |z| -> "onde" dentro do
    # intervalo [-2s, +1s] esta o pico (deve ficar perto do 0 = instante do gatilho)
    idx_peak = int(np.argmax(np.abs(z)))
    peak_offset_ms = float(t[idx_peak])

    pre = z[t < 0]
    post = z[t >= 0]

    def safe_std(a):
        return float(np.std(a)) if len(a) > 1 else 0.0

    # "jerk": variacao de z por passo de tempo (proxy p/ quao abrupto foi o
    # impacto vs. uma trepidacao continua de pista ruim)
    if n > 1:
        dz = np.diff(z)
        dt = np.diff(t)
        dt[dt == 0] = 1.0
        jerk = dz / dt
        jerk_max = float(np.max(np.abs(jerk)))
    else:
        jerk_max = 0.0

    return {
        "n_samples": n,
        "duration_ms": duration_ms,
        "sample_rate_hz": sample_rate_hz,
        "peak_abs_z": float(np.max(np.abs(z))),
        "peak_abs_x": float(np.max(np.abs(x))),
        "peak_abs_y": float(np.max(np.abs(y))),
        "peak_to_peak_z": float(z.max() - z.min()),
        "rms_z": float(np.sqrt(np.mean(z**2))),
        "std_z": safe_std(z),
        "std_pre_z": safe_std(pre),
        "std_post_z": safe_std(post),
        "peak_magnitude": float(mag.max()),
        "mean_magnitude": float(mag.mean()),
        "jerk_max": jerk_max,
        "peak_offset_ms": peak_offset_ms,
    }


FEATURE_COLUMNS = [
    "peak_abs_z",
    "peak_abs_x",
    "peak_abs_y",
    "peak_to_peak_z",
    "rms_z",
    "std_z",
    "std_pre_z",
    "std_post_z",
    "peak_magnitude",
    "mean_magnitude",
    "jerk_max",
    "peak_offset_ms",
]


def build_dataset() -> pd.DataFrame:
    rows = load_rows()
    records = []
    for row in rows:
        samples = json.loads(row["samplesJson"])
        feats = extract_features(samples)
        records.append(
            {
                "id": row["id"],
                "timestamp": row["timestamp"],
                "label": row["label"],
                "note": row["note"],
                "severity_trigger": row["severity"],
                **feats,
            }
        )
    df = pd.DataFrame.from_records(records).sort_values("timestamp").reset_index(drop=True)
    df["is_real_event"] = (df["label"] != "FALSE_POSITIVE").astype(int)
    df["gap_since_prev_s"] = df["timestamp"].diff().div(1000)
    return df


def main():
    df = build_dataset()
    df.to_csv(OUT_DIR / "dataset_features.csv", index=False)

    print("=" * 70)
    print(f"Total de deteccoes rotuladas: {len(df)}")
    print(df["label"].value_counts().to_string())
    print()
    print("Deteccoes agrupadas em <20s da anterior (possivel mesmo evento "
          "fisico capturado mais de uma vez pela suspensao):")
    clusters = df[df["gap_since_prev_s"] < 20][["id", "label", "gap_since_prev_s"]]
    print(clusters.to_string(index=False) if len(clusters) else "  (nenhuma)")
    print("=" * 70)

    X = df[FEATURE_COLUMNS].to_numpy()
    y = df["is_real_event"].to_numpy()

    majority_baseline = max(np.mean(y == 0), np.mean(y == 1))
    print(f"\nBaseline (sempre prever a classe majoritaria): {majority_baseline:.1%}")

    loo = LeaveOneOut()

    logreg = Pipeline([
        ("scaler", StandardScaler()),
        ("clf", LogisticRegression(class_weight="balanced", max_iter=2000, C=0.5)),
    ])
    y_pred_logreg = cross_val_predict(logreg, X, y, cv=loo)

    tree = DecisionTreeClassifier(max_depth=2, class_weight="balanced", random_state=0)
    y_pred_tree = cross_val_predict(tree, X, y, cv=loo)

    for name, y_pred in [("Regressao Logistica", y_pred_logreg), ("Arvore de Decisao (depth=2)", y_pred_tree)]:
        print(f"\n--- {name} (validacao Leave-One-Out, n={len(y)}) ---")
        print("Acuracia balanceada:", round(balanced_accuracy_score(y, y_pred), 3))
        print("Matriz de confusao [linhas=real, colunas=previsto] (0=FALSE_POSITIVE,1=EVENTO_REAL):")
        print(confusion_matrix(y, y_pred))
        print(classification_report(y, y_pred, target_names=["FALSE_POSITIVE", "EVENTO_REAL"], zero_division=0))

    # treina versao final com todos os dados (para uso/retraining futuro)
    final_model = Pipeline([
        ("scaler", StandardScaler()),
        ("clf", LogisticRegression(class_weight="balanced", max_iter=2000, C=0.5)),
    ])
    final_model.fit(X, y)
    joblib.dump(final_model, MODEL_DIR / "real_event_classifier.joblib")
    (MODEL_DIR / "feature_order.json").write_text(json.dumps(FEATURE_COLUMNS, indent=2))

    coefs = final_model.named_steps["clf"].coef_[0]
    print("\nCoeficientes (regressao logistica, dados padronizados) — sinal indica direcao:")
    for name, c in sorted(zip(FEATURE_COLUMNS, coefs), key=lambda p: -abs(p[1])):
        print(f"  {name:>18s}: {c:+.3f}")

    final_tree = DecisionTreeClassifier(max_depth=2, class_weight="balanced", random_state=0)
    final_tree.fit(X, y)
    print("\nArvore final (max_depth=2), so para leitura humana:")
    print(export_text(final_tree, feature_names=FEATURE_COLUMNS))

    # tabela resumida por classe original (para o documento)
    summary = df.groupby("label")[["peak_abs_z", "severity_trigger", "std_z", "jerk_max"]].agg(["count", "mean", "std"])
    summary.to_csv(OUT_DIR / "summary_by_label.csv")
    print("\nResumo por rotulo salvo em summary_by_label.csv")


if __name__ == "__main__":
    main()

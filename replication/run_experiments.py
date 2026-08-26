"""
Run the three effective-bidding-range experiments from the report:

  Exp 1  current rule      : 102%/98% range, out-of-range orders parked
  Exp 2  reject, don't park: same range, out-of-range orders rejected
  Exp 3  + closed 10% band : Exp 1 plus a closed +/-10% band on last trade

Baseline: N_RUNS runs x DAYS days each, metrics averaged across runs.
Stress:   the reconstructed thin-book / benchmark-jump scenario, one day,
          identical seed across the three mechanisms.

Outputs: results table to stdout, figures/ *.png
"""

import statistics as st
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

from absm import run

N_RUNS = 30
DAYS = 10

EXPS = {
    "Exp 1 (current rule)":   dict(mode="park",   closed_band=None),
    "Exp 2 (reject)":         dict(mode="reject", closed_band=None),
    "Exp 3 (+10% closed)":    dict(mode="park",   closed_band=0.10),
}


def baseline():
    rows = {}
    for name, cfg in EXPS.items():
        res = [run(seed=1000 + i, days=DAYS, **cfg) for i in range(N_RUNS)]
        rows[name] = dict(
            vol_trade=st.mean(r.vol_trade for r in res),
            vol_mid=st.mean(r.vol_mid for r in res),
            spread=st.mean(r.spread for r in res),
            volume=st.mean(r.volume for r in res),
            empty=st.mean(r.empty for r in res),
            mae=st.mean(r.mae for r in res),
            mre=st.mean(r.mre for r in res),
        )
        print(f"done: {name}")
    return rows


def stress():
    out = {}
    scenario = dict(day=0, minute=200, ask_mult=10.0, n_chasers=5)
    for name, cfg in EXPS.items():
        r = run(seed=7, days=1, stress=scenario, **cfg)
        out[name] = r.prices
        print(f"stress done: {name}  max price = {max(r.prices):.2f}")
    return out


def main():
    rows = baseline()

    print("\n=== Baseline (means over %d runs x %d days) ===" % (N_RUNS, DAYS))
    header = ["metric"] + list(rows)
    metrics = [
        ("trade-price vol (bp/min)", "vol_trade", "{:.2f}"),
        ("midquote vol (bp/min)",    "vol_mid",   "{:.2f}"),
        ("bid-ask spread (cents)",   "spread",    "{:.2f}"),
        ("volume (lots)",            "volume",    "{:,.0f}"),
        ("empty-side minutes",       "empty",     "{:.1f}"),
        ("MAE vs fundamental (yuan)","mae",       "{:.3f}"),
        ("MRE vs fundamental (%)",   "mre",       "{:.2f}"),
    ]
    print("| " + " | ".join(header) + " |")
    print("|" + "---|" * len(header))
    for label, key, fmt in metrics:
        cells = [fmt.format(rows[name][key]) for name in rows]
        print("| " + label + " | " + " | ".join(cells) + " |")

    paths = stress()

    # ---- figure: stress scenario ------------------------------------------
    fig, ax = plt.subplots(figsize=(8, 4.2))
    styles = {"Exp 1 (current rule)": ("-",  "tab:red"),
              "Exp 2 (reject)":       ("--", "tab:orange"),
              "Exp 3 (+10% closed)":  ("-",  "tab:green")}
    for name, px in paths.items():
        ls, c = styles[name]
        ax.plot(px, ls, color=c, lw=1.4, label=name)
    ax.axvline(200, color="grey", lw=0.8, ls=":", label="extreme sell quote")
    ax.set_xlabel("minute of the trading day")
    ax.set_ylabel("trade price (yuan)")
    ax.set_title("Stress scenario: thin book + benchmark-price jump")
    ax.legend(frameon=False, fontsize=9)
    fig.tight_layout()
    fig.savefig("figures/stress_scenario.png", dpi=150)
    print("\nwrote figures/stress_scenario.png")

    # ---- figure: sample baseline path -------------------------------------
    r = run(seed=1000, days=DAYS, **EXPS["Exp 1 (current rule)"])
    fig, ax = plt.subplots(figsize=(8, 4.2))
    ax.plot(r.prices, lw=0.9, color="tab:blue", label="trade price")
    ax.plot(r.fundamentals, lw=0.9, color="black", alpha=0.6, label="fundamental value")
    ax.set_xlabel("minute")
    ax.set_ylabel("yuan")
    ax.set_title("Baseline run: price vs. fundamental value (Exp 1)")
    ax.legend(frameon=False, fontsize=9)
    fig.tight_layout()
    fig.savefig("figures/baseline_run.png", dpi=150)
    print("wrote figures/baseline_run.png")


if __name__ == "__main__":
    main()

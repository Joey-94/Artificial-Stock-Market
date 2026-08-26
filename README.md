# Artificial Stock Market for Trading-Mechanism Research

An agent-based simulation of a limit-order-book market, written in Java on the
[MASON](https://cs.gmu.edu/~eclab/projects/mason/) multi-agent framework.
1,500 traders with heterogeneous beliefs submit limit and market orders into a
continuous double auction; prices emerge from order flow, not from an
equilibrium equation. Because the trading mechanism itself is explicit code,
the model can answer questions historical data cannot: *what happens to
volatility, liquidity, and price discovery if we change the trading rules?*

The engine has carried two research applications:

- **Effective-bidding-range policy study (SZSE Research Institute).** During my internship, the engine was configured to replicate ChiNext
  trading rules and used to stress-test four proposed redesigns of the effective bidding
  range — the rule restricting limit-order prices to a band around prevailing
  quotes. The study was written up as SZSE internal research
  report No. 378 on the effective bidding range (Dec 2020), a collaboration
  with researchers at the institute.
- **Strategic corporate disclosure (this snapshot).** A listed-firm agent
  learns, by reinforcement, whether to report earnings truthfully or manage
  them, and heterogeneously sophisticated investors trade on the reports.

**Attribution.** The agent behavioral models — `HBPlayer` (the
heterogeneous-belief trader) and `Corporation` (the learning disclosure agent)
— are my implementation. The market infrastructure (MASON scheduling, the
double-auction order book, the fundamental-value process, logging) is the
research group's existing codebase, which I studied and extended. The full
source is shared lab infrastructure and is available on request.

---

## Architecture

```
FinancialModel (MASON SimState)          simulation root: schedule, params, RNG
 ├─ ModelFactory                         builds agents by reflection from config
 ├─ HBPlayer × 1500        [mine]        belief → forecast → target position → order
 ├─ Corporation × 1        [mine]        truthful reporting vs. earnings management
 ├─ Asset                                fundamental-value process (earnings shocks)
 ├─ Market                               order routing + admission rules
 │   └─ DoubleAuctionOrderBook           price–time priority matching, 40-level depth
 └─ Reporter → timeSeries.txt            60+ columns/tick → MATLAB analysis scripts
```

One tick = one simulated minute; 240 ticks = one trading day. Scenarios are
configured entirely from `setups/main.properties` and an agent census file —
no recompilation between experiments.

## From the report's math to the code

The model design section of the policy report specifies the trader in
equations. Each maps to a specific site in the code:

| Report specification | Code |
|---|---|
| Eq. (1) chartist-strength draw, scale g₀ = 0.6 | `main.properties: gc=0.6`; per-agent draws in `HBPlayer.initializeAgent()` |
| Eq. (2) horizon τᵢ shrinks with chartist strength (τmax = 480) | `main.properties: tau=480`; `this.tau = (int)(baseTau * (1+g1)/(1+g2))` |
| Eq. (3) market-arrival rate λᵢ = 1/τᵢ, Poisson entry | `this.lambda = 1.0/this.tau`; `randDist.nextPoisson(lambda)` |
| Eq. (4) ln P̂ = fundamental + noise + chartist momentum | `HBPlayer.getForecastPrice()` |
| Eq. (5) risk aversion αᵢ inverse in chartist strength | `this.alph = initialAlph * (1+g1)/(1+g2)` |
| Eqs. (11)–(12) target position W·π/P; order = target − held | `generateOrders()`: `os = (int)(piW/price) - position` |
| Order-type table (market vs. limit by best quotes) | the four-branch dispatch in `generateOrders()` |
| Calibration: fat tails, Hurst exponents, ACF decay | `scripts/hurst_exponent.m`, `compareRuns.m` |

*(This snapshot is the later disclosure-study variant; a few constants differ
from the effective-bidding-range configuration described in the report.)*

### The forecast rule

Each agent blends a fundamentalist signal, a moving-average momentum signal,
and noise, with its own randomly drawn (then normalized) weights — the
population spans a continuum from near-pure fundamentalists to near-pure
chartists:

```java
// HBPlayer.getForecastPrice() — excerpt
double fundamentalR = Math.log(pf / pt);        // log-gap: perceived value vs price

pShort = pShort / length1;                      // MA over  τ/4 ticks
pLong  = pLong  / length2;                      // MA over  τ   ticks
meanR  = pShort / pLong - 1;                    // momentum

forecastR = g1 * fundamentalR + g2 * meanR + gn * epsilon;
forecastP = pt * Math.exp(forecastR);
```

The forecast maps to a target position through a wealth constraint, and the
gap between target and current holdings becomes an order. Whether it enters
passively or crosses the spread depends on where the agent's price sits
relative to the book:

```java
// HBPlayer.generateOrders() — excerpt
if (price < ask && os > 0)        { /* limit buy joins the book        */ }
else if (ask <= price && os > 0)  { /* market buy lifts the ask        */ }
else if (os < 0 && price <= bid)  { /* market sell hits the bid        */ }
else if (os < 0 && price > bid)   { /* limit sell joins the book       */ }
```

### The learning firm (disclosure variant)

```java
// Corporation.java — excerpt
// softmax choice between earnings management (EM) and truth-telling
double pEM = Math.exp(beta * earningsManagement)
           / (Math.exp(beta * earningsManagement) + Math.exp(beta * truthTelling));
EM = Math.random() < pEM;

// utility of last period's action: risk-adjusted log price growth (CARA)
rate    = Math.log(avP1 / avP2);
utility = rate - 0.5 * rho * variance;
utility = -Math.exp(-rho * utility);

// running-average reinforcement update of the chosen strategy's value
earningsManagement = (earningsManagement * emTimes + utility) / (++emTimes);
```

Smoothing accumulates in a hidden reserve; when the reserve breaches a bound
the firm is forced into a "big bath" — a corrective disclosure that dumps the
distortion and hands the strategy a painful utility draw.

## The effective-bidding-range experiments

Under ChiNext rules, a limit buy may not exceed 102% of its benchmark quote
and a limit sell may not fall below 98%; out-of-band orders are parked by the
exchange host and re-activated when prices move into range. In August 2020 a
newly listed stock (Kangtai Medical, 300869) showed that with a thin order
book the band can chase a jumping benchmark upward and admit extreme trades.

Three mechanism variants were implemented in the market layer, each run 30
times under normal conditions plus a reconstructed thin-book stress scenario:

| Metric (30-run means) | Exp 1: current rule | Exp 2: reject instead of park | Exp 3: + closed 10% band on last trade price |
|---|---:|---:|---:|
| Trade-price volatility (bp/min) | 101.03 | 83.50 | 100.12 |
| Midquote volatility (bp/min) | 110.81 | 68.11 | 92.73 |
| Bid–ask spread (cents) | 14.22 | 14.89 | 13.65 |
| Volume (lots) | 601,379 | 518,193 | 599,045 |
| Empty-side book events | 18 | 26 | 15 |
| Pricing error MAE (yuan) | 0.26 | 0.23 | 0.25 |
| Pricing error MRE (%) | 1.28 | 1.12 | 1.27 |
| Price jump under stress | reproduced | reproduced | **prevented** |

Two results carried the policy analysis:

1. The stress scenario reproduced the real-world jump under both the current
   mechanism **and** the reject variant — evidence that the order-parking rule,
   widely suspected at the time, was *not* the direct cause.
2. Anchoring a second, closed band to the last trade price contained the jump
   — it breaks the feedback loop between a moving quote benchmark and the
   orders it admits — at negligible cost to volatility, liquidity, or pricing
   efficiency in normal conditions.

## Calibration

Counterfactuals only mean something if the engine reproduces the stylized
facts of real markets first. Baseline-run statistics (30 runs):

| Statistic | Value |
|---|---:|
| Return kurtosis (fat tails) | 3.94 |
| Return skewness | −0.02 |
| Hurst exponent, absolute returns | 0.58 |
| Hurst exponent, midquote returns | 0.70 |
| Hurst exponent, bid–ask spread | 0.75 |

All Hurst exponents exceed 0.5 and the autocorrelation of absolute returns
decays slowly over 30+ lags — volatility clustering and long memory,
consistent with A-share data.

## Repository layout

| Path | LOC | Role |
|---|---:|---|
| `src/model/agents/HBPlayer.java` | 503 | heterogeneous-belief trader **(mine)** |
| `src/model/agents/Corporation.java` | 235 | learning disclosure agent **(mine)** |
| `src/model/market/Asset.java` | 167 | fundamental-value process |
| `src/model/market/Market.java` | 627 | order routing, admission rules |
| `src/model/market/books/DoubleAuctionOrderBook.java` | 624 | CDA matching engine |
| `src/model/FinancialModel.java` | 96 | MASON SimState root |
| `src/support/ModelFactory.java` | 209 | reflection-based agent construction |
| `src/support/Reporter.java` | 212 | tick-level logging |
| `src/gui/FinancialModelWithUI.java` | 299 | live charts (JFreeChart) |
| `scripts/*.m` | — | Hurst, ACF, kernel regression, run comparison |

**Stack:** Java 8 · MASON · SSJ (stochastic simulation) · JFreeChart · MATLAB

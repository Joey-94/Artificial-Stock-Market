"""
Public reimplementation of the artificial-stock-market model specified in the
model-design appendix of the SZSE internal research report
"Research on Optimizing the Effective Bidding Range Mechanism in China's A-Share Market" (Dec 2020).

This is a REIMPLEMENTATION written from the report's published model design
(equations (1)-(19) and the mechanism descriptions), not the original
experiment code, which remains at the institute. Equation numbers in the
comments refer to the report's appendix.

Model summary
-------------
* One risky asset. Fundamental value follows a per-minute random walk with
  volatility sigma_f (10 bp/min in the stable regime).
* 1,500 investors with mixed heterogeneous beliefs (fundamental / chartist /
  noise). Chartist strength gc_i is drawn per agent and drives the agent's
  horizon, arrival rate, and risk aversion.
* Continuous double auction with price-time priority. No daily price limit
  (ChiNext first-five-days regime), intraday halt lines at +/-30% and +/-60%
  vs. the day's open, T+1 (shares bought today cannot be sold today),
  no short selling, no costs.
* Mechanism layer (the object of study):
    - effective bidding range: buy limit <= 102% of the buy benchmark,
      sell limit >= 98% of the sell benchmark; benchmark cascade =
      counterparty best -> own best -> last trade -> previous close;
    - out-of-range orders are PARKED (stored and auto-released when they
      re-enter the range) or REJECTED, depending on the experiment;
    - optional second, closed band: all limit prices within +/-10% of the
      last trade price (experiment 3).
"""

import heapq
import math
import random
from collections import namedtuple

TICK = 0.01          # minimum price increment (yuan)
DAY = 240            # simulation periods per trading day (1 period = 1 min)


def round_tick(p):
    return max(TICK, round(p / TICK) * TICK)


# ---------------------------------------------------------------------------
# Order book: continuous double auction, price-time priority
# ---------------------------------------------------------------------------

class Order:
    __slots__ = ("side", "price", "qty", "agent", "time", "oid", "alive")

    def __init__(self, side, price, qty, agent, time, oid):
        self.side, self.price, self.qty = side, price, qty
        self.agent, self.time, self.oid = agent, time, oid
        self.alive = True


class OrderBook:
    def __init__(self):
        self.bids = []   # heap of (-price, time, oid, Order)
        self.asks = []   # heap of ( price, time, oid, Order)

    def _clean(self, heap):
        while heap and not heap[0][3].alive:
            heapq.heappop(heap)

    def best_bid(self):
        self._clean(self.bids)
        return self.bids[0][3].price if self.bids else None

    def best_ask(self):
        self._clean(self.asks)
        return self.asks[0][3].price if self.asks else None

    def add(self, order):
        if order.side == "B":
            heapq.heappush(self.bids, (-order.price, order.time, order.oid, order))
        else:
            heapq.heappush(self.asks, (order.price, order.time, order.oid, order))

    def match_market(self, side, qty, on_fill):
        """Execute a market order of `qty` against the book; returns filled qty."""
        book = self.asks if side == "B" else self.bids
        filled = 0
        while qty > 0:
            self._clean(book)
            if not book:
                break
            top = book[0][3]
            take = min(qty, top.qty)
            on_fill(top, take)
            top.qty -= take
            qty -= take
            filled += take
            if top.qty == 0:
                top.alive = False
        return filled


# ---------------------------------------------------------------------------
# Mechanism layer: effective bidding range (+ optional closed band)
# ---------------------------------------------------------------------------

class Mechanism:
    """
    mode='park'   : out-of-range limit orders are stored and auto-released
                    once prices move so that they re-enter the range
    mode='reject' : out-of-range limit orders are rejected outright
    closed_band   : if set (e.g. 0.10), all limit prices must additionally lie
                    within +/-band of the last trade price (experiment 3)
    """

    def __init__(self, market, mode="park", closed_band=None):
        self.m = market
        self.mode = mode
        self.closed_band = closed_band
        self.parked = []
        self._releasing = False

    # benchmark cascade: counterparty best -> own best -> last trade -> prev close
    def buy_benchmark(self):
        for p in (self.m.book.best_ask(), self.m.book.best_bid(),
                  self.m.last_trade, self.m.prev_close):
            if p is not None:
                return p

    def sell_benchmark(self):
        for p in (self.m.book.best_bid(), self.m.book.best_ask(),
                  self.m.last_trade, self.m.prev_close):
            if p is not None:
                return p

    def in_range(self, side, price):
        if side == "B":
            if price > 1.02 * self.buy_benchmark() + 1e-9:      # buy capped at 102%
                return False
        else:
            if price < 0.98 * self.sell_benchmark() - 1e-9:     # sell floored at 98%
                return False
        if self.closed_band is not None:                        # experiment 3
            ref = self.m.last_trade if self.m.last_trade is not None else self.m.prev_close
            if not ((1 - self.closed_band) * ref - 1e-9 <= price
                    <= (1 + self.closed_band) * ref + 1e-9):
                return False
        return True

    def submit(self, order):
        """Returns 'book' | 'parked' | 'rejected'."""
        if self.in_range(order.side, order.price):
            self.m.place(order)
            return "book"
        if self.mode == "park":
            self.parked.append(order)
            return "parked"
        return "rejected"

    def release_parked(self):
        """Auto-release parked orders that have re-entered the range.
        Releasing an order can move the benchmark and free further parked
        orders, so loop to a fixed point (guarded against re-entrancy)."""
        if self._releasing or not self.parked:
            return
        self._releasing = True
        try:
            changed = True
            while changed:
                changed = False
                still = []
                for o in self.parked:
                    if o.alive and self.in_range(o.side, o.price):
                        self.m.place(o)
                        changed = True
                    elif o.alive:
                        still.append(o)
                self.parked = still
        finally:
            self._releasing = False


# ---------------------------------------------------------------------------
# Market: matching, halts, bookkeeping
# ---------------------------------------------------------------------------

class Market:
    def __init__(self, rng, mode="park", closed_band=None):
        self.rng = rng
        self.book = OrderBook()
        self.mech = Mechanism(self, mode, closed_band)
        self.last_trade = None
        self.prev_close = None
        self.oid = 0
        self.time = 0
        self.halted_until = -1
        self.day_open = None
        self.halt30_done = False
        self.halt60_done = False
        # per-minute records
        self.minute_price = []
        self.minute_mid = []
        self.minute_spread = []
        self.minute_volume = []
        self.empty_side_minutes = 0
        self._vol_this_minute = 0

    def next_oid(self):
        self.oid += 1
        return self.oid

    def halted(self):
        return self.time < self.halted_until

    def _check_halt(self, price):
        # intraday halt lines vs the day's open: first touch of +/-30%, +/-60%
        if self.day_open is None:
            return
        move = abs(price / self.day_open - 1)
        if move >= 0.60 and not self.halt60_done:
            self.halt60_done = True
            self.halted_until = self.time + 10          # 10-minute halt
        elif move >= 0.30 and not self.halt30_done:
            self.halt30_done = True
            self.halted_until = self.time + 10

    def _fill(self, resting, qty, taker):
        price = resting.price
        maker = resting.agent
        if resting.side == "S":                          # taker buys
            taker.on_buy(price, qty)
            maker.on_sell(price, qty)
        else:                                            # taker sells
            taker.on_sell(price, qty)
            maker.on_buy(price, qty)
        self.last_trade = price
        if self.day_open is None:
            self.day_open = price
        self._vol_this_minute += qty
        self._check_halt(price)

    def place(self, order):
        """Put a limit order in the book, crossing it if marketable."""
        opp = self.book.best_ask() if order.side == "B" else self.book.best_bid()
        while order.qty > 0 and opp is not None and (
            (order.side == "B" and order.price >= opp) or
            (order.side == "S" and order.price <= opp)
        ):
            filled = self.book.match_market(
                order.side, order.qty,
                lambda resting, q: self._fill(resting, q, order.agent))
            if filled == 0:
                break
            order.qty -= filled
            opp = self.book.best_ask() if order.side == "B" else self.book.best_bid()
        if order.qty > 0:
            self.book.add(order)
        else:
            order.alive = False
        self.mech.release_parked()

    def market_order(self, agent, side, qty):
        self.book.match_market(side, qty,
                               lambda resting, q: self._fill(resting, q, agent))
        self.mech.release_parked()

    def end_minute(self, fundamental):
        p = self.last_trade if self.last_trade is not None else self.prev_close
        bb, ba = self.book.best_bid(), self.book.best_ask()
        self.minute_price.append(p)
        self.minute_mid.append((bb + ba) / 2 if bb and ba else p)   # Eq. (14)
        self.minute_spread.append(ba - bb if bb and ba else None)   # Eq. (17)
        self.minute_volume.append(self._vol_this_minute)
        if bb is None or ba is None:
            self.empty_side_minutes += 1
        self._vol_this_minute = 0

    def end_day(self):
        self.prev_close = self.last_trade if self.last_trade is not None else self.prev_close
        self.day_open = None
        self.halt30_done = False
        self.halt60_done = False


# ---------------------------------------------------------------------------
# Heterogeneous-belief investor  (report Eqs. (1)-(13))
# ---------------------------------------------------------------------------

class Investor:
    GC0 = 0.6        # baseline chartist strength      (Eq. (1))
    TAU_MAX = 480    # maximum horizon, two days       (Eq. (2))
    ALPHA0 = 2.0     # baseline risk aversion          (Eq. (5))
    RHO = 0.04       # information-lag loss coefficient (Eq. (13))

    def __init__(self, rng, market, sigma_f, cash, shares):
        self.rng = rng
        self.m = market
        self.sigma_f = sigma_f
        self.cash = cash
        self.pos = shares
        self.bought_today = 0                     # T+1 constraint
        self.gc = 2 * self.GC0 * rng.random()     # Eq. (1):  gc_i = 2*gc0*phi1
        self.tau = max(2, int(self.TAU_MAX / (1 + self.gc)))   # Eq. (2)
        self.lam = 1.0 / self.tau                 # Eq. (3):  arrival rate
        self.alpha = self.ALPHA0 / (1 + self.gc)  # Eq. (5)
        self.omega = self.gc / (1 + self.gc)      # Eq. (6)
        self.order = None

    # --- wealth accounting -------------------------------------------------
    def wealth(self, price):
        return self.cash + self.pos * price

    def on_buy(self, price, qty):
        self.cash -= price * qty
        self.pos += qty
        self.bought_today += qty

    def on_sell(self, price, qty):
        self.cash += price * qty
        self.pos -= qty

    def new_day(self):
        self.bought_today = 0

    # --- decision (Eqs. (4), (7)-(13) and the order table) -----------------
    def act(self, fundamental, prices):
        m = self.m
        if self.order is not None and self.order.alive:
            self.order.alive = False              # replace outstanding order
        self.order = None

        p_now = prices[-1]
        p_lag = prices[-self.tau] if len(prices) >= self.tau else prices[0]

        # Eq. (4): ln P_forecast = ln f + phi2*sigma_f*sqrt(tau) + gc*ln(p_t/p_{t-tau})
        phi2 = self.rng.uniform(-1, 1)
        ln_pf = (math.log(fundamental)
                 + phi2 * self.sigma_f * math.sqrt(self.tau)
                 + self.gc * math.log(p_now / p_lag))

        # Eq. (7): perceived variance = weighted harmonic mix of fundamental
        # and realized market variance over the past tau minutes
        window = prices[-self.tau:]
        rets = [math.log(window[i + 1] / window[i]) for i in range(len(window) - 1)]
        var_m = max(1e-10, sum(r * r for r in rets) / max(1, len(rets)))
        var_f = self.sigma_f ** 2
        var_i = 1.0 / ((1 - self.omega) / var_f + self.omega / var_m)

        # Eqs. (8)-(10): required return, target risky share, order price
        phi4 = self.rng.random()
        er = self.alpha * var_i * phi4            # Eq. (8)
        pi = phi4                                 # Eq. (9):  pi = er/(alpha*var)
        price = round_tick(math.exp(ln_pf - er))  # Eq. (10)

        # Eqs. (11)-(12): target position and order size
        target = int(self.wealth(p_now) * pi / price)   # Eq. (11)
        q = target - self.pos                           # Eq. (12)
        if q == 0:
            return

        delta = self.RHO * p_now                        # Eq. (13)
        bb, ba = m.book.best_bid(), m.book.best_ask()
        oid = m.next_oid()

        if q > 0:                                       # buying
            q = min(q, int(self.cash / price)) if price > 0 else 0
            if q <= 0:
                return
            if ba is not None and price - delta >= ba:  # order table: market buy
                m.market_order(self, "B", q)
            else:                                       # order table: limit buy
                o = Order("B", price, q, self, m.time, oid)
                if m.mech.submit(o) == "book":
                    self.order = o
                elif o.alive:
                    self.order = o                      # parked orders stay ours
        else:                                           # selling (no shorting, T+1)
            q = min(-q, self.pos - self.bought_today)
            if q <= 0:
                return
            if bb is not None and price + delta <= bb:  # order table: market sell
                m.market_order(self, "S", q)
            else:                                       # order table: limit sell
                o = Order("S", price, q, self, m.time, oid)
                if m.mech.submit(o) == "book":
                    self.order = o
                elif o.alive:
                    self.order = o


# ---------------------------------------------------------------------------
# Simulation driver
# ---------------------------------------------------------------------------

Result = namedtuple("Result", "vol_trade vol_mid spread volume empty mae mre "
                              "prices fundamentals")


def run(seed, mode="park", closed_band=None, days=10, n_agents=1500,
        sigma_f=0.0010, f0=20.0, stress=None):
    """
    One simulation run.
    stress: None, or dict(day=..., minute=..., ask_mult=..., n_chasers=...)
            reproducing the thin-book / benchmark-jump scenario.
    """
    rng = random.Random(seed)
    m = Market(rng, mode, closed_band)
    m.prev_close = f0
    m.last_trade = f0
    agents = [Investor(rng, m, sigma_f, cash=20000.0, shares=1000)
              for _ in range(n_agents)]

    f = f0
    fundamentals, prices = [], [f0] * Investor.TAU_MAX   # warm history
    total_minutes = days * DAY

    for t in range(total_minutes):
        m.time = t
        minute_of_day = t % DAY
        if minute_of_day == 0:
            for a in agents:
                a.new_day()

        # fundamental value: per-minute random walk, sigma_f (appendix (四) 1)
        f = round_tick(f * math.exp(rng.gauss(0.0, sigma_f)))
        fundamentals.append(f)

        stress_now = (stress is not None
                      and t // DAY == stress["day"]
                      and minute_of_day >= stress["minute"])

        if not m.halted():
            if stress_now:
                # --- reconstructed risk scenario -------------------------------
                # (report §4: thin late-session book, one-sided flow)
                if minute_of_day == stress["minute"]:
                    # sellers withdraw: the resting ask side empties out
                    # (first-day T+1 leaves almost nothing available to sell)
                    for tup in list(m.book.asks):
                        tup[3].alive = False
                    # one extreme limit sell far above the market becomes the
                    # only ask -> the buy benchmark jumps with it
                    seller = agents[0]
                    hi = round_tick(stress["ask_mult"] * prices[-1])
                    m.mech.submit(Order("S", hi, 200, seller, t, m.next_oid()))
                elif minute_of_day > stress["minute"]:
                    # aggressive buyers chase up to whatever the range admits
                    for _ in range(stress["n_chasers"]):
                        chaser = rng.choice(agents)
                        cap = 1.02 * m.mech.buy_benchmark()
                        if closed_band is not None:
                            ref = m.last_trade or m.prev_close
                            cap = min(cap, (1 + closed_band) * ref)
                        lo = min(prices[-1], cap)
                        px = round_tick(rng.uniform(lo, cap))
                        m.mech.submit(Order("B", px, 50, chaser, t, m.next_oid()))
            else:
                # normal minute: Poisson arrivals (Eq. (3))
                for a in agents:
                    if rng.random() < a.lam:
                        a.act(f, prices)

        m.end_minute(f)
        prices.append(m.minute_price[-1])
        if minute_of_day == DAY - 1:
            m.end_day()

    # ---- metrics (Eqs. (14)-(19)) -----------------------------------------
    px = m.minute_price
    mid = m.minute_mid

    def vol_bp(series):                                  # Eqs. (15)-(16)
        rets = [math.log(series[i + 1] / series[i]) for i in range(len(series) - 1)]
        mean = sum(rets) / len(rets)
        var = sum((r - mean) ** 2 for r in rets) / (len(rets) - 1)
        return math.sqrt(var) * 1e4                      # basis points / minute

    spreads = [s for s in m.minute_spread if s is not None]
    mae = sum(abs(p - fv) for p, fv in zip(px, fundamentals)) / len(px)   # Eq. (18)
    mre = sum(abs(p - fv) / fv for p, fv in zip(px, fundamentals)) / len(px)  # Eq. (19)

    return Result(vol_trade=vol_bp(px), vol_mid=vol_bp(mid),
                  spread=(sum(spreads) / len(spreads)) * 100 if spreads else float("nan"),
                  volume=sum(m.minute_volume) / 100.0,   # lots of 100 shares
                  empty=m.empty_side_minutes, mae=mae, mre=mre * 100,
                  prices=px, fundamentals=fundamentals)

"""
Concurrency check for BankLite using only the Python standard library.

What it does
  1. Creates two accounts (A and B) with 100000.00 each.
  2. Fires N concurrent transfers of 1.00 from A to B (some will be retried by the service).
  3. Reads both balances and checks that no money was created or lost.

Run:  python loadtest/concurrency_check.py [base_url] [requests] [threads]
Example: python loadtest/concurrency_check.py http://localhost:8080 500 50
"""
import json
import sys
import time
import uuid
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
N = int(sys.argv[2]) if len(sys.argv) > 2 else 500
THREADS = int(sys.argv[3]) if len(sys.argv) > 3 else 50
START = 100000.00
AMOUNT = 1.00


def call(method, path, body=None, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, json.loads(resp.read() or b"null")
    except urllib.error.HTTPError as e:
        return e.code, None


def make_account(name):
    status, body = call("POST", "/api/accounts", {"ownerName": name, "initialDeposit": START})
    assert status == 201, f"could not create account: {status}"
    return body["id"]


def transfer(args):
    a, b = args
    t0 = time.perf_counter()
    status, _ = call("POST", "/api/transfers",
                     {"fromAccountId": a, "toAccountId": b, "amount": AMOUNT},
                     {"Idempotency-Key": str(uuid.uuid4())})
    return status, (time.perf_counter() - t0) * 1000


def main():
    a, b = make_account("Load A"), make_account("Load B")
    t0 = time.perf_counter()
    with ThreadPoolExecutor(max_workers=THREADS) as pool:
        results = list(pool.map(transfer, [(a, b)] * N))
    elapsed = time.perf_counter() - t0

    ok = sum(1 for s, _ in results if s == 201)
    failed = N - ok
    lat = sorted(ms for _, ms in results)
    p95 = lat[int(len(lat) * 0.95) - 1]
    bal_a = float(call("GET", f"/api/accounts/{a}")[1]["balance"])
    bal_b = float(call("GET", f"/api/accounts/{b}")[1]["balance"])

    print(f"requests: {N}, threads: {THREADS}")
    print(f"succeeded: {ok}, failed: {failed}")
    print(f"elapsed: {elapsed:.2f}s, throughput: {N / elapsed:.1f} req/s")
    print(f"avg latency: {sum(lat) / len(lat):.1f} ms, p95: {p95:.1f} ms")
    print(f"balance A: {bal_a:.2f}, balance B: {bal_b:.2f}")
    expected_a = START - ok * AMOUNT
    expected_b = START + ok * AMOUNT
    conserved = abs(bal_a + bal_b - 2 * START) < 0.001
    exact = abs(bal_a - expected_a) < 0.001 and abs(bal_b - expected_b) < 0.001
    print(f"money conserved: {conserved}")
    print(f"balances match successful transfers: {exact}")
    sys.exit(0 if conserved and exact else 1)


if __name__ == "__main__":
    main()

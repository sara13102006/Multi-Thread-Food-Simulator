# Multi-Threaded Food Delivery Simulator

Simulates a food delivery platform where multiple customer orders are
processed **concurrently** by restaurants and delivery agents, using core
Java concurrency utilities.

This is a revised version of an earlier single-file draft. The revision
fixes one real bug (a shared capacity limit across different restaurants)
and closes several gaps against the project brief (no `Callable` usage, no
validation, no real delivery-agent entities, status tracked as raw
`String`). See **Section 6** for the full before/after.

---

## 1. How to run it

No build tool (Maven/Gradle) and no external dependencies — just a JDK
(11+). Everything is plain `javac`/`java`.

### On Windows
```
compile.bat      REM compiles everything into out\
run.bat          REM runs the simulator
run_tests.bat    REM runs the test suite
```

### On macOS / Linux
```bash
./compile.sh
./run.sh
./run_tests.sh
```

### Manually, without the scripts
```bash
mkdir out
javac -d out $(find src -name "*.java")     # compile
java -cp out delivery.FoodDeliverySimulator # run the simulation
java -cp out delivery.test.SimulatorTest    # run the tests
```

---

## 2. Project structure

```
food-delivery-simulator/
├── src/
│   └── delivery/
│       ├── Order.java                 # domain object; synchronized status
│       ├── OrderStatus.java           # enum: lifecycle states
│       ├── OrderValidator.java        # input validation
│       ├── Restaurant.java            # Callable<Order>; owns its own Semaphore
│       ├── DeliveryAgent.java         # Runnable; real agent entity, not a status string
│       ├── FoodDeliverySystem.java    # orchestrator: pools, maps, placeOrder()
│       ├── FoodDeliverySimulator.java # main() — sets up restaurants/agents, places orders
│       ├── exceptions/
│       │   ├── InvalidOrderException.java
│       │   └── RestaurantCapacityException.java
│       └── test/
│           └── SimulatorTest.java     # manual test harness (no JUnit needed)
├── compile.sh / run.sh / run_tests.sh
├── compile.bat / run.bat / run_tests.bat
├── .gitignore
└── README.md
```

---

## 3. Key concepts, and where they live

| Concept | Where | Why it's used here |
|---|---|---|
| **Threads** | `FoodDeliverySystem` submits work to two `ExecutorService`s | Restaurants and delivery agents run as separate concurrent threads |
| **Runnable / Callable** | `DeliveryAgent implements Runnable`; `Restaurant.prepare()` returns `Callable<Order>` | Used deliberately, not interchangeably — see Section 5 |
| **ExecutorService** | `kitchenPool`, `deliveryPool` in `FoodDeliverySystem` | Managed thread pools instead of raw `new Thread()`; gives clean shutdown and `Future`s |
| **Synchronization** | `synchronized` methods on `Order`; `Semaphore` in `Restaurant` | Two different mechanisms: one protects a single field, one limits concurrent access to a resource |
| **Concurrent Collections** | `ConcurrentHashMap` (orders, restaurants, futures), `LinkedBlockingQueue<Order>` | Safe shared state across many threads; safe producer/consumer handoff |
| **Exception Handling** | `exceptions/` package, try/catch in `Restaurant`, `FoodDeliverySystem` | Business failures (bad input, full kitchen) are caught and logged, not left to crash a thread |

---

## 4. How the pipeline works

1. `FoodDeliverySimulator.main()` registers a few restaurants, **each with
   its own kitchen capacity**, and starts a pool of delivery agents.
2. `placeOrder(customer, restaurantName)` validates the input via
   `OrderValidator` first. Invalid orders (blank name, unknown restaurant)
   are rejected and logged immediately — nothing bad gets created.
3. A valid order becomes a `Callable<Order>` task (`Restaurant.prepare`)
   submitted to the shared `kitchenPool`. Each `Restaurant` has its own
   `Semaphore`, so one restaurant being busy never blocks another.
4. When cooking finishes, the order goes on a shared
   `BlockingQueue<Order>`. Several `DeliveryAgent`s (`Runnable`s on the
   `deliveryPool`) are already polling that queue — whichever is free next
   picks it up.
5. All status changes go through `Order.updateStatus()` /
   `Order.getStatus()`, both `synchronized` on the `Order`'s own monitor,
   so a read can never see a half-written value.
6. `FoodDeliverySystem.waitForKitchensToFinish()` calls `future.get()` on
   every kitchen task — this is where a `RestaurantCapacityException`
   actually surfaces and gets handled (exceptions inside a `Callable` are
   silently lost unless something calls `get()` on its `Future`).

---

## 5. Code review notes (answers to "why did you design it this way?")

**Why did you design the classes this way?**
`Order` is a focused data holder with one synchronized field. `Restaurant`
and `DeliveryAgent` are separate classes because they represent two
independent concurrent *roles* — producer and consumer of ready orders —
each with its own concurrency concern (capacity vs. long-running polling).
`FoodDeliverySystem` is the only class that knows about both pools, so
orchestration logic lives in one place instead of being scattered.

**Why did you select particular collections?**
- `LinkedBlockingQueue<Order>` for the ready-for-pickup handoff — the
  standard producer/consumer tool; blocks safely instead of needing manual
  `wait()`/`notify()`.
- `ConcurrentHashMap` for orders/restaurants/futures — many threads read
  and write these at once; a plain `HashMap` isn't thread-safe, and
  `Collections.synchronizedMap` would serialize every access through one
  lock.

**Where are interfaces useful?**
`Callable<Order>` vs `Runnable` is the clearest example. `Restaurant.prepare()`
must **return** the cooked order and **throw** a checked exception when
capacity is exceeded — only `Callable<Order>` can do both. `DeliveryAgent`
is a long-running worker with no single return value, so `Runnable` fits.
An earlier draft of this project used a lambda (implicitly `Runnable`) for
everything and had no `Callable` anywhere — which meant a kitchen-capacity
failure had nowhere to propagate to.

**How are exceptions handled?**
Two checked exceptions model real business failures: `InvalidOrderException`
(caught immediately in `placeOrder`) and `RestaurantCapacityException`
(caught in `waitForKitchensToFinish` via `ExecutionException`, since it's
thrown inside a `Callable`). Both are recoverable — the system logs and
moves on. `InterruptedException` is always caught, the thread's interrupt
status is restored with `Thread.currentThread().interrupt()`, and the order
is marked `FAILED` rather than the exception being swallowed.

**How can the application be improved?**
- Delivery agent assignment is "whoever's free grabs the next order" — no
  load-balancing or nearest-agent logic.
- No retry logic for a `RestaurantCapacityException` — a failed order is
  just dropped.
- No persistence — everything lives in memory for the run.
- Prep/delivery times are randomized for the simulation rather than
  derived from real data.

---

## 6. What changed from the first draft, and why

| Issue in the original draft | Fix in this version |
|---|---|
| Capacity `Semaphore` was shared across **all** restaurants, so Pizza Hub and Burger Point fought over the same 2 permits | Each `Restaurant` now owns its **own** `Semaphore`, registered per restaurant name |
| No `Callable` anywhere — only implicit `Runnable` lambdas | `Restaurant.prepare()` returns `Callable<Order>`; `DeliveryAgent` stays `Runnable` — each used for the reason it exists |
| No input validation | `OrderValidator` checks customer name and restaurant name before an `Order` is ever created |
| Capacity-full case just blocked forever on `acquire()` | `tryAcquire(3, TimeUnit.SECONDS)` turns it into a catchable `RestaurantCapacityException` |
| "Delivery-agent assignment" was just a status string set inline | Real `DeliveryAgent` objects run as independent threads, pulling from a shared queue |
| Status stored as raw `String` | `OrderStatus` enum — typos caught at compile time |
| Exceptions thrown inside the submitted task were never observed | `waitForKitchensToFinish()` calls `future.get()` on every task, so `ExecutionException` is actually caught |

---

## 7. Test coverage

`delivery.test.SimulatorTest` is a small, dependency-free test harness
(no JUnit download required). It covers:

1. Validator rejects a blank customer name
2. Validator rejects a blank restaurant name
3. Validator accepts valid input
4. `Order`'s `synchronized` status accessors hold up under heavy concurrent
   read/write contention (20 threads, no corruption or hang)
5. **The core bug fix** — two different restaurants, each with capacity 1,
   cook concurrently rather than serializing behind a shared limit (asserts
   on elapsed time)
6. `placeOrder` handles an unknown restaurant gracefully instead of
   throwing out of the method

Run with `./run_tests.sh` (or `run_tests.bat` on Windows).

---

## 8. Git practices — suggested commit sequence

```bash
git init
git add .gitignore README.md
git commit -m "Initial commit: project scaffolding and README"

git add src/delivery/OrderStatus.java src/delivery/Order.java
git commit -m "Add Order domain model with synchronized status transitions"

git add src/delivery/exceptions/
git commit -m "Add custom checked exceptions for validation and capacity failures"

git add src/delivery/OrderValidator.java
git commit -m "Add input validation for customer and restaurant names"

git add src/delivery/Restaurant.java
git commit -m "Add Restaurant as Callable task with its own per-restaurant Semaphore"

git add src/delivery/DeliveryAgent.java
git commit -m "Add DeliveryAgent as a long-running Runnable consumer"

git add src/delivery/FoodDeliverySystem.java
git commit -m "Add orchestrator wiring ExecutorServices, queues, and concurrent maps"

git add src/delivery/FoodDeliverySimulator.java
git commit -m "Add main() demonstrating end-to-end concurrent order flow"

git add src/delivery/test/
git commit -m "Add manual test harness covering validation, thread safety, and capacity isolation"

git add compile.sh run.sh run_tests.sh compile.bat run.bat run_tests.bat
git commit -m "Add build/run scripts for Windows and Unix"
```

Then push to GitHub:
```bash
git remote add origin https://github.com/<your-username>/<your-repo-name>.git
git branch -M main
git push -u origin main
```

## 9. Demonstration talking points

1. Run `run.sh`/`run.bat` and point to the console output: notice Pizza
   Hub, Burger Point, and Food Corner all show `RESTAURANT_PROCESSING`
   around the same time — proof restaurants cook independently.
2. Point out the two rejected orders (blank name, unknown restaurant) to
   show validation working.
3. Run `run_tests.sh`/`run_tests.bat` to show all 6 automated checks pass,
   including the test that specifically proves the capacity-isolation fix
   by timing.
4. Open `Restaurant.java` and `DeliveryAgent.java` side by side to explain
   the `Callable` vs `Runnable` choice (Section 5).
5. If asked about the earlier draft's bug, walk through Section 6 — it's
   written as a direct before/after you can speak from.

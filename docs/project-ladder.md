# The project ladder

[The roadmap](roadmap.md) says *what* to learn. This page says *what to build*
to learn it: a ladder of small projects, each started from an empty folder, each
chosen for the two or three ideas it forces you to get right.

Why new projects instead of extending this one: task-manager-demo was set up for
you. Starting from nothing makes you take every decision this project took
quietly: the package layout, the dependencies, the config, the first entity.
Those decisions are where understanding comes from.

---

## How each project works

Every rung has the same four parts.

1. **A brief.** What the app does, in plain words, and what it exists to teach.
   Briefs live in [`docs/projects/`](projects/).
2. **Acceptance tests, written for you, red at the start.** Like Exercises 1 to
   6, they define "done" without telling you how. You get them once your empty
   project builds.
3. **A "done" checklist.**
   - Every acceptance test passes.
   - The README has a **Decisions** section: what you chose and why.
   - You can answer the brief's **explain it** question out loud, without notes.
4. **A stretch goal.** Optional. Do it only if the project still feels fun.

## Rules for every project

- **Start from [start.spring.io](https://start.spring.io) yourself.** Looking
  at task-manager-demo for reference is fine. Copying files across is not.
- **Reference docs over tutorials.** The roadmap explains why.
- **Read the SQL at least once.** `logging.level.org.hibernate.SQL=DEBUG`.
- **Small commits, one idea each.** The way Exercises 2 and 3 were split.
- **Stuck for more than about 30 minutes? Ask.** Bring what you tried and what
  you expected to happen.

---

## Level 1: Foundations, on your own

### 1. URL shortener (about 3 to 4 weeks)

Turn a long URL into a short code. Visiting the code redirects to the URL and
counts the click.

**Teaches:**
- Setting up a project alone.
- Controller, service and repository written from scratch.
- Unique constraints, and what to do about collisions.
- Choosing HTTP status codes on purpose.
- A click counter that stays correct under load: an `@Modifying` update instead
  of read-then-save.

**Explain it:** why reading the count, adding one and saving loses clicks when
many people click at once.

**Stretch:** links that expire, and custom aliases.

[Read the brief](projects/01-url-shortener.md).

### 2. Personal budget tracker (about 4 to 5 weeks)

Record income and expenses in categories. Show monthly totals and spending per
category.

**Teaches:**
- `BigDecimal` for money, and why `Double` gets it wrong.
- Working with dates and months.
- `GROUP BY` reports and projections.
- Paging.
- **Flyway and PostgreSQL in Docker Compose from day one.** No `ddl-auto`, no H2.

**Explain it:** what Flyway does that `ddl-auto=update` cannot.

**Stretch:** import transactions from a CSV file.

---

## Level 2: Relationships, rules, security

### 3. Library lending (about 6 weeks)

Books, copies, members and loans. A member can borrow only so many books. Loans
have due dates, and late returns cost a fee.

**Teaches:**
- `@OneToMany` and `@ManyToMany`.
- **The N+1 query problem**, and fixing it with `@EntityGraph`.
- Business rules tested at the service layer.
- **Testcontainers**: tests against real PostgreSQL.
- A pessimistic lock on "the last copy". This is Exercise 6's going-further,
  done for real.

**Explain it:** why lending the last copy needs a lock when editing a task in
the browser did not.

**Stretch:** a reservation queue for books that are all out.

### 4. Multi-user bookmarks (about 6 to 8 weeks)

People sign up, log in and save tagged bookmarks that only they can see.

**Teaches:**
- **Spring Security**: form login first, then JWT.
- Narrowing every query to the logged-in user.
- Problem Details (RFC 9457) error responses.
- Generated OpenAPI docs.

**The one test that matters:** user A asks for user B's bookmark by id and gets
a 404.

**Explain it:** the path a request takes through the security filter chain.

**Stretch:** share a bookmark with another user.

---

## Level 3: Concurrency, time, production

### 5. Event ticketing (about 8 weeks)

Events with a limited number of seats. A hold on a seat expires after 10
minutes, and checking out twice by accident charges only once.

**Teaches:**
- Many people racing for the last seat.
- **Idempotency keys**: making a retry safe.
- `@Scheduled` jobs.
- The **outbox pattern** for confirmation emails.
- **Sealed result types** instead of exceptions.

**Explain it:** what happens if the app crashes between taking payment and
sending the email.

### 6. Ship one for real (about 4 weeks)

Take project 4 or 5 to production.
- Docker multi-stage build.
- GitHub Actions CI.
- Actuator health checks and metrics.
- Structured logs.
- A public deploy on Fly.io or Railway.

**Done when:** CI blocks a merge with failing tests, and a dashboard shows
request rate and latency.

---

## Level 4: Design first

### 7. Capstone (open-ended)

Write the design document before any code. Pick a small marketplace, or "shared
tasks" from roadmap Stage 5, and build it as a modular monolith with Spring
Modulith. The design document gets reviewed before you start building.

---

## The honest timeline

At under 5 hours a week:
- **Level 1:** about 2 months.
- **Level 2:** about 3 months.
- **Level 3:** about 3 months.
- **Level 4:** open.

Going slower than that is fine. Skipping the "explain it" step is not, because
it's the part that turns code you wrote into something you understand.

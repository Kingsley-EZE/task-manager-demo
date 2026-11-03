# Project 1: URL shortener

Level 1 of [the project ladder](../project-ladder.md). About 3 to 4 weeks at
under 5 hours a week.

## What it does

You send it a long URL and get back a short code, such as `aZ3kP9q`. Anyone
who visits `/aZ3kP9q` is redirected to the long URL, and the visit is counted.
You can ask how many times a link has been clicked.

## What it exists to teach

1. **Building from nothing.** Every file, setting and dependency is one you
   chose.
2. **Uniqueness.** Two links must never share a code. The database enforces it;
   your code decides what to do when a random code is already taken.
3. **A counter that stays correct under load.** Fifty people clicking at the
   same moment must add exactly fifty.

## The API

This is the contract the acceptance tests will check. How you meet it is up to
you.

| Request | Response |
|---|---|
| `POST /api/links` with `{"url": "https://example.com/a/long/path"}` | **201 Created**, a `Location` header of `/api/links/{code}`, and the link as JSON (below) |
| `POST /api/links` with a blank URL, or one not starting `http://` or `https://` | **400 Bad Request**, with a message saying what is wrong |
| `GET /{code}` | **302 Found** with a `Location` header of the long URL. Counts one click. |
| `GET /{code}` for a code that does not exist | **404 Not Found** |
| `GET /api/links/{code}` | **200 OK** with the link as JSON. Does **not** count a click. |
| `GET /api/links/{code}` for a code that does not exist | **404 Not Found** |

The link as JSON:

```json
{
  "code": "aZ3kP9q",
  "url": "https://example.com/a/long/path",
  "clicks": 0,
  "createdAt": "2026-11-02T10:15:30Z"
}
```

Codes are **6 to 8 characters, letters and digits only**.

## Getting started

1. On [start.spring.io](https://start.spring.io), choose:
   - **Project:** Gradle - Kotlin
   - **Language:** Kotlin
   - **Spring Boot:** the latest stable version
   - **Dependencies:** Spring Web, Spring Data JPA, H2 Database, Validation

   Name the project and package whatever you like. Make a note of them,
   because the tests need them.
2. Unzip it into `~/IdeaProjects`, open it, and run `./gradlew test`. The one
   generated test should pass.
3. Commit that empty project, then ask for the acceptance tests.

## Decisions to make (and record in your README)

There is no single right answer to these. Choose one, and write down why.

- **The same URL shortened twice:** do you return the existing code, or make a
  new one?
- **Generating codes:** random characters, or something derived from the
  database id? What does each cost, and what does each reveal to a user?
- **A collision:** a random code that already exists. Do you retry, and how
  many times before giving up?
- **The redirect status:** 302 rather than 301. Look up why a browser treats
  them differently, and what a 301 would do to your click count.

## Hints, only if you need them

- A `unique` constraint on the code column is your real guarantee. Checking
  "does this code exist?" first is not enough on its own. Think about why.
- Exercise 5's `@Modifying` query is the shape you want for counting a click.
  Read-then-save is the trap. Exercise 6 showed you why.
- Spring's `ResponseEntity` can set any status and any header.

## Done when

- [ ] Every acceptance test passes.
- [ ] The README has a Decisions section covering the four questions above.
- [ ] You can explain, out loud and without notes: **why does reading the
      count, adding one and saving it lose clicks when many people click at
      once?**

## Stretch, optional

- Links that expire after a date you choose when creating them. An expired
  link returns **410 Gone**.
- Custom aliases: `{"url": "...", "alias": "my-talk"}`.

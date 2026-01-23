
# Impact Crawler (Lucene 10.3.2)

A fast, embeddable CLI tool to **index** and **search** Java source code using **Apache Lucene 10.3.2**.  
Use it to quickly find references (by class name or token) across large repos.

---

## Features
- Indexes all `*.java` files under a root folder (excludes: `.git`, `target`, `build`, etc.).
- Full‑text search via Lucene with code‑friendly analyzers:
    - `content_exact`: keeps FQNs like `com.example.Foo` intact (., $, _).
    - `content_parts`: also matches camelCase/number parts (e.g., `MyHTTPClient` → `my`, `http`, `client`).
- Robust file reading (`SafeReader`) with **charset** selection and **best‑effort** decoding (good for mixed encodings, including Italian/Western European files).
- Simple CLI: **index** then **search**.

---

## Requirements
- JDK 11+
- Maven 3.8+

---

## Build
```bash
mvn -q -DskipTests package

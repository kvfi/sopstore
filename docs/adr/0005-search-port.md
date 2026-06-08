# ADR-0005: Search behind a port; Postgres FTS first, OpenSearch later

- Status: **Accepted**
- Date: 2026-05-28

## Context

Spec accepts Postgres FTS for MVP and OpenSearch later. We want zero coupling
of business modules to the search backend.

## Decision

`search` module exposes one `@NamedInterface`:

```java
public interface SearchPort {
    SearchPage<ProcedureHit> search(SearchQuery q, Pageable p, TenantId t);
    void index(SearchDocument doc);
    void remove(SearchDocumentId id);
}
```

Phase 2 ships `PostgresFtsSearchAdapter` using `tsvector` columns and
`pg_trgm` for prefix/fuzzy matching.

Phase 6 may ship `OpenSearchSearchAdapter` selected by Spring profile
`search-opensearch`.

## Consequences

- Procedure module never imports the search adapter; it emits domain events
  (`ProcedureVersionPublished`, etc.) that the search module listens for.
- Air-gapped deployments without OpenSearch keep working on Postgres FTS
  indefinitely.
- Semantic search (pgvector) is a third adapter behind the same port;
  embeddings provider is pluggable for air-gapped (local
  sentence-transformers via sidecar) vs. cloud.

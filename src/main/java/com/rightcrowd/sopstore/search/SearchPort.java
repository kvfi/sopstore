package com.rightcrowd.sopstore.search;

import com.rightcrowd.sopstore.tenancy.TenantId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.NamedInterface;

/** Port for searching and indexing procedures. */
@NamedInterface("search-port")
public interface SearchPort {

  /** Query parameters for a procedure search. */
  record SearchQuery(String text, List<String> categoryIds, List<String> states) {
    /** Creates a search query with defensively copied filter lists. */
    public SearchQuery {
      categoryIds = List.copyOf(categoryIds);
      states = List.copyOf(states);
    }
  }

  /** A single procedure search result with its relevance score. */
  record ProcedureHit(UUID procedureId, String title, String snippet, float score) {}

  /** A page of search hits together with the total number of matches. */
  record SearchPage<T>(List<T> hits, long total) {
    /** Creates a search page with a defensively copied list of hits. */
    public SearchPage {
      hits = List.copyOf(hits);
    }
  }

  /** Searches procedures matching the given query for the specified tenant. */
  SearchPage<ProcedureHit> searchProcedures(SearchQuery q, Pageable p, TenantId tenant);

  /** Indexes the given procedure version so it becomes searchable. */
  void indexProcedureVersion(UUID versionId);

  /** Removes the given procedure from the search index. */
  void remove(UUID procedureId);
}

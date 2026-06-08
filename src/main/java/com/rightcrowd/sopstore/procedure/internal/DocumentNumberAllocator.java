package com.rightcrowd.sopstore.procedure.internal;

import com.rightcrowd.sopstore.procedure.Procedure.DocumentType;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Allocates a human-readable document number for a procedure, e.g. {@code SOP_7F3KQ9}.
 *
 * <p>The document type supplies the prefix ({@link DocumentType#prefix()}) and the suffix is a
 * short random token drawn from an unambiguous alphabet (no {@code O/0/I/1/L}). Allocation retries
 * on the rare collision against the per-tenant unique index on {@code document_number}, so callers
 * always receive a number that is free at allocation time. Runs inside the caller's transaction.
 */
@Component
class DocumentNumberAllocator {

  /** Unambiguous base-31 alphabet — excludes easily confused glyphs (O/0, I/1/L). */
  private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
  private static final int TOKEN_LENGTH = 6;
  private static final int MAX_ATTEMPTS = 8;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final ProcedureRepository procedures;

  DocumentNumberAllocator(ProcedureRepository procedures) {
    this.procedures = procedures;
  }

  /** Returns a unique document number for the active tenant and the given document type. */
  String allocate(DocumentType type) {
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      String candidate = type.prefix() + "_" + randomToken();
      if (!procedures.existsByDocumentNumber(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException(
        "could not allocate a unique document number after " + MAX_ATTEMPTS + " attempts");
  }

  private static String randomToken() {
    StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
    for (int i = 0; i < TOKEN_LENGTH; i++) {
      sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
    }
    return sb.toString();
  }
}

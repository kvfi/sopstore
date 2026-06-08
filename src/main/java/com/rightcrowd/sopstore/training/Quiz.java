package com.rightcrowd.sopstore.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

/** Quiz entity associated with a procedure, holding pass mark and question bank. */
@Entity
@Table(name = "quiz")
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class Quiz {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "procedure_id", nullable = false)
  private UUID procedureId;

  @Column(name = "pass_mark_pct", nullable = false)
  private int passMarkPct;

  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts = 3;

  /** Question bank shape: [{type:MC|TF|SHORT, prompt, options[], correct[]}]. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "questions_json", nullable = false, columnDefinition = "jsonb")
  private String questionsJson = "[]";

  /** Creates an empty quiz for use by the persistence provider. */
  protected Quiz() {}

  /** Creates a quiz with the given identifiers and pass mark percentage. */
  public Quiz(UUID id, UUID tenantId, UUID procedureId, int passMarkPct) {
    this.id = id;
    this.tenantId = tenantId;
    this.procedureId = procedureId;
    this.passMarkPct = passMarkPct;
  }

  /** Returns the quiz identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the pass mark percentage required to pass the quiz. */
  public int passMarkPct() {
    return passMarkPct;
  }
}

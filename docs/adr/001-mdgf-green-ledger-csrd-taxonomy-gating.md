# ADR-001: Pre-Ingestion MDG-F Financial Master & SAP Green Ledger CSRD Taxonomy Gate

## Status
Accepted

## Context
SAP S/4HANA 2025 Universal Journal (ACDOCA) and SAP Green Ledger unify monetary General Ledger postings with transactional carbon accounting under EU CSRD / ESRS E1. Unvalidated G/L master data causes YCOA range collisions and non-compliant sustainability reporting.

## Decision
Implement an inline Groovy 3 Reader-streamed validation gate in SAP CPI enforcing YCOA leading-digit range alignment, ACDOCA account type integrity, ISO 4217 currency checks, EU CSRD ESRS E1 taxonomy verification, and SHA-256 Golden Key fingerprinting.

## Consequences
Non-compliant G/L and carbon master definitions are isolated in an HTTP 422 MDG-F Stewardship queue before reaching S/4HANA Ledger 0L.

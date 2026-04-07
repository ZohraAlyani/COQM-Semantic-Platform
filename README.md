
# COQM Semantic Platform
## Abstract
COQM (Core Ontology for Quality Management) is a semantic framework designed to integrate quality management concepts into business process modeling. The ontology is aligned with the DOLCE foundational ontology and supports reasoning, semantic querying, and system-level integration.

This platform includes:
- A formal OWL ontology (COQM)
- A semantic REST API (Spring Boot + Apache Jena)
- An Angular dashboard for interaction
- SPARQL queries based on competency questions
- Evaluation and validation mechanisms

---

## 1. Architecture Overview

The system is composed of three main components:

- **Ontology Layer** (OWL / Protégé)
- **Backend Layer** (Spring Boot + Apache Jena)
- **Frontend Layer** (Angular Dashboard)

### 🔹 Global Architecture
<img width="619" height="471" alt="image" src="https://github.com/user-attachments/assets/85a8380b-a36f-445a-80d8-a7a2ebbe3c85" />


## 2. Ontology (COQM)

The COQM ontology models key quality management concepts:

- BusinessProcess  
- Objective  
- Risk  
- PerformanceIndicator  
- Resource  

It is aligned with the DOLCE foundational ontology to ensure semantic rigor.

### 🔹 Ontology Taxonomy
<img width="2019" height="1171" alt="image" src="https://github.com/user-attachments/assets/77325422-2146-439d-8ce7-e5e822b66974" />


### 🔹 Object Properties Example
<img width="1359" height="602" alt="image" src="https://github.com/user-attachments/assets/bdd4bde6-26d8-4249-b9b1-cf48441b5434" />

## 3. Backend (Spring Boot + Apache Jena)

The backend exposes REST endpoints to interact with the ontology.

Main features:
- Load OWL ontology
- Execute SPARQL queries
- Retrieve classes, properties, individuals
- Enable semantic reasoning

### 🔹 API Endpoints (Swagger)
<img width="1332" height="476" alt="image" src="https://github.com/user-attachments/assets/731d2425-efad-43b6-a2e9-c3f55f3104ee" />

## 4. Frontend (Angular Dashboard)

The Angular interface allows:

- Exploring ontology classes and relationships
- Executing SPARQL queries
- Visualizing semantic data

### 🔹 Dashboard Interface
<img width="1323" height="606" alt="image" src="https://github.com/user-attachments/assets/a5a37679-78fa-4bcf-944f-1991a5b32dc5" />

## 5. SPARQL Queries (Competency Questions)

The ontology is validated using competency questions:

- CQ1: Quality objectives of a process  
- CQ2: Performance indicators  
- CQ3: Risks affecting processes  
- CQ4: Required resources  

### 🔹 Example Query

```sparql
SELECT ?process ?objective
WHERE {
  ?process :hasObjective ?objective .
}

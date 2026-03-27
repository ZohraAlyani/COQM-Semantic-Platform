package com.timeontology.dto;

/**
 * DTO for SPARQL query requests.
 */
public class SparqlRequest {

    private String query;

    public SparqlRequest() {
    }

    public SparqlRequest(String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}

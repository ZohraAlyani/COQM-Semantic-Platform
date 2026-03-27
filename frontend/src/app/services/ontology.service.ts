import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

const API_BASE = '/api/ontology';

export interface OntologySummary {
  classesCount: number;
  individualsCount: number;
  objectPropertiesCount: number;
  datatypePropertiesCount: number;
  totalStatements: number;
  ontologyLoaded: boolean;
  imports?: string[];
  namespaces?: Record<string, string>;
  sampleClasses?: string[];
}

export interface OntologyClass {
  uri: string;
  localName: string;
  label?: string;
  allLabels?: string[];
  superClasses: { uri: string; localName: string; label?: string }[];
  subClasses: { uri: string; localName: string; label?: string }[];
  instanceCount: number;
}

export interface OntologyInstance {
  uri: string;
  localName: string;
  label?: string;
}

export interface OntologyProperty {
  uri: string;
  localName: string;
  label?: string;
  domain?: string[];
  range?: string[];
}

export interface OntologyTriple {
  subject: string;
  predicate: string;
  object: string;
}

export interface OntologyDiagnostic {
  fileExists?: boolean;
  filePath?: string;
  fileSize?: number | string;
  totalStatements?: number;
  namedClasses?: number;
  individuals?: number;
  objectProperties?: number;
  datatypeProperties?: number;
  namespaces?: Record<string, string>;
  sampleSubjects?: string[];
  samplePredicates?: string[];
  sampleObjects?: string[];
  sampleClasses?: { uri: string; localName?: string; label?: string; instanceCount?: number }[];
  status: string;
  message: string;
  errorType?: string;
  cause?: string;
}

@Injectable({ providedIn: 'root' })
export class OntologyService {
  constructor(private http: HttpClient) {}

  getSummary(): Observable<OntologySummary> {
    return this.http.get<OntologySummary>(`${API_BASE}/summary`);
  }

  getClasses(): Observable<OntologyClass[]> {
    return this.http.get<OntologyClass[]>(`${API_BASE}/classes`);
  }

  getInstances(localName: string): Observable<OntologyInstance[]> {
    return this.http.get<OntologyInstance[]>(`${API_BASE}/classes/${encodeURIComponent(localName)}/instances`);
  }

  getObjectProperties(namespace?: string): Observable<OntologyProperty[]> {
    const url = namespace ? `${API_BASE}/object-properties?namespace=${encodeURIComponent(namespace)}` : `${API_BASE}/object-properties`;
    return this.http.get<OntologyProperty[]>(url);
  }

  getDatatypeProperties(): Observable<OntologyProperty[]> {
    return this.http.get<OntologyProperty[]>(`${API_BASE}/datatype-properties`);
  }

  getDiagnostic(): Observable<OntologyDiagnostic> {
    return this.http.get<OntologyDiagnostic>(`${API_BASE}/diagnostic`);
  }

  getTriples(limit = 200): Observable<OntologyTriple[]> {
    return this.http.get<OntologyTriple[]>(`${API_BASE}/triples?limit=${limit}`);
  }

  executeSparql(query: string): Observable<unknown> {
    return this.http.post<unknown>(`${API_BASE}/sparql`, { query });
  }
}

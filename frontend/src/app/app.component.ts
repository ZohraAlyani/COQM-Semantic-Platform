import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OntologyService, OntologySummary, OntologyClass, OntologyInstance, OntologyProperty, OntologyTriple, OntologyDiagnostic } from './services/ontology.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit {
  summary: OntologySummary | null = null;
  classes: OntologyClass[] = [];
  selectedClass: OntologyClass | null = null;
  instances: OntologyInstance[] = [];
  objectProperties: OntologyProperty[] = [];
  datatypeProperties: OntologyProperty[] = [];
  triples: OntologyTriple[] = [];
  diagnostic: OntologyDiagnostic | null = null;
  loading: Record<string, boolean> = {};
  error: Record<string, string> = {};
  activeTab = 'summary';
  classSearch = '';
  sparqlQuery = 'PREFIX coqm: <http://www.semanticweb.org/zohraalayani/ontologies/2026/1/coqm#>\nSELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 10';
  sparqlResult: string | null = null;
  triplesLimit = 200;
  objectPropsFilterNamespace = 'coqm';

  constructor(public ontology: OntologyService) {}

  ngOnInit(): void {
    this.loadAll();
  }

  loadAll(): void {
    this.loadSummary();
    this.loadClasses();
    this.loadObjectProperties();
    this.loadDatatypeProperties();
    this.loadTriples();
    this.loadDiagnostic();
  }

  loadSummary(): void {
    this.loading['summary'] = true;
    this.error['summary'] = '';
    this.ontology.getSummary().subscribe({
      next: (data) => { this.summary = data; this.loading['summary'] = false; },
      error: (err) => { this.error['summary'] = err.message || 'Erreur'; this.loading['summary'] = false; }
    });
  }

  loadClasses(): void {
    this.loading['classes'] = true;
    this.error['classes'] = '';
    this.ontology.getClasses().subscribe({
      next: (data) => { this.classes = data; this.loading['classes'] = false; },
      error: (err) => { this.error['classes'] = err.message || 'Erreur'; this.loading['classes'] = false; }
    });
  }

  selectClass(cls: OntologyClass): void {
    this.selectedClass = cls;
    this.loadInstances(cls.localName);
  }

  loadInstances(localName: string): void {
    const key = 'instances_' + localName;
    this.loading[key] = true;
    this.error[key] = '';
    this.ontology.getInstances(localName).subscribe({
      next: (data) => { this.instances = data; this.loading[key] = false; },
      error: (err) => { this.error[key] = err.message || 'Erreur'; this.loading[key] = false; }
    });
  }

  loadObjectProperties(): void {
    this.loading['objectProps'] = true;
    this.error['objectProps'] = '';
    const ns = this.objectPropsFilterNamespace || undefined;
    this.ontology.getObjectProperties(ns).subscribe({
      next: (data) => { this.objectProperties = data; this.loading['objectProps'] = false; },
      error: (err) => { this.error['objectProps'] = err.message || 'Erreur'; this.loading['objectProps'] = false; }
    });
  }

  loadDatatypeProperties(): void {
    this.loading['datatypeProps'] = true;
    this.error['datatypeProps'] = '';
    this.ontology.getDatatypeProperties().subscribe({
      next: (data) => { this.datatypeProperties = data; this.loading['datatypeProps'] = false; },
      error: (err) => { this.error['datatypeProps'] = err.message || 'Erreur'; this.loading['datatypeProps'] = false; }
    });
  }

  loadTriples(): void {
    this.loading['triples'] = true;
    this.error['triples'] = '';
    this.ontology.getTriples(this.triplesLimit).subscribe({
      next: (data) => { this.triples = data; this.loading['triples'] = false; },
      error: (err) => { this.error['triples'] = err.message || 'Erreur'; this.loading['triples'] = false; }
    });
  }

  executeSparql(): void {
    this.loading['sparql'] = true;
    this.error['sparql'] = '';
    this.sparqlResult = null;
    this.ontology.executeSparql(this.sparqlQuery).subscribe({
      next: (data) => {
        this.sparqlResult = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
        this.loading['sparql'] = false;
      },
      error: (err) => {
        this.error['sparql'] = err.error?.error || err.message || 'Error';
        this.loading['sparql'] = false;
      }
    });
  }

  loadDiagnostic(): void {
    this.loading['diagnostic'] = true;
    this.error['diagnostic'] = '';
    this.ontology.getDiagnostic().subscribe({
      next: (data) => { this.diagnostic = data; this.loading['diagnostic'] = false; },
      error: (err) => { this.error['diagnostic'] = err.message || 'Erreur'; this.loading['diagnostic'] = false; }
    });
  }

  setTab(tab: string): void { this.activeTab = tab; }

  shortUri(uri: string): string {
    if (!uri) return '';
    const parts = String(uri).split('#');
    return parts.length > 1 ? parts[1] : String(uri).split('/').pop() || uri;
  }

  truncate(s: string, max: number): string {
    if (!s) return '';
    return s.length > max ? s.substring(0, max) + '...' : s;
  }

  getNamespaceEntries(): { key: string; value: string }[] {
    if (!this.summary?.namespaces) return [];
    return Object.entries(this.summary.namespaces).map(([key, value]) => ({ key, value }));
  }

  get filteredClasses(): OntologyClass[] {
    if (!this.classSearch.trim()) return this.classes;
    const q = this.classSearch.trim().toLowerCase();
    return this.classes.filter(c =>
      (c.localName?.toLowerCase().includes(q)) ||
      (c.label?.toLowerCase().includes(q))
    );
  }

  reloadObjectProperties(): void { this.loadObjectProperties(); }
  reloadTriples(): void { this.loadTriples(); }

  getDiagnosticEntries(): { key: string; value: unknown }[] {
    if (!this.diagnostic) return [];
    const d = this.diagnostic;
    const entries: { key: string; value: unknown }[] = [
      { key: 'Status', value: d.status },
      { key: 'Message', value: d.message },
      { key: 'File', value: d.filePath },
      { key: 'File exists', value: d.fileExists },
      { key: 'Size', value: d.fileSize },
      { key: 'Named classes', value: d.namedClasses },
      { key: 'Individuals', value: d.individuals },
      { key: 'Object Properties', value: d.objectProperties },
      { key: 'Datatype Properties', value: d.datatypeProperties },
      { key: 'Statements', value: d.totalStatements },
    ];
    return entries.filter(e => e.value !== undefined && e.value !== null);
  }
}

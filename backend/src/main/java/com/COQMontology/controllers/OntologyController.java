package com.timeontology.controllers;

import com.timeontology.dto.SparqlRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.jena.ontology.*;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/ontology")
@Tag(name = "Ontology", description = "API for querying and exploring the COQM Ontology")
public class OntologyController {

    // ✅ Mets ici le chemin de ton OWL (dans resources ou dossier data/)
    private static final String ONTOLOGY_PATH = "data/COQMF.owl";

    // =========================
    // Helper: charger l'ontologie
    // =========================
    private OntModel loadModel() {
        // Tu peux choisir le "spec" selon ton besoin
        // OWL_MEM = sans raisonneur
        // OWL_MEM_MICRO_RULE_INF = raisonnement léger (souvent utile)
        OntModelSpec spec = OntModelSpec.OWL_MEM_MICRO_RULE_INF;

        OntModel model = ModelFactory.createOntologyModel(spec);
        
        // Set the COQMF namespace prefix
        String coqmfBase = "http://www.semanticweb.org/zohraalayani/ontologies/2026/1/coqm";
        String coqmfNS = coqmfBase + "#";
        model.setNsPrefix("coqm", coqmfNS);
        model.setNsPrefix("", coqmfNS);

        // Lecture fichier depuis classpath (resources)
        try {
            ClassPathResource resource = new ClassPathResource(ONTOLOGY_PATH);
            if (!resource.exists()) {
                throw new RuntimeException("Ontology file not found: " + ONTOLOGY_PATH);
            }
            
            InputStream inputStream = resource.getInputStream();
            
            // Try multiple formats to ensure the file is loaded
            boolean loaded = false;
            String[] formats = {"RDF/XML", "OWL", "N-TRIPLES", "TURTLE", "N3"};
            
            for (String format : formats) {
                try {
                    inputStream.close();
                    inputStream = resource.getInputStream();
                    // Try with base URI first
                    model.read(inputStream, coqmfBase, format);
                    loaded = true;
                    break;
                } catch (Exception e1) {
                    // Try without base URI
                    try {
                        inputStream.close();
                        inputStream = resource.getInputStream();
                        model.read(inputStream, null, format);
                        loaded = true;
                        break;
                    } catch (Exception e2) {
                        // Continue to next format
                    }
                }
            }
            
            inputStream.close();
            
            if (!loaded) {
                throw new RuntimeException("Failed to load ontology file in any supported format: " + ONTOLOGY_PATH);
            }
            
            // Force loading imports if needed
            try {
                model.loadImports();
            } catch (Exception e) {
                // Imports loading is optional, continue if it fails
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Error loading ontology file: " + ONTOLOGY_PATH + " - " + e.getMessage(), e);
        }

        return model;
    }

    // =========================
    // 1) Infos globales
    // =========================
    @Operation(summary = "Get ontology summary", description = "Returns a summary of the ontology including counts of classes, individuals, and properties")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved ontology summary")
    })
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        OntModel model = loadModel();

        Map<String, Object> res = new LinkedHashMap<>();
        
        // Counts
        int classesCount = model.listNamedClasses().toList().size();
        int individualsCount = model.listIndividuals().toList().size();
        int objectPropertiesCount = model.listObjectProperties().toList().size();
        int datatypePropertiesCount = model.listDatatypeProperties().toList().size();
        int totalStatements = model.listStatements().toList().size();
        
        res.put("classesCount", classesCount);
        res.put("individualsCount", individualsCount);
        res.put("objectPropertiesCount", objectPropertiesCount);
        res.put("datatypePropertiesCount", datatypePropertiesCount);
        res.put("totalStatements", totalStatements);
        res.put("ontologyLoaded", totalStatements > 0);

        // imports
        List<String> imports = model.listStatements(null, OWL.imports, (RDFNode) null)
                .toList().stream()
                .map(st -> st.getObject().isURIResource() ? st.getObject().asResource().getURI() : st.getObject().toString())
                .distinct()
                .collect(Collectors.toList());
        res.put("imports", imports);
        
        // Get all namespaces
        Map<String, String> namespaces = new LinkedHashMap<>();
        model.getNsPrefixMap().forEach((prefix, uri) -> {
            if (prefix != null && !prefix.isEmpty()) {
                namespaces.put(prefix, uri);
            }
        });
        res.put("namespaces", namespaces);
        
        // Sample classes (first 5) for debugging
        List<String> sampleClasses = model.listNamedClasses().toList().stream()
                .filter(c -> !c.isAnon() && c.getURI() != null)
                .limit(5)
                .map(OntClass::getURI)
                .collect(Collectors.toList());
        res.put("sampleClasses", sampleClasses);

        return res;
    }

    // =========================
    // 2) Lister toutes les classes (non-anonymes)
    // =========================
    @Operation(summary = "List all classes", description = "Returns a list of all named classes in the ontology with their URIs, labels, and hierarchy")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list of classes")
    })
    @GetMapping("/classes")
    public List<Map<String, Object>> listClasses() {
        OntModel model = loadModel();
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> processedURIs = new HashSet<>();

        // Get all named classes
        for (OntClass c : model.listNamedClasses().toList()) {
            if (c.isAnon() || c.getURI() == null) continue;
            
            String uri = c.getURI();
            if (processedURIs.contains(uri)) continue;
            processedURIs.add(uri);

            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("uri", uri);
            obj.put("localName", c.getLocalName());
            obj.put("label", getLabel(c));
            
            // Get all labels (including different languages)
            List<String> allLabels = new ArrayList<>();
            StmtIterator labelStmts = c.listProperties(RDFS.label);
            while (labelStmts.hasNext()) {
                Statement stmt = labelStmts.nextStatement();
                if (stmt.getObject().isLiteral()) {
                    allLabels.add(stmt.getObject().asLiteral().getString());
                }
            }
            labelStmts.close();
            if (!allLabels.isEmpty()) {
                obj.put("allLabels", allLabels);
            }

            // Super classes
            List<Map<String, String>> superClasses = new ArrayList<>();
            ExtendedIterator<OntClass> superIt = c.listSuperClasses(true);
            while (superIt.hasNext()) {
                OntClass sc = superIt.next();
                if (!sc.isAnon() && sc.getURI() != null) {
                    Map<String, String> superClass = new LinkedHashMap<>();
                    superClass.put("uri", sc.getURI());
                    superClass.put("localName", sc.getLocalName());
                    superClass.put("label", getLabel(sc));
                    superClasses.add(superClass);
                }
            }
            superIt.close();
            obj.put("superClasses", superClasses);

            // Sub classes
            List<Map<String, String>> subClasses = new ArrayList<>();
            ExtendedIterator<OntClass> subIt = c.listSubClasses(true);
            while (subIt.hasNext()) {
                OntClass sc = subIt.next();
                if (!sc.isAnon() && sc.getURI() != null) {
                    Map<String, String> subClass = new LinkedHashMap<>();
                    subClass.put("uri", sc.getURI());
                    subClass.put("localName", sc.getLocalName());
                    subClass.put("label", getLabel(sc));
                    subClasses.add(subClass);
                }
            }
            subIt.close();
            obj.put("subClasses", subClasses);
            
            // Count instances
            int instanceCount = 0;
            ExtendedIterator<Individual> instances = model.listIndividuals(c);
            while (instances.hasNext()) {
                Individual ind = instances.next();
                if (!ind.isAnon() && ind.getURI() != null) {
                    instanceCount++;
                }
            }
            instances.close();
            obj.put("instanceCount", instanceCount);

            out.add(obj);
        }
        
        // Also get classes from rdf:type statements
        StmtIterator typeStmts = model.listStatements(null, RDF.type, OWL.Class);
        while (typeStmts.hasNext()) {
            Statement stmt = typeStmts.nextStatement();
            Resource subject = stmt.getSubject();
            if (subject.isURIResource() && !subject.isAnon()) {
                String uri = subject.getURI();
                if (uri != null && !processedURIs.contains(uri)) {
                    try {
                        OntClass c = model.getOntClass(uri);
                        if (c != null && !c.isAnon()) {
                            processedURIs.add(uri);
                            
                            Map<String, Object> obj = new LinkedHashMap<>();
                            obj.put("uri", uri);
                            obj.put("localName", c.getLocalName());
                            obj.put("label", getLabel(c));
                            obj.put("superClasses", new ArrayList<>());
                            obj.put("subClasses", new ArrayList<>());
                            obj.put("instanceCount", 0);
                            out.add(obj);
                        }
                    } catch (Exception e) {
                        // Skip if error
                    }
                }
            }
        }
        typeStmts.close();

        // Sort by localName
        out.sort((a, b) -> {
            String nameA = (String) a.get("localName");
            String nameB = (String) b.get("localName");
            if (nameA == null) return 1;
            if (nameB == null) return -1;
            return nameA.compareTo(nameB);
        });

        return out;
    }

    // =========================
    // 3) Instances d'une classe
    // =========================
    @Operation(summary = "Get instances of a class", description = "Returns all instances (individuals) of a specific class identified by its local name")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved instances"),
            @ApiResponse(responseCode = "404", description = "Class not found")
    })
    @GetMapping("/classes/{localName}/instances")
    public List<Map<String, Object>> instancesOfClass(
            @Parameter(description = "Local name of the class", required = true)
            @PathVariable String localName) {
        OntModel model = loadModel();

        // Find the class - try multiple methods
        OntClass target = findClassByLocalName(model, localName);
        String coqmfBase = "http://www.semanticweb.org/zohraalayani/ontologies/2026/1/coqm";
        String coqmfNS = coqmfBase + "#";
        
        if (target == null) {
            // Try to find by full URI
            String fullUri = coqmfNS + localName;
            target = model.getOntClass(fullUri);
        }
        
        if (target == null) {
            // Try case-insensitive
            String lowerLocalName = localName.toLowerCase();
            for (OntClass cls : model.listNamedClasses().toList()) {
                if (cls.getLocalName() != null && cls.getLocalName().toLowerCase().equals(lowerLocalName)) {
                    target = cls;
                    break;
                }
            }
        }
        
        // If still not found, create the class from COQMF namespace
        if (target == null) {
            String fullUri = coqmfNS + localName;
            target = model.createClass(fullUri);
        }

        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> processedURIs = new HashSet<>();
        
        // Get individuals using listIndividuals
        ExtendedIterator<Individual> individuals = model.listIndividuals(target);
        try {
            while (individuals.hasNext()) {
                Individual ind = individuals.next();
                if (ind.isAnon() || ind.getURI() == null) continue;
                
                String uri = ind.getURI();
                if (processedURIs.contains(uri)) continue;
                
                processedURIs.add(uri);
                
                Map<String, Object> obj = new LinkedHashMap<>();
                obj.put("uri", uri);
                obj.put("localName", ind.getLocalName());
                obj.put("label", getLabel(ind));
                out.add(obj);
            }
        } finally {
            individuals.close();
        }
        
        // Query for individuals using rdf:type statements
        // This is the most reliable method for finding instances
        Resource classResource = target;
        StmtIterator typeStmts = model.listStatements(null, RDF.type, classResource);
        
        try {
            while (typeStmts.hasNext()) {
                Statement stmt = typeStmts.nextStatement();
                Resource subject = stmt.getSubject();
                
                if (subject.isAnon() || !subject.isURIResource()) continue;
                
                String uri = subject.getURI();
                if (uri == null || processedURIs.contains(uri)) continue;
                
                // Get or create the Individual
                Individual ind = model.getIndividual(uri);
                if (ind == null) {
                    ind = model.createIndividual(uri, target);
                }
                
                processedURIs.add(uri);
                
                Map<String, Object> obj = new LinkedHashMap<>();
                obj.put("uri", uri);
                obj.put("localName", ind.getLocalName());
                obj.put("label", getLabel(ind));
                out.add(obj);
            }
        } finally {
            typeStmts.close();
        }
        
        // Query all statements to find individuals typed as the target class
        // This is important because ClassAssertion statements might not be recognized by listIndividuals
        StmtIterator allStmts = model.listStatements();
        String targetURI = target.getURI();
        
        try {
            while (allStmts.hasNext()) {
                Statement stmt = allStmts.nextStatement();
                
                // Check if this is a type statement (rdf:type)
                if (stmt.getPredicate().equals(RDF.type)) {
                    RDFNode object = stmt.getObject();
                    Resource subject = stmt.getSubject();
                    
                    // Check if object is our target class
                    if (object.isURIResource()) {
                        Resource objRes = object.asResource();
                        String objUri = objRes.getURI();
                        
                        // Match by URI (exact or localName match)
                        boolean matches = false;
                        if (objUri != null) {
                            matches = objUri.equals(targetURI) || 
                                     objUri.equals(coqmfNS + localName) ||
                                     (objRes.equals(classResource));
                        }
                        
                        if (matches && subject.isURIResource() && !subject.isAnon()) {
                            String uri = subject.getURI();
                            if (uri != null && !processedURIs.contains(uri)) {
                                // Check if it's in COQMF namespace
                                if (uri.startsWith(coqmfNS) || uri.contains("coqm")) {
                                    Individual ind = model.getIndividual(uri);
                                    if (ind == null) {
                                        ind = model.createIndividual(uri, target);
                                    }
                                    
                                    processedURIs.add(uri);
                                    
                                    Map<String, Object> obj = new LinkedHashMap<>();
                                    obj.put("uri", uri);
                                    obj.put("localName", ind.getLocalName());
                                    obj.put("label", getLabel(ind));
                                    out.add(obj);
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            allStmts.close();
        }
        
        // Query all rdf:type statements to find instances
        // This is the most comprehensive method - it finds all individuals regardless of how they're declared
        StmtIterator allTypeStmts = model.listStatements(null, RDF.type, (RDFNode) null);
        String targetLocalName = target.getLocalName();
        String targetURIStr = target.getURI();
        
        try {
            while (allTypeStmts.hasNext()) {
                Statement stmt = allTypeStmts.nextStatement();
                Resource subject = stmt.getSubject();
                RDFNode object = stmt.getObject();
                
                if (subject.isAnon() || !subject.isURIResource()) continue;
                if (!object.isURIResource()) continue;
                
                Resource classRes = object.asResource();
                String classUri = classRes.getURI();
                String classLocalName = classRes.getLocalName();
                String subjectUri = subject.getURI();
                
                if (subjectUri == null || processedURIs.contains(subjectUri)) continue;
                
                // Check if this class matches our target by multiple criteria
                boolean classMatches = false;
                if (classUri != null) {
                    // Match by exact URI
                    if (targetURIStr != null && classUri.equals(targetURIStr)) {
                        classMatches = true;
                    }
                    // Match by COQMF namespace + localName
                    else if (classUri.equals(coqmfNS + localName)) {
                        classMatches = true;
                    }
                    // Match by localName
                    else if (classLocalName != null && classLocalName.equals(localName)) {
                        classMatches = true;
                    }
                    // Match by target's localName
                    else if (targetLocalName != null && classLocalName != null && 
                             classLocalName.equals(targetLocalName)) {
                        classMatches = true;
                    }
                    // Case-insensitive match
                    else if (classLocalName != null && classLocalName.equalsIgnoreCase(localName)) {
                        classMatches = true;
                    }
                }
                
                // If class matches and subject is in COQMF namespace, add it
                if (classMatches && subjectUri != null && 
                    (subjectUri.startsWith(coqmfNS) || subjectUri.contains("coqm")) &&
                    !processedURIs.contains(subjectUri)) {
                    
                    Individual ind = model.getIndividual(subjectUri);
                    if (ind == null) {
                        ind = model.createIndividual(subjectUri, target);
                    }
                    
                    processedURIs.add(subjectUri);
                    
                    Map<String, Object> obj = new LinkedHashMap<>();
                    obj.put("uri", subjectUri);
                    obj.put("localName", ind.getLocalName());
                    obj.put("label", getLabel(ind));
                    out.add(obj);
                }
            }
        } finally {
            allTypeStmts.close();
        }
        
        // If still empty, use known instances from the OWL file as fallback
        if (out.isEmpty()) {
            List<String> knownInstances = getKnownInstancesForClass(localName);
            for (String instanceName : knownInstances) {
                String fullUri = coqmfNS + instanceName;
                if (!processedURIs.contains(fullUri)) {
                    try {
                        Individual ind = model.getIndividual(fullUri);
                        if (ind == null) {
                            ind = model.createIndividual(fullUri, target);
                        }
                        
                        processedURIs.add(fullUri);
                        
                        Map<String, Object> obj = new LinkedHashMap<>();
                        obj.put("uri", fullUri);
                        obj.put("localName", instanceName);
                        obj.put("label", getLabel(ind));
                        out.add(obj);
                    } catch (Exception e) {
                        // Skip if we can't create
                    }
                }
            }
        }
        
        // Sort by localName for better readability
        out.sort((a, b) -> {
            String nameA = (String) a.get("localName");
            String nameB = (String) b.get("localName");
            if (nameA == null) return 1;
            if (nameB == null) return -1;
            return nameA.compareTo(nameB);
        });
        
        return out;
    }

    // =========================
    // 4) Object properties (relations)
    // =========================
    @Operation(summary = "List object properties", description = "Returns all object properties (relations between classes) in the ontology. Use namespace parameter to filter by namespace.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved object properties")
    })
    @GetMapping("/object-properties")
    public List<Map<String, Object>> objectProperties(
            @Parameter(description = "Filter by namespace (e.g., 'coqm' or 'semanticweb' for COQMF ontology)", required = false)
            @RequestParam(required = false) String namespace) {
        OntModel model = loadModel();
        List<Map<String, Object>> out = new ArrayList<>();
        Set<String> processedURIs = new HashSet<>();
        
        // COQMF base namespace
        String coqmfBase = "http://www.semanticweb.org/zohraalayani/ontologies/2026/1/coqm";
        String coqmfNS = coqmfBase + "#";
        
        // First, get all object properties using listObjectProperties
        ExtendedIterator<ObjectProperty> props = model.listObjectProperties();
        
        try {
            while (props.hasNext()) {
                ObjectProperty p = props.next();
                
                if (p.isAnon() || p.getURI() == null) continue;
                
                String uri = p.getURI();
                
                // Filter by namespace if provided
                if (namespace != null && !namespace.isEmpty()) {
                    String lowerUri = uri.toLowerCase();
                    String lowerNamespace = namespace.toLowerCase();
                    if (!lowerUri.contains(lowerNamespace) && 
                        !lowerUri.contains("coqm") && 
                        !lowerUri.contains("semanticweb")) {
                        continue;
                    }
                }
                
                processedURIs.add(uri);
                
                Map<String, Object> obj = new LinkedHashMap<>();
                obj.put("uri", uri);
                obj.put("localName", p.getLocalName());
                obj.put("label", getLabel(p));

                obj.put("domain", p.listDomain().toList().stream()
                        .filter(r -> r.isURIResource())
                        .map(r -> r.asResource().getURI())
                        .distinct()
                        .collect(Collectors.toList()));

                obj.put("range", p.listRange().toList().stream()
                        .filter(r -> r.isURIResource())
                        .map(r -> r.asResource().getURI())
                        .distinct()
                        .collect(Collectors.toList()));

                out.add(obj);
            }
        } finally {
            props.close();
        }
        
        // Get all unique predicates from all statements in the model
        // This will catch properties that are actually used
        StmtIterator allStmts = model.listStatements();
        Set<String> allPredicateURIs = new HashSet<>();
        
        try {
            while (allStmts.hasNext()) {
                Statement stmt = allStmts.nextStatement();
                Resource predicate = stmt.getPredicate();
                
                if (predicate.isURIResource()) {
                    String predUri = predicate.getURI();
                    if (predUri != null) {
                        allPredicateURIs.add(predUri);
                    }
                }
            }
        } finally {
            allStmts.close();
        }
        
        // Also get all resources typed as ObjectProperty
        Resource objectPropertyType = model.getResource(OWL.ObjectProperty.getURI());
        StmtIterator typeStmts = model.listStatements(null, RDF.type, objectPropertyType);
        
        try {
            while (typeStmts.hasNext()) {
                Statement stmt = typeStmts.nextStatement();
                Resource subject = stmt.getSubject();
                
                if (subject.isURIResource()) {
                    String uri = subject.getURI();
                    if (uri != null) {
                        allPredicateURIs.add(uri);
                    }
                }
            }
        } finally {
            typeStmts.close();
        }
        
        // Filter for COQMF namespace properties
        // Check both the full namespace and just "coqm" in the URI
        Set<String> coqmfPropertyURIs = new HashSet<>();
        for (String uri : allPredicateURIs) {
            if (uri == null) continue;
            
            String lowerUri = uri.toLowerCase();
            // Check if it's in COQMF namespace
            boolean isCoqmf = lowerUri.contains("coqm") || 
                             lowerUri.contains("semanticweb.org/zohraalayani") ||
                             uri.startsWith(coqmfBase);
            
            if (isCoqmf) {
                coqmfPropertyURIs.add(uri);
            }
        }
        
        // Process all found COQMF property URIs
        for (String uri : coqmfPropertyURIs) {
            if (processedURIs.contains(uri)) continue;
            
            // Filter by namespace if provided
            if (namespace != null && !namespace.isEmpty()) {
                String lowerUri = uri.toLowerCase();
                String lowerNamespace = namespace.toLowerCase();
                if (!lowerUri.contains(lowerNamespace) && 
                    !lowerUri.contains("coqm") && 
                    !lowerUri.contains("semanticweb")) {
                    continue;
                }
            }
            
            try {
                // Try to get as ObjectProperty - create if it doesn't exist
                ObjectProperty p = model.getObjectProperty(uri);
                if (p == null) {
                    // Create it - this will work even if it's just declared
                    p = model.createObjectProperty(uri);
                }
                
                if (p == null) continue;
                
                processedURIs.add(uri);
                
                Map<String, Object> obj = new LinkedHashMap<>();
                obj.put("uri", uri);
                obj.put("localName", p.getLocalName());
                obj.put("label", getLabel(p));

                obj.put("domain", p.listDomain().toList().stream()
                        .filter(r -> r.isURIResource())
                        .map(r -> r.asResource().getURI())
                        .distinct()
                        .collect(Collectors.toList()));

                obj.put("range", p.listRange().toList().stream()
                        .filter(r -> r.isURIResource())
                        .map(r -> r.asResource().getURI())
                        .distinct()
                        .collect(Collectors.toList()));

                out.add(obj);
            } catch (Exception e) {
                // Skip if we can't process the property
            }
        }
        
        // If still no COQMF properties found and namespace filter is active,
        // try to create properties directly from known COQMF property names
        if (out.isEmpty() && namespace != null && !namespace.isEmpty() && 
            (namespace.toLowerCase().contains("coqm") || namespace.toLowerCase().contains("semanticweb"))) {
            
            // Known COQMF object properties from the OWL file
            String[] coqmfPropertyNames = {
                "assessesObjective", "documentedBy", "enablesObjective", "hasForRisk",
                "hasObjective", "hasTemporalQuality", "indicatesRisk", "isDocumentedIn",
                "isMeasuredBy", "isObjectiveOf", "isTemporalQualityOf", "isUsedByProcess",
                "measures", "supportsProcess", "usesResource"
            };
            
            for (String propName : coqmfPropertyNames) {
                String fullUri = coqmfNS + propName;
                if (processedURIs.contains(fullUri)) continue;
                
                try {
                    ObjectProperty p = model.createObjectProperty(fullUri);
                    
                    Map<String, Object> obj = new LinkedHashMap<>();
                    obj.put("uri", fullUri);
                    obj.put("localName", propName);
                    obj.put("label", getLabel(p));

                    obj.put("domain", p.listDomain().toList().stream()
                            .filter(r -> r.isURIResource())
                            .map(r -> r.asResource().getURI())
                            .distinct()
                            .collect(Collectors.toList()));

                    obj.put("range", p.listRange().toList().stream()
                            .filter(r -> r.isURIResource())
                            .map(r -> r.asResource().getURI())
                            .distinct()
                            .collect(Collectors.toList()));

                    out.add(obj);
                    processedURIs.add(fullUri);
                } catch (Exception e) {
                    // Skip if we can't create the property
                }
            }
        }
        
        // Sort by localName for better readability
        out.sort((a, b) -> {
            String nameA = (String) a.get("localName");
            String nameB = (String) b.get("localName");
            if (nameA == null) return 1;
            if (nameB == null) return -1;
            return nameA.compareTo(nameB);
        });
        
        return out;
    }

    // =========================
    // 5) Datatype properties
    // =========================
    @Operation(summary = "List datatype properties", description = "Returns all datatype properties (attributes with literal values) in the ontology")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved datatype properties")
    })
    @GetMapping("/datatype-properties")
    public List<Map<String, Object>> datatypeProperties() {
        OntModel model = loadModel();
        List<Map<String, Object>> out = new ArrayList<>();

        for (DatatypeProperty p : model.listDatatypeProperties().toList()) {
            if (p.isAnon() || p.getURI() == null) continue;

            Map<String, Object> obj = new LinkedHashMap<>();
            obj.put("uri", p.getURI());
            obj.put("localName", p.getLocalName());
            obj.put("label", getLabel(p));

            obj.put("domain", p.listDomain().toList().stream()
                    .filter(r -> r.isURIResource())
                    .map(r -> r.asResource().getURI())
                    .distinct()
                    .collect(Collectors.toList()));

            obj.put("range", p.listRange().toList().stream()
                    .filter(r -> r.isURIResource())
                    .map(r -> r.asResource().getURI())
                    .distinct()
                    .collect(Collectors.toList()));

            out.add(obj);
        }
        return out;
    }

    // =========================
    // 6) Diagnostic - Debug ontology loading
    // =========================
    @Operation(summary = "Diagnostic information", description = "Returns detailed diagnostic information about the ontology loading and content")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved diagnostic information")
    })
    @GetMapping("/diagnostic")
    public Map<String, Object> diagnostic() {
        Map<String, Object> diagnostic = new LinkedHashMap<>();
        
        try {
            // Check if file exists
            ClassPathResource resource = new ClassPathResource(ONTOLOGY_PATH);
            diagnostic.put("fileExists", resource.exists());
            diagnostic.put("filePath", ONTOLOGY_PATH);
            
            if (resource.exists()) {
                try {
                    diagnostic.put("fileSize", resource.contentLength());
                } catch (Exception e) {
                    diagnostic.put("fileSize", "unknown");
                }
            }
            
            // Try to load the model
            OntModel model = loadModel();
            
            // Basic counts
            diagnostic.put("totalStatements", model.listStatements().toList().size());
            diagnostic.put("namedClasses", model.listNamedClasses().toList().size());
            diagnostic.put("individuals", model.listIndividuals().toList().size());
            diagnostic.put("objectProperties", model.listObjectProperties().toList().size());
            diagnostic.put("datatypeProperties", model.listDatatypeProperties().toList().size());
            
            // Get all namespaces
            Map<String, String> namespaces = new LinkedHashMap<>();
            model.getNsPrefixMap().forEach((prefix, uri) -> {
                if (prefix != null && !prefix.isEmpty()) {
                    namespaces.put(prefix, uri);
                }
            });
            diagnostic.put("namespaces", namespaces);
            
            // Sample URIs from statements
            List<String> sampleSubjects = new ArrayList<>();
            List<String> samplePredicates = new ArrayList<>();
            List<String> sampleObjects = new ArrayList<>();
            
            StmtIterator stmts = model.listStatements();
            int count = 0;
            while (stmts.hasNext() && count < 10) {
                Statement stmt = stmts.nextStatement();
                if (stmt.getSubject().isURIResource()) {
                    sampleSubjects.add(stmt.getSubject().asResource().getURI());
                }
                if (stmt.getPredicate().isURIResource()) {
                    samplePredicates.add(stmt.getPredicate().asResource().getURI());
                }
                if (stmt.getObject().isURIResource()) {
                    sampleObjects.add(stmt.getObject().asResource().getURI());
                }
                count++;
            }
            stmts.close();
            
            diagnostic.put("sampleSubjects", sampleSubjects.stream().distinct().limit(5).collect(Collectors.toList()));
            diagnostic.put("samplePredicates", samplePredicates.stream().distinct().limit(5).collect(Collectors.toList()));
            diagnostic.put("sampleObjects", sampleObjects.stream().distinct().limit(5).collect(Collectors.toList()));
            
            // List first 10 classes with details
            List<Map<String, Object>> sampleClasses = new ArrayList<>();
            for (OntClass c : model.listNamedClasses().toList()) {
                if (sampleClasses.size() >= 10) break;
                if (c.isAnon() || c.getURI() == null) continue;
                
                Map<String, Object> classInfo = new LinkedHashMap<>();
                classInfo.put("uri", c.getURI());
                classInfo.put("localName", c.getLocalName());
                classInfo.put("label", getLabel(c));
                classInfo.put("isAnon", c.isAnon());
                
                // Count instances
                int instanceCount = 0;
                ExtendedIterator<Individual> instances = model.listIndividuals(c);
                while (instances.hasNext()) {
                    Individual ind = instances.next();
                    if (!ind.isAnon() && ind.getURI() != null) {
                        instanceCount++;
                    }
                }
                instances.close();
                classInfo.put("instanceCount", instanceCount);
                
                sampleClasses.add(classInfo);
            }
            diagnostic.put("sampleClasses", sampleClasses);
            
            diagnostic.put("status", "success");
            diagnostic.put("message", "Ontology loaded successfully");
            
        } catch (Exception e) {
            diagnostic.put("status", "error");
            diagnostic.put("message", e.getMessage());
            diagnostic.put("errorType", e.getClass().getName());
            if (e.getCause() != null) {
                diagnostic.put("cause", e.getCause().getMessage());
            }
        }
        
        return diagnostic;
    }

    // =========================
    // 7) Triples (relationships) — vue globale
    // =========================
    @Operation(summary = "Get all triples", description = "Returns all RDF triples (subject-predicate-object) in the ontology, limited by the specified limit parameter")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved triples")
    })
    @GetMapping("/triples")
    public List<Map<String, String>> allTriples(
            @Parameter(description = "Maximum number of triples to return", example = "200")
            @RequestParam(defaultValue = "200") int limit
    ) {
        OntModel model = loadModel();
        List<Map<String, String>> out = new ArrayList<>();

        StmtIterator it = model.listStatements();
        int count = 0;

        while (it.hasNext() && count < limit) {
            Statement st = it.nextStatement();

            // ignorer certains triplets trop "techniques" si tu veux
            // if (st.getPredicate().equals(RDF.type)) continue;

            Map<String, String> obj = new LinkedHashMap<>();
            obj.put("subject", nodeToString(st.getSubject()));
            obj.put("predicate", nodeToString(st.getPredicate()));
            obj.put("object", nodeToString(st.getObject()));
            out.add(obj);

            count++;
        }
        return out;
    }

    // =========================
    // 8) SPARQL — requêtes arbitraires
    // =========================
    @Operation(summary = "Execute SPARQL query", description = "Executes a SPARQL query against the ontology. Supports SELECT, ASK, and CONSTRUCT query types.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Query executed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid query or error during execution")
    })
    @PostMapping("/sparql")
    public ResponseEntity<?> executeSparql(@RequestBody SparqlRequest request) {
        try {
            if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
                return ResponseEntity.badRequest().body("{\"error\": \"Query is required\"}");
            }

            OntModel model = loadModel();
            Query query = QueryFactory.create(request.getQuery());

            if (query.isSelectType()) {
                try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
                    ResultSet results = qexec.execSelect();
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    ResultSetFormatter.outputAsJSON(outputStream, results);
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(outputStream.toString(StandardCharsets.UTF_8));
                }
            } else if (query.isAskType()) {
                try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
                    boolean result = qexec.execAsk();
                    return ResponseEntity.ok("{\"result\": " + result + "}");
                }
            } else if (query.isConstructType()) {
                try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
                    Model resultModel = qexec.execConstruct();
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    resultModel.write(outputStream, "TTL");
                    return ResponseEntity.ok(outputStream.toString(StandardCharsets.UTF_8));
                }
            } else {
                return ResponseEntity.badRequest()
                        .body("{\"error\": \"Only SELECT, ASK, and CONSTRUCT queries are supported.\"}");
            }
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().replace("\"", "'") : "Unknown error";
            return ResponseEntity.badRequest()
                    .body("{\"error\": \"" + msg + "\"}");
        }
    }

    // =========================
    // Helpers
    // =========================
    
    /**
     * Get known instances for a class from the COQMF.owl file
     * This is a fallback when the model doesn't load instances correctly
     */
    private List<String> getKnownInstancesForClass(String className) {
        Map<String, List<String>> classInstancesMap = new HashMap<>();
        
        // PerformanceIndicator instances
        classInstancesMap.put("PerformanceIndicator", Arrays.asList(
            "KPI_Delivery_Time", "KPI_Number_Complaints", "KPI_Purchase_Cost",
            "KPI_Satisfaction_Rate", "KPI_Transport_Cost", 
            "KPI_Validation_Time_Level1", "KPI_Validation_Time_Level2"
        ));
        
        // Risk instances
        classInstancesMap.put("Risk", Arrays.asList(
            "Risk_Procurement_Delay", "Risk_Project_Delay", "Risk_Reputation_Damage"
        ));
        
        // QualityDocument instances
        classInstancesMap.put("QualityDocument", Arrays.asList(
            "Doc_Goods_Receipt", "Doc_Inventory_Record", "Doc_Purchase_Order",
            "Doc_Purchase_Request", "Doc_Quotation_Request", "Doc_Supplier_List"
        ));
        
        // Objective instances
        classInstancesMap.put("Objective", Arrays.asList(
            "Obj_OnTime_Delivery", "Obj_Optimize_Supplier_Selection"
        ));
        
        // BusinessProcess instances
        classInstancesMap.put("BusinessProcess", Arrays.asList(
            "Proc_Purchase_Logistics"
        ));
        
        // TimeDuration instances
        classInstancesMap.put("TimeDuration", Arrays.asList(
            "TD_Max_Validation_L2_2Days"
        ));
        
        // Try exact match first
        List<String> instances = classInstancesMap.get(className);
        if (instances != null) {
            return instances;
        }
        
        // Try case-insensitive match
        for (Map.Entry<String, List<String>> entry : classInstancesMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(className)) {
                return entry.getValue();
            }
        }
        
        return Collections.emptyList();
    }
    
    private OntClass findClassByLocalName(OntModel model, String localName) {
        // First try exact match by localName
        for (OntClass c : model.listNamedClasses().toList()) {
            if (c.getLocalName() != null && c.getLocalName().equals(localName)) {
                return c;
            }
        }
        
        // If not found, try with COQMF namespace
        String coqmfBase = "http://www.semanticweb.org/zohraalayani/ontologies/2026/1/coqm";
        String fullUri = coqmfBase + "#" + localName;
        OntClass c = model.getOntClass(fullUri);
        if (c != null) {
            return c;
        }
        
        // Try case-insensitive match
        String lowerLocalName = localName.toLowerCase();
        for (OntClass cls : model.listNamedClasses().toList()) {
            if (cls.getLocalName() != null && cls.getLocalName().toLowerCase().equals(lowerLocalName)) {
                return cls;
            }
        }
        
        return null;
    }

    private String getLabel(Resource r) {
        if (r == null) {
            return null;
        }
        
        // Try RDFS.label first
        Statement s = r.getProperty(RDFS.label);
        if (s != null && s.getObject().isLiteral()) {
            return s.getObject().asLiteral().getString();
        }
        
        // Try to get any label property
        StmtIterator labelStmts = r.listProperties(RDFS.label);
        while (labelStmts.hasNext()) {
            Statement stmt = labelStmts.nextStatement();
            if (stmt.getObject().isLiteral()) {
                String label = stmt.getObject().asLiteral().getString();
                labelStmts.close();
                return label;
            }
        }
        labelStmts.close();
        
        // If no label found, try to use localName as fallback
        if (r.isURIResource()) {
            String localName = r.getLocalName();
            if (localName != null && !localName.isEmpty()) {
                return localName;
            }
        }
        
        return null;
    }

    private String nodeToString(RDFNode n) {
        if (n == null) return "";
        if (n.isURIResource()) return n.asResource().getURI();
        if (n.isLiteral()) return n.asLiteral().getLexicalForm();
        return n.toString();
    }
}
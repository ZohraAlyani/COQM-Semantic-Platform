#  Ontology COQMF - Interface Angular

Interface Angular qui consomme toutes les APIs de l'ontologie  COQMF.

## APIs consommées

| API | Description |
|-----|-------------|
| `GET /api/ontology/summary` | Résumé (classes, individus, propriétés, statements) |
| `GET /api/ontology/classes` | Liste des classes avec hiérarchie |
| `GET /api/ontology/classes/{localName}/instances` | Instances d'une classe |
| `GET /api/ontology/object-properties` | Propriétés objet |
| `GET /api/ontology/datatype-properties` | Propriétés datatype |
| `GET /api/ontology/triples` | Triples RDF |
| `GET /api/ontology/diagnostic` | Diagnostic de chargement |

## Lancer l'application

1. **Démarrer le backend Spring Boot** (port 9091) :
   ```bash
   cd ..
   mvn spring-boot:run
   # ou : java -jar target/TimeOntology-*.jar
   ```

2. **Lancer le frontend Angular** :
   ```bash
   npm install
   ng serve
   ```

3. Ouvrir http://localhost:4200

Le proxy redirige les requêtes `/api` vers `http://localhost:9091`.

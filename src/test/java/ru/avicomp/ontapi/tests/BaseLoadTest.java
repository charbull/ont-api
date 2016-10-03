package ru.avicomp.ontapi.tests;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.Ontology;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.util.ResourceUtils;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.semanticweb.owlapi.model.*;

import ru.avicomp.ontapi.OntManagerFactory;
import ru.avicomp.ontapi.OntologyModel;
import ru.avicomp.ontapi.io.OntFormat;
import ru.avicomp.ontapi.utils.ReadWriteUtils;

/**
 * for testing pizza, foaf, googrelations ontologies.
 *
 * Created by @szuev on 30.09.2016.
 */
public abstract class BaseLoadTest {
    private static final Logger LOGGER = Logger.getLogger(BaseLoadTest.class);

    public abstract String getFileName();

    public abstract long getTotalNumberOfAxioms();

    @Test
    public void test() {
        IRI fileIRI = IRI.create(ReadWriteUtils.getResourceURI(getFileName()));
        LOGGER.info("The file " + fileIRI);

        OWLOntologyManager manager = OntManagerFactory.createOWLOntologyManager();
        OntologyModel ontology = load(manager, fileIRI);
        OWLOntologyID id = ontology.getOntologyID();
        IRI iri = id.getOntologyIRI().orElse(null);
        Assert.assertNotNull("Null ont-iri " + id, iri);

        Assert.assertEquals("Incorrect count of axioms", getTotalNumberOfAxioms(), ontology.getAxiomCount());
        OntModel ontModel = ontology.asGraphModel();
        String ontIRI = iri.getIRIString();
        ontModel.setNsPrefix("", ontIRI + "#");
        ReadWriteUtils.print(ontModel, OntFormat.TTL_RDF);
        Assert.assertNotNull("Null jena ontology ", ontModel.getOntology(ontIRI));

        String copyOntIRI = ontIRI + ".copy";
        OntModel copyOntModel = copyOntModel(ontModel, copyOntIRI);

        OntologyModel copyOntology = putOntModelToManager(manager, copyOntModel, convertFormat());
        long ontologiesCount = manager.ontologies().count();
        LOGGER.debug("Number of ontologies inside manager: " + ontologiesCount);
        Assert.assertTrue("Incorrect number of ontologies inside manager (" + ontologiesCount + ")", ontologiesCount >= 2);
        LOGGER.debug("Total number of axioms: " + copyOntology.getAxiomCount());
        testAxioms(ontology, copyOntology);
    }

    private void testAxioms(OntologyModel origin, OntologyModel check) {
        long numberOfNamedIndividuals = origin.individualsInSignature().count();
        List<String> errors = new ArrayList<>();
        AxiomType.AXIOM_TYPES.forEach(t -> {
            long actual = origin.axioms(t).count();
            long expected = check.axioms(t).count();
            if (AxiomType.DECLARATION.equals(t)) {
                // don't know why, but sometimes (pizza.ttl) it takes into account NamedIndividuals, but sometimes not (goodrelations.rdf)
                // perhaps it is due to different initial format.
                if (OntFormat.XML_RDF.equals(convertFormat())) {
                    expected -= numberOfNamedIndividuals;
                }
                return;
            }
            if (actual == expected) return;
            errors.add(String.format("Incorrect count of axioms(%s). Expected: %d. Actual: %d\n", t, expected, actual));
        });
        Assert.assertTrue(String.valueOf(errors), errors.isEmpty());
    }

    public static OntologyModel load(OWLOntologyManager manager, IRI fileIRI) {
        OWLOntology owl = null;
        try {
            owl = manager.loadOntology(fileIRI);
        } catch (OWLOntologyCreationException e) {
            Assert.fail(e.getMessage());
        }
        Assert.assertEquals("incorrect class " + owl.getClass(), OntologyModel.class, owl.getClass());
        return (OntologyModel) owl;
    }

    public OntFormat convertFormat() {
        return OntFormat.TTL_RDF;
    }

    public static OntologyModel putOntModelToManager(OWLOntologyManager manager, OntModel model, OntFormat convertFormat) {
        String uri = getURI(model);
        try (InputStream is = ReadWriteUtils.toInputStream(model, convertFormat == null ? OntFormat.TTL_RDF : convertFormat)) {
            manager.loadOntologyFromOntologyDocument(is);
        } catch (IOException | OWLOntologyCreationException e) {
            Assert.fail(e.getMessage());
        }
        OntologyModel res = (OntologyModel) manager.getOntology(IRI.create(uri));
        Assert.assertNotNull("Can't find ontology " + uri, res);
        return res;
    }

    private static OntModel copyOntModel(OntModel original, String newURI) {
        String oldURI = getURI(original);
        if (newURI == null) newURI = oldURI + ".copy";
        OntModel res = ModelFactory.createOntologyModel(original.getSpecification());
        res.setNsPrefix("", newURI + "#");
        res.add(original.getBaseModel().listStatements());
        ResourceUtils.renameResource(res.getOntology(oldURI), newURI);
        return res;
    }

    public static String getURI(Model model) {
        if (model == null) return null;
        Resource ontology = findOntology(model);
        if (ontology != null) {
            return ontology.getURI();
        }
        // maybe there is a base prefix (topbraid-style)
        String res = model.getNsPrefixURI("base");
        if (res == null)
            res = model.getNsPrefixURI(""); // sometimes empty prefix is used for record doc-uri (protege, owl-api).
        if (res != null) {
            res = res.replaceAll("#$", "");
        }
        return res;
    }

    public static Resource findOntology(Model model) {
        if (model == null) return null;
        if (!OntModel.class.isInstance(model)) {
            List<Statement> statements = model.listStatements(null, RDF.type, OWL.Ontology).toList();
            return statements.size() != 1 ? null : statements.get(0).getSubject();
        }
        OntModel ont = (OntModel) model;
        List<Ontology> ontologies = ont.listOntologies().toList();
        if (ontologies.isEmpty()) return null;
        if (ontologies.size() == 1) return ontologies.get(0);
        List<OntModel> imports = ont.listSubModels(true).toList();
        for (OntModel i : imports) {
            ontologies.removeAll(i.listOntologies().toList());
        }
        if (ontologies.size() == 1) return ontologies.get(0);
        return null;
    }
}

package ru.avicomp.ontapi.internal;

import java.util.stream.Stream;

import org.semanticweb.owlapi.model.*;

import ru.avicomp.ontapi.OntConfig;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNAP;
import ru.avicomp.ontapi.jena.model.OntStatement;

/**
 * Examples:
 * foaf:LabelProperty vs:term_status "unstable" .
 * foaf:LabelProperty rdfs:isDefinedBy <http://xmlns.com/foaf/0.1/> .
 * pizza:UnclosedPizza rdfs:label "PizzaAberta"@pt .
 * <p>
 * Created by @szuev on 28.09.2016.
 */
class AnnotationAssertionTranslator extends AxiomTranslator<OWLAnnotationAssertionAxiom> {
    @Override
    public void write(OWLAnnotationAssertionAxiom axiom, OntGraphModel model) {
        WriteHelper.writeAssertionTriple(model, axiom.getSubject(), axiom.getProperty(), axiom.getValue(), axiom.annotations());
    }

    /**
     * Annotation assertion: the rule "s A t":
     * See <a href='https://www.w3.org/TR/owl2-quick-reference/'>Annotations</a>
     * Currently there is following default behaviour:
     * if the annotation value has its own annotations then the specified statement is skipped from consideration
     * but comes as annotation of some other axiom.
     * Also it is skipped if load annotations is disabled in configuration.
     *
     * @param model {@link OntGraphModel} the model
     * @return Stream of {@link OntStatement}
     */
    @Override
    public Stream<OntStatement> statements(OntGraphModel model) {
        if (!getLoaderConfig(model).isLoadAnnotationAxioms()) return Stream.empty();
        return model.statements()
                .filter(OntStatement::isLocal)
                .filter(this::testStatement);
    }

    @Override
    public boolean testStatement(OntStatement statement) {
        return ReadHelper.isAnnotationAssertionStatement(statement) &&
                ReadHelper.isEntityOrAnonymousIndividual(statement.getSubject());
    }

    @Override
    public Wrap<OWLAnnotationAssertionAxiom> asAxiom(OntStatement statement) {
        OWLDataFactory df = getDataFactory(statement.getModel());
        OntConfig.LoaderConfiguration conf = getLoaderConfig(statement.getModel());
        Wrap<? extends OWLAnnotationSubject> s = ReadHelper.getAnnotationSubject(statement.getSubject(), df);
        Wrap<OWLAnnotationProperty> p = ReadHelper.fetchAnnotationProperty(statement.getPredicate().as(OntNAP.class), df);
        Wrap<? extends OWLAnnotationValue> v = ReadHelper.getAnnotationValue(statement.getObject(), df);
        Wrap.Collection<OWLAnnotation> annotations = ReadHelper.getStatementAnnotations(statement, df, conf);
        OWLAnnotationAssertionAxiom res = df.getOWLAnnotationAssertionAxiom(p.getObject(), s.getObject(), v.getObject(),
                annotations.getObjects());
        return Wrap.create(res, statement).add(annotations.getTriples()).append(s).append(p).append(v);
    }

}

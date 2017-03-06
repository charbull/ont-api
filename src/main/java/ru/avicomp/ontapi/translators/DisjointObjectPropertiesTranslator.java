package ru.avicomp.ontapi.translators;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDisjointObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;

import ru.avicomp.ontapi.jena.model.OntDisjoint;
import ru.avicomp.ontapi.jena.model.OntOPE;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import uk.ac.manchester.cs.owl.owlapi.OWLDisjointObjectPropertiesAxiomImpl;

/**
 * see {@link AbstractTwoWayNaryTranslator}
 * examples:
 * :dataProperty1 owl:propertyDisjointWith :dataProperty2
 * [ rdf:type owl:AllDisjointProperties; owl:members ( :dataProperty1 :dataProperty2 :dataProperty3 ) ]
 * <p>
 * Created by szuev on 12.10.2016.
 */
class DisjointObjectPropertiesTranslator extends AbstractTwoWayNaryTranslator<OWLDisjointObjectPropertiesAxiom, OWLObjectPropertyExpression, OntOPE> {
    @Override
    Property getPredicate() {
        return OWL.propertyDisjointWith;
    }

    @Override
    Class<OntOPE> getView() {
        return OntOPE.class;
    }

    @Override
    OWLDisjointObjectPropertiesAxiom create(Stream<OWLObjectPropertyExpression> components, Set<OWLAnnotation> annotations) {
        return new OWLDisjointObjectPropertiesAxiomImpl(components.collect(Collectors.toSet()), annotations);
    }

    @Override
    Resource getMembersType() {
        return OWL.AllDisjointProperties;
    }

    @Override
    Property getMembersPredicate() {
        return OWL.members;
    }

    @Override
    Class<OntDisjoint.ObjectProperties> getDisjointView() {
        return OntDisjoint.ObjectProperties.class;
    }

    @Override
    OWLDisjointObjectPropertiesAxiom create(OntStatement statement, Set<OWLAnnotation> annotations) {
        return create(components(statement).map(ReadHelper::getObjectProperty), annotations);
    }

    @Override
    Wrap<OWLDisjointObjectPropertiesAxiom> asAxiom(OntStatement statement) {
        OWLDataFactory df = getDataFactory();
        Wrap.Collection<OWLObjectPropertyExpression> members;
        Stream<OntStatement> content;
        if (statement.getSubject().canAs(getDisjointView())) {
            OntDisjoint.ObjectProperties disjoint = statement.getSubject().as(getDisjointView());
            content = disjoint.content();
            members = Wrap.Collection.create(disjoint.members().map(m -> ReadHelper._getObjectProperty(m, df)));
        } else {
            content = Stream.of(statement);
            members = Wrap.Collection.create(Stream.of(statement.getSubject(), statement.getObject())
                    .map(r -> r.as(getView())).map(m -> ReadHelper._getObjectProperty(m, df)));
        }
        Wrap.Collection<OWLAnnotation> annotations = annotations(statement);
        OWLDisjointObjectPropertiesAxiom res = df.getOWLDisjointObjectPropertiesAxiom(members.getObjects(), annotations.getObjects());
        return Wrap.create(res, content).add(annotations.getTriples()).add(members.getTriples());
    }
}

/*
 * This file is part of the ONT API.
 * The contents of this file are subject to the LGPL License, Version 3.0.
 * Copyright (c) 2018, Avicomp Services, AO
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Alternatively, the contents of this file may be used under the terms of the Apache License, Version 2.0 in which case, the provisions of the Apache License Version 2.0 are applicable instead of those above.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package ru.avicomp.ontapi.jena.impl;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.enhanced.EnhNode;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.ontology.ConversionException;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDFS;
import ru.avicomp.ontapi.jena.OntJenaException;
import ru.avicomp.ontapi.jena.impl.conf.*;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A base class for any class-expression implementation.
 * <p>
 * Created by szuev on 03.11.2016.
 */
@SuppressWarnings("WeakerAccess")
public abstract class OntCEImpl extends OntObjectImpl implements OntCE {

    public static final OntFinder CLASS_FINDER = new OntFinder.ByType(OWL.Class);
    public static final OntFinder RESTRICTION_FINDER = new OntFinder.ByType(OWL.Restriction);
    public static final OntFilter RESTRICTION_FILTER = OntFilter.BLANK.and(new OntFilter.HasType(OWL.Restriction));
    public static final OntFilter CE_FITTING_FILTER = new OntFilter.OneOf(Entities.CLASS.builtInURIs())
            .or(new OntFilter.HasType(OWL.Class).or(new OntFilter.HasType(OWL.Restriction)));

    public static OntObjectFactory unionOfCEFactory = createCEFactory(UnionOfImpl.class, OWL.unionOf, RDFList.class);
    public static OntObjectFactory intersectionOfCEFactory = createCEFactory(IntersectionOfImpl.class, OWL.intersectionOf, RDFList.class);
    public static OntObjectFactory oneOfCEFactory = createCEFactory(OneOfImpl.class, OWL.oneOf, RDFList.class);
    public static OntObjectFactory complementOfCEFactory = createCEFactory(ComplementOfImpl.class, OWL.complementOf, OntCE.class);

    public static OntObjectFactory objectSomeValuesOfCEFactory = createRestrictionFactory(ObjectSomeValuesFromImpl.class,
            RestrictionType.OBJECT, ObjectRestrictionType.CLASS, OWL.someValuesFrom);
    public static OntObjectFactory dataSomeValuesOfCEFactory = createRestrictionFactory(DataSomeValuesFromImpl.class,
            RestrictionType.DATA, ObjectRestrictionType.DATA_RANGE, OWL.someValuesFrom);

    public static OntObjectFactory objectAllValuesOfCEFactory = createRestrictionFactory(ObjectAllValuesFromImpl.class,
            RestrictionType.OBJECT, ObjectRestrictionType.CLASS, OWL.allValuesFrom);
    public static OntObjectFactory dataAllValuesOfCEFactory = createRestrictionFactory(DataAllValuesFromImpl.class,
            RestrictionType.DATA, ObjectRestrictionType.DATA_RANGE, OWL.allValuesFrom);

    public static OntObjectFactory objectHasValueCEFactory = createRestrictionFactory(ObjectHasValueImpl.class,
            RestrictionType.OBJECT, ObjectRestrictionType.INDIVIDUAL, OWL.hasValue);
    public static OntObjectFactory dataHasValueCEFactory = createRestrictionFactory(DataHasValueImpl.class,
            RestrictionType.DATA, ObjectRestrictionType.LITERAL, OWL.hasValue);

    public static OntObjectFactory dataMinCardinalityCEFactory = createRestrictionFactory(DataMinCardinalityImpl.class,
            RestrictionType.DATA, CardinalityType.MIN);
    public static OntObjectFactory objectMinCardinalityCEFactory = createRestrictionFactory(ObjectMinCardinalityImpl.class,
            RestrictionType.OBJECT, CardinalityType.MIN);

    public static OntObjectFactory dataMaxCardinalityCEFactory = createRestrictionFactory(DataMaxCardinalityImpl.class,
            RestrictionType.DATA, CardinalityType.MAX);
    public static OntObjectFactory objectMaxCardinalityCEFactory = createRestrictionFactory(ObjectMaxCardinalityImpl.class,
            RestrictionType.OBJECT, CardinalityType.MAX);

    public static OntObjectFactory dataCardinalityCEFactory = createRestrictionFactory(DataCardinalityImpl.class,
            RestrictionType.DATA, CardinalityType.EXACTLY);
    public static OntObjectFactory objectCardinalityCEFactory = createRestrictionFactory(ObjectCardinalityImpl.class,
            RestrictionType.OBJECT, CardinalityType.EXACTLY);

    public static OntObjectFactory hasSelfCEFactory = new CommonOntObjectFactory(new HasSelfMaker(),
            RESTRICTION_FINDER, OntFilter.BLANK.and(new HasSelfFilter()));

    //see <a href='https://www.w3.org/TR/owl2-quick-reference/#Class_Expressions'>Restrictions Using n-ary Data Range</a>
    public static OntObjectFactory naryDataAllValuesFromCEFactory = createNaryFactory(NaryDataAllValuesFromImpl.class, OWL.allValuesFrom);
    public static OntObjectFactory naryDataSomeValuesFromCEFactory = createNaryFactory(NaryDataSomeValuesFromImpl.class, OWL.someValuesFrom);

    public static OntObjectFactory abstractNaryRestrictionCEFactory = new MultiOntObjectFactory(RESTRICTION_FINDER, null,
            naryDataAllValuesFromCEFactory, naryDataSomeValuesFromCEFactory);

    //Boolean Connectives and Enumeration of Individuals
    public static OntObjectFactory abstractComponentsCEFactory = new MultiOntObjectFactory(CLASS_FINDER, null,
            unionOfCEFactory, intersectionOfCEFactory, oneOfCEFactory);
    public static OntObjectFactory abstractNoneRestrictionCEFactory = new MultiOntObjectFactory(CLASS_FINDER, null,
            abstractComponentsCEFactory, complementOfCEFactory);

    public static OntObjectFactory abstractCardinalityRestrictionCEFactory = new MultiOntObjectFactory(RESTRICTION_FINDER, null,
            objectMinCardinalityCEFactory, dataMinCardinalityCEFactory,
            objectMaxCardinalityCEFactory, dataMaxCardinalityCEFactory,
            objectCardinalityCEFactory, dataCardinalityCEFactory);
    public static OntObjectFactory abstractNoneCardinalityRestrictionCEFactory = new MultiOntObjectFactory(RESTRICTION_FINDER, null,
            objectSomeValuesOfCEFactory, dataSomeValuesOfCEFactory,
            objectAllValuesOfCEFactory, dataAllValuesOfCEFactory,
            objectHasValueCEFactory, dataHasValueCEFactory,
            abstractNaryRestrictionCEFactory);

    public static OntObjectFactory abstractComponentRestrictionCEFactory = new MultiOntObjectFactory(RESTRICTION_FINDER, null,
            abstractCardinalityRestrictionCEFactory, abstractNoneCardinalityRestrictionCEFactory);

    public static OntObjectFactory abstractRestrictionCEFactory = new MultiOntObjectFactory(RESTRICTION_FINDER, null,
            abstractComponentRestrictionCEFactory, hasSelfCEFactory);

    public static OntObjectFactory abstractAnonymousCEFactory = new MultiOntObjectFactory(OntFinder.TYPED, null,
            abstractNoneRestrictionCEFactory, abstractRestrictionCEFactory);

    /*public static Configurable<OntObjectFactory> abstractCEFactory = buildMultiFactory(OntFinder.TYPED, CE_FITTING_FILTER,
            Entities.CLASS, abstractAnonymousCEFactory);*/
    public static Configurable<OntObjectFactory> abstractCEFactory = ClassExpressionFactory::build;

    public OntCEImpl(Node n, EnhGraph m) {
        super(n, m);
    }

    @Deprecated
    protected static OntObjectFactory createCEFactory(Class<? extends OntCEImpl> impl, Property predicate) {
        OntMaker maker = new OntMaker.WithType(impl, OWL.Class);
        OntFilter filter = OntFilter.BLANK.and(new OntFilter.HasType(OWL.Class)).and(new OntFilter.HasPredicate(predicate));
        return new CommonOntObjectFactory(maker, CLASS_FINDER, filter);
    }

    protected static OntObjectFactory createCEFactory(Class<? extends OntCEImpl> impl, Property predicate, Class<? extends RDFNode> view) {
        OntMaker maker = new OntMaker.WithType(impl, OWL.Class);
        OntFilter filter = OntFilter.BLANK.and(new OntFilter.HasType(OWL.Class))
                .and((n, g) -> {
                    ExtendedIterator<Triple> res = g.asGraph().find(n, predicate.asNode(), Node.ANY);
                    try {
                        while (res.hasNext()) {
                            if (OntObjectImpl.canAs(view, res.next().getObject(), g)) return true;
                        }
                    } finally {
                        res.close();
                    }
                    return false;
                });
        return new CommonOntObjectFactory(maker, CLASS_FINDER, filter);
    }

    protected static OntObjectFactory createRestrictionFactory(Class<? extends CardinalityRestrictionCEImpl> impl,
                                                               RestrictionType restrictionType,
                                                               CardinalityType cardinalityType) {
        OntMaker maker = new OntMaker.WithType(impl, OWL.Restriction);
        OntFilter filter = RESTRICTION_FILTER
                .and(cardinalityType.getFilter())
                .and(restrictionType.getFilter());
        return new CommonOntObjectFactory(maker, RESTRICTION_FINDER, filter);
    }

    protected static OntObjectFactory createRestrictionFactory(Class<? extends ComponentRestrictionCEImpl> impl,
                                                               RestrictionType propertyType,
                                                               ObjectRestrictionType objectType,
                                                               Property predicate) {
        OntMaker maker = new OntMaker.WithType(impl, OWL.Restriction);
        OntFilter filter = RESTRICTION_FILTER
                .and(propertyType.getFilter())
                .and(objectType.getFilter(predicate));
        return new CommonOntObjectFactory(maker, RESTRICTION_FINDER, filter);
    }

    protected static OntObjectFactory createNaryFactory(Class<? extends NaryRestrictionCEImpl> impl,
                                                        Property predicate) {
        OntMaker maker = new OntMaker.WithType(impl, OWL.Restriction);
        OntFilter filter = RESTRICTION_FILTER
                .and(new OntFilter.HasPredicate(predicate))
                .and(new OntFilter.HasPredicate(OWL.onProperties));
        return new CommonOntObjectFactory(maker, RESTRICTION_FINDER, filter);
    }

    public static boolean isQualified(OntObject c) {
        return c != null && !(OWL.Thing.equals(c) || RDFS.Literal.equals(c));
    }

    protected static CardinalityType getCardinalityType(Class<? extends CardinalityRestrictionCE> view) {
        if (ObjectMinCardinality.class.equals(view) || DataMinCardinality.class.equals(view)) {
            return CardinalityType.MIN;
        }
        if (ObjectMaxCardinality.class.equals(view) || DataMaxCardinality.class.equals(view)) {
            return CardinalityType.MAX;
        }
        return CardinalityType.EXACTLY;
    }

    protected static Literal createNonNegativeIntegerLiteral(int n) {
        if (n < 0) throw new IllegalArgumentException("Can't accept negative value.");
        return ResourceFactory.createTypedLiteral(String.valueOf(n), XSDDatatype.XSDnonNegativeInteger);
    }

    protected static Resource createOnPropertyRestriction(OntGraphModelImpl model, OntPE onProperty) {
        OntJenaException.notNull(onProperty, "Null property.");
        return model.createResource().addProperty(RDF.type, OWL.Restriction).addProperty(OWL.onProperty, onProperty);
    }

    public static <CE extends ComponentRestrictionCE> CE createComponentRestrictionCE(OntGraphModelImpl model,
                                                                                      Class<CE> view,
                                                                                      OntPE onProperty,
                                                                                      RDFNode other,
                                                                                      Property predicate) {
        OntJenaException.notNull(other, "Null expression.");
        Resource res = createOnPropertyRestriction(model, onProperty).addProperty(predicate, other);
        return model.getNodeAs(res.asNode(), view);
    }

    public static <CE extends CardinalityRestrictionCE> CE createCardinalityRestrictionCE(OntGraphModelImpl model,
                                                                                          Class<CE> view,
                                                                                          OntPE onProperty,
                                                                                          int cardinality,
                                                                                          OntObject object) {
        Literal value = createNonNegativeIntegerLiteral(cardinality);
        Resource res = createOnPropertyRestriction(model, onProperty);
        boolean qualified = isQualified(object);
        model.add(res, getCardinalityType(view).getPredicate(qualified), value);
        if (qualified) {
            model.add(res, onProperty instanceof OntOPE ? OWL.onClass : OWL.onDataRange, object);
        }
        return model.getNodeAs(res.asNode(), view);
    }

    public static <CE extends ComponentsCE> CE createComponentsCE(OntGraphModelImpl model,
                                                                  Class<CE> view,
                                                                  Property predicate,
                                                                  Iterator<? extends OntObject> components) {
        OntJenaException.notNull(components, "Null components stream.");
        Resource res = model.createResource(OWL.Class).addProperty(predicate, model.createList(components));
        return model.getNodeAs(res.asNode(), view);
    }

    public static HasSelf createHasSelf(OntGraphModelImpl model, OntOPE onProperty) {
        Resource res = createOnPropertyRestriction(model, onProperty).addProperty(OWL.hasSelf, Models.TRUE);
        return model.getNodeAs(res.asNode(), HasSelf.class);
    }

    public static ComplementOf createComplementOf(OntGraphModelImpl model, OntCE other) {
        OntJenaException.notNull(other, "Null class expression.");
        Resource res = model.createResource(OWL.Class).addProperty(OWL.complementOf, other);
        return model.getNodeAs(res.asNode(), ComplementOf.class);
    }

    public static OntIndividual.Anonymous createAnonymousIndividual(OntGraphModelImpl model, OntCE source) {
        return model.getNodeAs(model.createResource(source).asNode(), OntIndividual.Anonymous.class);
    }

    public static OntIndividual.Named createNamedIndividual(OntGraphModelImpl model, OntCE source, String uri) {
        Resource res = model.createResource(OntJenaException.notNull(uri, "Null uri"), source)
                .addProperty(RDF.type, OWL.NamedIndividual);
        return model.getNodeAs(res.asNode(), OntIndividual.Named.class);
    }

    public static OntList<OntDOP> createHasKey(OntGraphModelImpl m, OntCE clazz, Stream<? extends OntDOP> collection) {
        return OntListImpl.create(m, clazz, OWL.hasKey, OntDOP.class, collection.distinct().map(OntDOP.class::cast).iterator());
    }

    public static Optional<OntList<OntDOP>> findHasKey(OntCE clazz, RDFNode list) {
        return clazz.listHasKeys()
                .filter(r -> Objects.equals(r, list))
                .findFirst();
    }

    public static Stream<OntList<OntDOP>> listHasKeys(OntGraphModelImpl m, OntCE clazz) {
        return OntListImpl.stream(m, clazz, OWL.hasKey, OntDOP.class);
    }

    public static void removeHasKey(OntCE clazz, RDFNode rdfList) throws OntJenaException.IllegalArgument {
        clazz.remove(OWL.hasKey, clazz.findHasKey(rdfList)
                .orElseThrow(() -> new OntJenaException.IllegalArgument("Can't find list " + rdfList)).clearAnnotations().clear());
    }

    @Override
    public Optional<OntStatement> findRootStatement() {
        return getRequiredRootStatement(this, OWL.Class);
    }

    @Override
    public abstract Class<? extends OntCE> getActualClass();

    @Override
    public OntIndividual.Anonymous createIndividual() {
        return createAnonymousIndividual(getModel(), this);
    }

    @Override
    public OntIndividual.Named createIndividual(String uri) {
        return createNamedIndividual(getModel(), this, uri);
    }

    @Override
    public OntList<OntDOP> createHasKey(Collection<OntOPE> ope, Collection<OntNDP> dpe) {
        return createHasKey(getModel(), this, Stream.of(ope, dpe).flatMap(Collection::stream));
    }

    @Override
    public OntStatement addHasKey(OntDOP... properties) {
        return createHasKey(getModel(), this, Arrays.stream(properties)).getRoot();
    }

    @Override
    public Optional<OntList<OntDOP>> findHasKey(RDFNode list) {
        return findHasKey(this, list);
    }

    @Override
    public Stream<OntList<OntDOP>> listHasKeys() {
        return listHasKeys(getModel(), this);
    }

    @Override
    public void removeHasKey(RDFNode list) throws OntJenaException.IllegalArgument {
        removeHasKey(this, list);
    }

    @Override
    public void removeHasKey() {
        clearAll(OWL.hasKey);
    }

    protected enum ObjectRestrictionType implements PredicateFilterProvider {
        CLASS {
            @Override
            public Class<OntCE> view() {
                return OntCE.class;
            }
        },
        DATA_RANGE {
            @Override
            public Class<OntDR> view() {
                return OntDR.class;
            }
        },
        INDIVIDUAL {
            @Override
            public Class<OntIndividual> view() {
                return OntIndividual.class;
            }
        },
        LITERAL {
            @Override
            public Class<Literal> view() {
                return Literal.class;
            }

        },
        ;
    }

    protected enum RestrictionType implements PredicateFilterProvider {
        DATA {
            @Override
            public Class<OntNDP> view() {
                return OntNDP.class;
            }
        },
        OBJECT {
            @Override
            public Class<OntOPE> view() {
                return OntOPE.class;
            }
        },
        ;

        public OntFilter getFilter() {
            return getFilter(OWL.onProperty);
        }
    }

    protected enum CardinalityType {
        EXACTLY(OWL.qualifiedCardinality, OWL.cardinality),
        MAX(OWL.maxQualifiedCardinality, OWL.maxCardinality),
        MIN(OWL.minQualifiedCardinality, OWL.minCardinality);
        protected final Property qualifiedPredicate, predicate;

        CardinalityType(Property qualifiedPredicate, Property predicate) {
            this.qualifiedPredicate = qualifiedPredicate;
            this.predicate = predicate;
        }

        public OntFilter getFilter() {
            return (n, g) -> g.asGraph().contains(n, qualifiedPredicate.asNode(), Node.ANY)
                    || g.asGraph().contains(n, predicate.asNode(), Node.ANY);
        }

        public Property getPredicate(boolean isQualified) {
            return isQualified ? qualifiedPredicate : predicate;
        }
    }

    /**
     * Technical interface to make predicate filter for restrictions
     */
    private interface PredicateFilterProvider {

        Class<? extends RDFNode> view();

        default OntFilter getFilter(Property predicate) {
            return (node, graph) -> testObjects(predicate, node, graph);
        }

        default boolean testObjects(Property predicate, Node node, EnhGraph graph) {
            Class<? extends RDFNode> v = view();
            ExtendedIterator<Triple> res = graph.asGraph().find(node, predicate.asNode(), Node.ANY);
            try {
                while (res.hasNext()) {
                    if (OntObjectImpl.canAs(v, res.next().getObject(), graph)) return true;
                }
            } finally {
                res.close();
            }
            return false;
        }
    }

    public static class ObjectSomeValuesFromImpl extends ComponentRestrictionCEImpl<OntCE, OntOPE> implements ObjectSomeValuesFrom {
        public ObjectSomeValuesFromImpl(Node n, EnhGraph m) {
            super(n, m, OWL.someValuesFrom, OntCE.class, OntOPE.class);
        }

        @Override
        public Class<ObjectSomeValuesFrom> getActualClass() {
            return ObjectSomeValuesFrom.class;
        }
    }

    public static class DataSomeValuesFromImpl extends ComponentRestrictionCEImpl<OntDR, OntNDP> implements DataSomeValuesFrom {
        public DataSomeValuesFromImpl(Node n, EnhGraph m) {
            super(n, m, OWL.someValuesFrom, OntDR.class, OntNDP.class);
        }

        @Override
        public Class<DataSomeValuesFrom> getActualClass() {
            return DataSomeValuesFrom.class;
        }
    }

    public static class ObjectAllValuesFromImpl extends ComponentRestrictionCEImpl<OntCE, OntOPE> implements ObjectAllValuesFrom {
        public ObjectAllValuesFromImpl(Node n, EnhGraph m) {
            super(n, m, OWL.allValuesFrom, OntCE.class, OntOPE.class);
        }

        @Override
        public Class<ObjectAllValuesFrom> getActualClass() {
            return ObjectAllValuesFrom.class;
        }
    }

    public static class DataAllValuesFromImpl extends ComponentRestrictionCEImpl<OntDR, OntNDP> implements DataAllValuesFrom {
        public DataAllValuesFromImpl(Node n, EnhGraph m) {
            super(n, m, OWL.allValuesFrom, OntDR.class, OntNDP.class);
        }

        @Override
        public Class<DataAllValuesFrom> getActualClass() {
            return DataAllValuesFrom.class;
        }
    }

    public static class ObjectHasValueImpl extends ComponentRestrictionCEImpl<OntIndividual, OntOPE> implements ObjectHasValue {
        public ObjectHasValueImpl(Node n, EnhGraph m) {
            super(n, m, OWL.hasValue, OntIndividual.class, OntOPE.class);
        }

        @Override
        public Class<ObjectHasValue> getActualClass() {
            return ObjectHasValue.class;
        }
    }

    public static class DataHasValueImpl extends ComponentRestrictionCEImpl<Literal, OntNDP> implements DataHasValue {
        public DataHasValueImpl(Node n, EnhGraph m) {
            super(n, m, OWL.hasValue, Literal.class, OntNDP.class);
        }

        @Override
        public Class<DataHasValue> getActualClass() {
            return DataHasValue.class;
        }
    }

    public static class UnionOfImpl extends ComponentsCEImpl<OntCE> implements UnionOf {
        public UnionOfImpl(Node n, EnhGraph m) {
            super(n, m, OWL.unionOf, OntCE.class);
        }

        @Override
        public Class<UnionOf> getActualClass() {
            return UnionOf.class;
        }
    }

    public static class IntersectionOfImpl extends ComponentsCEImpl<OntCE> implements IntersectionOf {
        public IntersectionOfImpl(Node n, EnhGraph m) {
            super(n, m, OWL.intersectionOf, OntCE.class);
        }

        @Override
        public Class<IntersectionOf> getActualClass() {
            return IntersectionOf.class;
        }
    }

    public static class OneOfImpl extends ComponentsCEImpl<OntIndividual> implements OneOf {
        public OneOfImpl(Node n, EnhGraph m) {
            super(n, m, OWL.oneOf, OntIndividual.class);
        }

        @Override
        public Class<OneOf> getActualClass() {
            return OneOf.class;
        }
    }

    public static class DataMinCardinalityImpl extends CardinalityRestrictionCEImpl<OntDR, OntNDP> implements DataMinCardinality {
        public DataMinCardinalityImpl(Node n, EnhGraph m) {
            super(n, m, OWL.onDataRange, OntDR.class, OntNDP.class, CardinalityType.MIN);
        }

        @Override
        public Class<DataMinCardinality> getActualClass() {
            return DataMinCardinality.class;
        }
    }

    public static class ObjectMinCardinalityImpl extends CardinalityRestrictionCEImpl<OntCE, OntOPE> implements ObjectMinCardinality {
        public ObjectMinCardinalityImpl(Node n, EnhGraph m) {
            super(n, m, OWL.onClass, OntCE.class, OntOPE.class, CardinalityType.MIN);
        }

        @Override
        public Class<ObjectMinCardinality> getActualClass() {
            return ObjectMinCardinality.class;
        }
    }

    public static class DataMaxCardinalityImpl extends CardinalityRestrictionCEImpl<OntDR, OntNDP> implements DataMaxCardinality {
        public DataMaxCardinalityImpl(Node n, EnhGraph m) {
            super(n, m, OWL.onDataRange, OntDR.class, OntNDP.class, CardinalityType.MAX);
        }

        @Override
        public Class<DataMaxCardinality> getActualClass() {
            return DataMaxCardinality.class;
        }
    }

    public static class ObjectMaxCardinalityImpl extends CardinalityRestrictionCEImpl<OntCE, OntOPE> implements ObjectMaxCardinality {
        public ObjectMaxCardinalityImpl(Node n, EnhGraph m) {
            super(n, m, OWL.onClass, OntCE.class, OntOPE.class, CardinalityType.MAX);
        }

        @Override
        public Class<ObjectMaxCardinality> getActualClass() {
            return ObjectMaxCardinality.class;
        }
    }

    public static class DataCardinalityImpl extends CardinalityRestrictionCEImpl<OntDR, OntNDP> implements DataCardinality {
        public DataCardinalityImpl(Node n, EnhGraph m) {
            super(n, m, OWL.onDataRange, OntDR.class, OntNDP.class, CardinalityType.EXACTLY);
        }

        @Override
        public Class<DataCardinality> getActualClass() {
            return DataCardinality.class;
        }
    }

    public static class ObjectCardinalityImpl extends CardinalityRestrictionCEImpl<OntCE, OntOPE> implements ObjectCardinality {
        public ObjectCardinalityImpl(Node n, EnhGraph m) {
            super(n, m, OWL.onClass, OntCE.class, OntOPE.class, CardinalityType.EXACTLY);
        }

        @Override
        public Class<ObjectCardinality> getActualClass() {
            return ObjectCardinality.class;
        }
    }

    public static class HasSelfImpl extends OnPropertyRestrictionCEImpl<OntOPE> implements HasSelf {
        public HasSelfImpl(Node n, EnhGraph m) {
            super(n, m, OntOPE.class);
        }

        @Override
        public Stream<OntStatement> spec() {
            return Stream.concat(super.spec(), required(OWL.hasSelf));
        }

        @Override
        public Class<HasSelf> getActualClass() {
            return HasSelf.class;
        }
    }

    public static class ComplementOfImpl extends OntCEImpl implements ComplementOf {
        public ComplementOfImpl(Node n, EnhGraph m) {
            super(n, m);
        }

        @Override
        public Stream<OntStatement> spec() {
            return Stream.concat(super.spec(), required(OWL.complementOf));
        }

        @Override
        public Class<ComplementOf> getActualClass() {
            return ComplementOf.class;
        }

        @Override
        public OntCE getValue() {
            return getRequiredObject(OWL.complementOf, OntCE.class);
        }

        @Override
        public void setValue(OntCE c) {
            Objects.requireNonNull(c, "Null component");
            clear();
            addProperty(OWL.complementOf, c);
        }

        void clear() {
            removeAll(OWL.complementOf);
        }
    }

    public static class NaryDataAllValuesFromImpl extends NaryRestrictionCEImpl<OntDR, OntNDP> implements NaryDataAllValuesFrom {
        public NaryDataAllValuesFromImpl(Node n, EnhGraph m) {
            super(n, m, OWL.allValuesFrom, OntDR.class, OntNDP.class);
        }

        @Override
        public Class<? extends OntCE> getActualClass() {
            return NaryDataAllValuesFrom.class;
        }
    }

    public static class NaryDataSomeValuesFromImpl extends NaryRestrictionCEImpl<OntDR, OntNDP> implements NaryDataSomeValuesFrom {
        public NaryDataSomeValuesFromImpl(Node n, EnhGraph m) {
            super(n, m, OWL.someValuesFrom, OntDR.class, OntNDP.class);
        }

        @Override
        public Class<? extends OntCE> getActualClass() {
            return NaryDataSomeValuesFrom.class;
        }
    }

    /**
     * An abstract super class for {@link IntersectionOf}, {@link OneOf}, {@link UnionOf}.
     *
     * @param <O> {@link OntObject}
     */
    protected static abstract class ComponentsCEImpl<O extends OntObject> extends OntCEImpl implements ComponentsCE<O> {
        protected final Property predicate;
        protected final Class<O> type;

        protected ComponentsCEImpl(Node n, EnhGraph m, Property predicate, Class<O> type) {
            super(n, m);
            this.predicate = OntJenaException.notNull(predicate, "Null predicate.");
            this.type = OntJenaException.notNull(type, "Null view.");
        }

        @Override
        public Stream<OntStatement> spec() {
            return Stream.concat(super.spec(), getList().content());
        }

        @Override
        public OntList<O> getList() {
            return OntListImpl.asSafeOntList(getRequiredObject(predicate, RDFList.class), getModel(), this, predicate, null, type);
        }
    }

    /**
     * Abstract implementation for any restriction with {@code owl:onProperty} predicate.
     *
     * @param <P> subtype of {@link OntDOP Data or Object Property Expression}
     */
    protected static abstract class OnPropertyRestrictionCEImpl<P extends OntDOP> extends OntCEImpl implements ONProperty<P> {
        protected final Class<P> propertyView;

        /**
         * @param n            {@link Node}
         * @param m            {@link EnhGraph}
         * @param propertyType Class-type for {@link OntDOP}
         */
        protected OnPropertyRestrictionCEImpl(Node n, EnhGraph m, Class<P> propertyType) {
            super(n, m);
            this.propertyView = propertyType;
        }

        @Override
        public Optional<OntStatement> findRootStatement() {
            return getRequiredRootStatement(this, OWL.Restriction);
        }

        @Override
        public P getOnProperty() {
            return getRequiredObject(OWL.onProperty, propertyView);
        }

        @Override
        public void setOnProperty(P p) {
            Objects.requireNonNull(p, "Null " + viewAsString(propertyView));
            clearProperty(OWL.onProperty);
            addProperty(OWL.onProperty, p);
        }

        protected void clearProperty(Property property) {
            removeAll(property);
        }

        @Override
        public Stream<OntStatement> spec() {
            return Stream.concat(super.spec(), required(OWL.onProperty));
        }

    }

    /**
     * Abstract base component-restriction class.
     * It's for CE which has owl:onProperty and some component also (with predicate owl:dataRange,owl:onClass, owl:someValuesFrom, owl:allValuesFrom)
     *
     * @param <O> a class-type of {@link RDFNode rdf-node}
     * @param <P> a class-type of {@link OntDOP data or object property-expression}
     */
    protected static abstract class ComponentRestrictionCEImpl<O extends RDFNode, P extends OntDOP>
            extends OnPropertyRestrictionCEImpl<P> implements ComponentRestrictionCE<O, P> {
        protected final Property predicate;
        protected final Class<O> objectView;

        /**
         * @param n            Node
         * @param m            EnhGraph
         * @param predicate    predicate for value
         * @param objectView   Class
         * @param propertyView Class
         */
        protected ComponentRestrictionCEImpl(Node n, EnhGraph m, Property predicate, Class<O> objectView, Class<P> propertyView) {
            super(n, m, propertyView);
            this.predicate = OntJenaException.notNull(predicate, "Null predicate.");
            this.objectView = OntJenaException.notNull(objectView, "Null object view.");
        }

        @Override
        public Stream<OntStatement> spec() {
            return spec(true);
        }

        protected Stream<OntStatement> spec(boolean requireObject) {
            return requireObject ? Stream.concat(super.spec(), required(predicate)) : super.spec();
        }

        @Override
        public O getValue() {
            return getModel().getNodeAs(getRequiredProperty(predicate).getObject().asNode(), objectView);
        }

        @Override
        public void setValue(O c) {
            clearProperty(predicate);
            addProperty(predicate, c);
        }
    }

    /**
     * Abstraction for any cardinality restriction.
     *
     * @param <O> either {@link OntCE} (predicate {@link OWL#onClass owl:onClass}) or {@link OntDR}
     *            (predicate: {@link OWL#onDataRange owl:onDataRange})
     * @param <P> either {@link OntOPE} or {@link OntNDP}
     */
    protected static abstract class CardinalityRestrictionCEImpl<O extends OntObject, P extends OntDOP>
            extends ComponentRestrictionCEImpl<O, P> implements CardinalityRestrictionCE<O, P> {
        protected final CardinalityType cardinalityType;

        /**
         * @param n               {@link Node}
         * @param m               {@link EnhGraph}
         * @param predicate       either {@code owl:onDataRange} or {@code owl:onClass}
         * @param objectView      interface of class expression or data range
         * @param propertyView    interface, property expression
         * @param cardinalityType type of cardinality.
         */
        protected CardinalityRestrictionCEImpl(Node n,
                                               EnhGraph m,
                                               Property predicate,
                                               Class<O> objectView,
                                               Class<P> propertyView,
                                               CardinalityType cardinalityType) {
            super(n, m, predicate, objectView, propertyView);
            this.cardinalityType = cardinalityType;
        }

        @Override
        public Stream<OntStatement> spec() {
            // note: object value <O> is null for non-qualified restrictions.
            boolean q;
            return Stream.concat(super.spec(q = isQualified()), required(getCardinalityPredicate(q)));
        }

        @Override
        public O getValue() { // null for non-qualified restrictions:
            return object(predicate, objectView).orElse(null);
        }

        @Override
        public int getCardinality() {
            return getRequiredObject(getCardinalityPredicate(), Literal.class).getInt();
        }

        @Override
        public void setCardinality(int cardinality) {
            Literal value = createNonNegativeIntegerLiteral(cardinality);
            Property property = getCardinalityPredicate();
            clearProperty(property);
            addLiteral(property, value);
        }

        protected Property getCardinalityPredicate() {
            return getCardinalityPredicate(isQualified());
        }

        protected Property getCardinalityPredicate(boolean q) {
            return cardinalityType.getPredicate(q);
        }

        @Override
        public boolean isQualified() {
            return isQualified(getValue());
        }

    }

    /**
     * TODO: currently it is a read-only object, no way to modify, since I don't know how to check input parameters
     */
    protected static abstract class NaryRestrictionCEImpl<O extends OntObject, P extends OntDOP> extends OntCEImpl implements NaryRestrictionCE<O, P> {
        protected final Property predicate;
        protected final Class<O> objectType;
        protected final Class<P> propertyType;

        protected NaryRestrictionCEImpl(Node n, EnhGraph m, Property predicate, Class<O> objectType, Class<P> propertyType) {
            super(n, m);
            this.predicate = predicate;
            this.objectType = objectType;
            this.propertyType = propertyType;
        }

        @Override
        public Optional<OntStatement> findRootStatement() {
            return getRequiredRootStatement(this, OWL.Restriction);
        }

        @Override
        public O getValue() {
            return getRequiredObject(predicate, objectType);
        }

        @Override
        public void setValue(O value) {
            throw new OntJenaException.Unsupported("TODO");
        }

        @Override
        public Class<? extends OntCE> getActualClass() {
            return NaryRestrictionCE.class;
        }

        @Override
        public Stream<OntStatement> spec() {
            return Stream.of(super.spec(), required(predicate), getList().content()).flatMap(Function.identity());
        }

        @Override
        public OntList<P> getList() {
            return OntListImpl.asSafeOntList(getRequiredObject(OWL.onProperties, RDFList.class), getModel(),
                    this, predicate, null, propertyType);
        }
    }

    protected static class HasSelfFilter implements OntFilter {
        @Override
        public boolean test(Node n, EnhGraph g) {
            return g.asGraph().contains(n, OWL.hasSelf.asNode(), Models.TRUE.asNode());
        }
    }

    protected static class HasSelfMaker extends OntMaker.WithType {
        protected HasSelfMaker() {
            super(HasSelfImpl.class, OWL.Restriction);
        }

        @Override
        public void make(Node node, EnhGraph eg) {
            super.make(node, eg);
            eg.asGraph().add(Triple.create(node, OWL.hasSelf.asNode(), Models.TRUE.asNode()));
        }
    }

    /**
     * Created by @ssz on 01.09.2018.
     */
    @SuppressWarnings("WeakerAccess")
    public static class ClassExpressionFactory extends OntObjectFactory {
        private static final Node CLASS = OWL.Class.asNode();
        private static final Node RESTRICTION = OWL.Restriction.asNode();
        private final OntObjectFactory namedClass;
        private final Collection<OntObjectFactory> anonymousClasses, restrictions;

        protected ClassExpressionFactory(OntObjectFactory namedClass,
                                         Collection<OntObjectFactory> anonymousClasses,
                                         Collection<OntObjectFactory> restrictions) {
            this.namedClass = namedClass;
            this.anonymousClasses = anonymousClasses;
            this.restrictions = restrictions;
        }

        public static OntObjectFactory build(Configurable.Mode m) {
            return new ClassExpressionFactory(Entities.CLASS.select(m),
                    Arrays.asList(unionOfCEFactory
                            , intersectionOfCEFactory
                            , oneOfCEFactory
                            , complementOfCEFactory),
                    Arrays.asList(objectSomeValuesOfCEFactory
                            , dataSomeValuesOfCEFactory
                            , objectAllValuesOfCEFactory
                            , dataAllValuesOfCEFactory
                            , objectHasValueCEFactory
                            , dataHasValueCEFactory
                            , dataMinCardinalityCEFactory
                            , objectMinCardinalityCEFactory
                            , dataMaxCardinalityCEFactory
                            , objectMaxCardinalityCEFactory
                            , dataCardinalityCEFactory
                            , objectCardinalityCEFactory
                            , hasSelfCEFactory
                            , naryDataAllValuesFromCEFactory
                            , naryDataSomeValuesFromCEFactory));
        }

        private static EnhNode map(Collection<OntObjectFactory> factories, Node n, EnhGraph g) {
            for (OntObjectFactory f : factories) {
                EnhNode r = map(f, n, g);
                if (r != null) return r;
            }
            return null;
        }

        private static EnhNode map(OntObjectFactory f, Node n, EnhGraph g) {
            try {
                return f.wrap(n, g);
            } catch (ConversionException c) {
                return null;
            }
        }

        @Override
        public ExtendedIterator<EnhNode> iterator(EnhGraph g) {
            return g.asGraph().find(Node.ANY, RDF.Nodes.type, CLASS)
                    .mapWith(t -> {
                        Node n = t.getSubject();
                        return n.isURI() ? map(namedClass, n, g) : map(anonymousClasses, n, g);
                    })
                    .andThen(g.asGraph().find(Node.ANY, RDF.Nodes.type, RESTRICTION)
                            .mapWith(t -> map(restrictions, t.getSubject(), g)))
                    .filterDrop(Objects::isNull);
        }

        @Override
        public boolean canWrap(Node node, EnhGraph eg) {
            if (node.isURI()) {
                return namedClass.canWrap(node, eg);
            }
            if (eg.asGraph().contains(node, RDF.Nodes.type, RESTRICTION)) {
                for (OntObjectFactory f : restrictions) {
                    if (f.canWrap(node, eg)) return true;
                }
                return false;
            }
            for (OntObjectFactory f : anonymousClasses) {
                if (f.canWrap(node, eg)) return true;
            }
            return false;
        }

        @Override
        protected EnhNode doWrap(Node node, EnhGraph eg) {
            if (node.isURI()) {
                return map(namedClass, node, eg);
            }
            if (eg.asGraph().contains(node, RDF.Nodes.type, CLASS)) {
                return map(anonymousClasses, node, eg);
            }
            return map(restrictions, node, eg);
        }

        @Override
        public EnhNode wrap(Node node, EnhGraph eg) throws ConversionException {
            if (node.isURI()) {
                return namedClass.wrap(node, eg);
            }
            ConversionException ex = new ConversionException("Can't convert node " + node + " to Class Expression.");
            if (eg.asGraph().contains(node, RDF.Nodes.type, RESTRICTION)) {
                for (OntObjectFactory f : restrictions) {
                    try {
                        return f.wrap(node, eg);
                    } catch (ConversionException c) {
                        ex.addSuppressed(c);
                    }
                }
                throw ex;
            }
            for (OntObjectFactory f : anonymousClasses) {
                try {
                    return f.wrap(node, eg);
                } catch (ConversionException c) {
                    ex.addSuppressed(c);
                }
            }
            throw ex;
        }
    }
}

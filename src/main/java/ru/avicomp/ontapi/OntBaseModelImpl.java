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

package ru.avicomp.ontapi;

import org.apache.jena.graph.Graph;
import org.apache.jena.mem.GraphMem;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.AxiomAnnotations;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.model.parameters.Navigation;
import org.semanticweb.owlapi.model.parameters.OntologyCopy;
import org.semanticweb.owlapi.search.Filters;
import org.semanticweb.owlapi.util.OWLAxiomSearchFilter;
import ru.avicomp.ontapi.internal.ConfigProvider;
import ru.avicomp.ontapi.internal.InternalModel;
import ru.avicomp.ontapi.internal.InternalModelHolder;
import ru.avicomp.ontapi.jena.model.OntID;
import ru.avicomp.owlapi.OWLObjectImpl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * An 'Immutable' ontology only with methods to read information in the form of OWL-Objects from graph-model.
 * It's our analogy of <a href='https://github.com/owlcs/owlapi/blob/version5/impl/src/main/java/uk/ac/manchester/cs/owl/owlapi/OWLImmutableOntologyImpl.java'>uk.ac.manchester.cs.owl.owlapi.OWLImmutableOntologyImpl</a>
 * <p>
 * Created by @szuev on 03.12.2016.
 */
@SuppressWarnings("WeakerAccess")
public abstract class OntBaseModelImpl extends OWLObjectImpl implements OWLOntology, ConfigProvider, InternalModelHolder {
    // binary format to provide serialization:
    protected static final OntFormat DEFAULT_SERIALIZATION_FORMAT = OntFormat.RDF_THRIFT;

    protected transient InternalModel base;
    protected transient OntologyManagerImpl managerBackCopy;

    protected OWLOntologyID ontologyID;

    protected OntBaseModelImpl(Graph graph, OntologyManagerImpl.ModelConfig conf) {
        this.base = new InternalModel(OntApiException.notNull(graph, "Null graph."), OntApiException.notNull(conf, "Null conf."));
    }

    @Override
    public InternalModel getBase() {
        return base;
    }

    @Override
    public void setBase(InternalModel m) {
        base = m;
    }

    @Override
    public OntologyManagerImpl.ModelConfig getConfig() {
        return (OntologyManagerImpl.ModelConfig) base.getConfig();
    }

    @Override
    public OntologyManager getOWLOntologyManager() {
        return getConfig().manager();
    }

    /**
     * Sets the manager.
     * The parameter could be null (e.g. during {@link OWLOntologyManager#clearOntologies})
     * Used also during {@link OWLOntologyManager#copyOntology(OWLOntology, OntologyCopy)}
     *
     * @param manager {@link OntologyManager}, nullable.
     * @throws OntApiException in case wrong manager specified.
     * @see <a href='https://github.com/owlcs/owlapi/blob/version5/impl/src/main/java/uk/ac/manchester/cs/owl/owlapi/OWLOntologyManagerImpl.java'>uk.ac.manchester.cs.owl.owlapi.OWLOntologyManagerImpl#copyOntology(OWLOntology, OntologyCopy)</a>
     */
    @Override
    public void setOWLOntologyManager(OWLOntologyManager manager) {
        if (Objects.equals(getOWLOntologyManager(), manager)) return;
        OntologyManagerImpl m;
        try {
            m = (OntologyManagerImpl) manager;
        } catch (ClassCastException ce) {
            if (this.managerBackCopy != null) {
                // rollback changes made while coping (inside OWL-API 5.0.5)
                this.managerBackCopy.rollBackMoving(this, manager);
                getConfig().setManager(this.managerBackCopy);
                this.managerBackCopy = null;
            }
            throw new OntApiException("Trying to move? Don't do it!", ce);
        }
        this.managerBackCopy = getConfig().setManager(m);
    }

    /**
     * Gets Ontology ID.
     * Does not just return cached {@link #ontologyID} to provide synchronization with encapsulated jena model ({@link #base}).
     * In the other hand we need this cached {@link #ontologyID} to be existed and relevant for owl serialization.
     *
     * @return the {@link OWLOntologyID}
     */
    @Override
    public OWLOntologyID getOntologyID() {
        OntID id = this.base.getID();
        if (id.isAnon()) {
            return this.ontologyID == null || !this.ontologyID.isAnonymous() ? assignID(new OWLOntologyID()) : this.ontologyID;
        }
        Optional<IRI> iri = Optional.of(id.getURI()).map(IRI::create);
        Optional<IRI> version = Optional.ofNullable(id.getVersionIRI()).map(IRI::create);
        return assignID(new OWLOntologyID(iri, version));
    }

    /**
     * Sets Ontology ID.
     * For internal usage only: the outer interface must be "immutable".
     *
     * @param id {@link OWLOntologyID Ontology ID}
     */
    protected void setOntologyID(OWLOntologyID id) {
        try {
            if (id.isAnonymous()) {
                this.base.setID(null).setVersionIRI(null);
                return;
            }
            this.base.setID(id.getOntologyIRI().map(IRI::getIRIString).orElse(null))
                    .setVersionIRI(id.getVersionIRI().map(IRI::getIRIString).orElse(null));
        } finally {
            assignID(id);
        }
    }

    /**
     * Physically sets Ontology ID to this instance.
     * For internal usage only: the outer interface must be "immutable".
     *
     * @param id {@link OWLOntologyID Ontology ID}
     * @return the same id
     */
    protected OWLOntologyID assignID(OWLOntologyID id) {
        this.ontologyID = id;
        // reset hashcode (due to changes in OWL-API-api 5.1.1 -> 5.1.4)
        this.hashCode = 0;
        return id;
    }

    @Override
    public boolean isAnonymous() {
        return base.getID().isAnon();
    }

    @Override
    public boolean isEmpty() {
        return base.isOntologyEmpty();
    }

    @Override
    public Stream<OWLAnnotation> annotations() {
        return base.annotations();
    }

    /*
     * =============================
     * Methods to work with imports:
     * =============================
     */

    @Override
    public Stream<OWLOntology> imports() {
        return getOWLOntologyManager().imports(this);
    }

    @Override
    public Stream<OWLImportsDeclaration> importsDeclarations() {
        return base.importDeclarations();
    }

    @Override
    public Stream<IRI> directImportsDocuments() {
        return importsDeclarations().map(OWLImportsDeclaration::getIRI);
    }

    @Override
    public Stream<OWLOntology> directImports() {
        return getOWLOntologyManager().directImports(this);
    }

    @Override
    public Stream<OWLOntology> importsClosure() {
        return getOWLOntologyManager().importsClosure(this);
    }

    /*
     * ==========================
     * To work with OWL-entities:
     * ==========================
     */

    @Override
    public Stream<OWLClass> classesInSignature() {
        return base.classes();
    }

    @Override
    public Stream<OWLAnonymousIndividual> anonymousIndividuals() {
        return base.anonymousIndividuals();
    }

    @Override
    public Stream<OWLAnonymousIndividual> referencedAnonymousIndividuals() {
        return anonymousIndividuals();
    }

    @Override
    public Stream<OWLNamedIndividual> individualsInSignature() {
        return base.namedIndividuals();
    }

    @Override
    public Stream<OWLDataProperty> dataPropertiesInSignature() {
        return base.dataProperties();
    }

    @Override
    public Stream<OWLObjectProperty> objectPropertiesInSignature() {
        return base.objectProperties();
    }

    @Override
    public Stream<OWLAnnotationProperty> annotationPropertiesInSignature() {
        return base.annotationProperties();
    }

    @Override
    public Stream<OWLDatatype> datatypesInSignature() {
        return base.datatypes();
    }

    @Override
    public Stream<OWLEntity> signature() {
        return Stream.of(classesInSignature(), objectPropertiesInSignature(), dataPropertiesInSignature(),
                individualsInSignature(), datatypesInSignature(), annotationPropertiesInSignature()).flatMap(Function.identity());
    }

    @Override
    public Stream<OWLEntity> entitiesInSignature(@Nullable IRI entityIRI) {
        return base.entities(entityIRI);
    }

    @Override
    public Set<IRI> getPunnedIRIs(@Nonnull Imports imports) {
        return base.ambiguousEntities(Imports.INCLUDED.equals(imports)).map(Resource::getURI).map(IRI::create).collect(Collectors.toSet());
    }

    @Override
    public boolean isDeclared(@Nullable OWLEntity owlEntity) {
        return base.axioms(OWLDeclarationAxiom.class).map(OWLDeclarationAxiom::getEntity)
                .anyMatch(obj -> obj.equals(owlEntity));
    }

    @Override
    public boolean containsReference(@Nonnull OWLEntity entity) {
        return signature().anyMatch(entity::equals);
    }

    @Override
    public boolean containsClassInSignature(@Nonnull IRI iri) {
        return classesInSignature().map(HasIRI::getIRI).anyMatch(iri::equals);
    }

    @Override
    public boolean containsClassInSignature(@Nonnull IRI iri, @Nonnull Imports imports) {
        return imports.stream(this).anyMatch(o -> o.containsClassInSignature(iri));
    }

    @Override
    public boolean containsObjectPropertyInSignature(@Nonnull IRI iri) {
        return objectPropertiesInSignature().map(HasIRI::getIRI).anyMatch(iri::equals);
    }

    @Override
    public boolean containsObjectPropertyInSignature(@Nonnull IRI iri, @Nonnull Imports imports) {
        return imports.stream(this).anyMatch(o -> o.containsObjectPropertyInSignature(iri));
    }

    @Override
    public boolean containsDataPropertyInSignature(@Nonnull IRI iri) {
        return dataPropertiesInSignature().map(HasIRI::getIRI).anyMatch(iri::equals);
    }

    @Override
    public boolean containsDataPropertyInSignature(@Nonnull IRI iri, @Nonnull Imports imports) {
        return imports.stream(this).anyMatch(o -> o.containsDataPropertyInSignature(iri));
    }

    @Override
    public boolean containsAnnotationPropertyInSignature(@Nonnull IRI iri) {
        return annotationPropertiesInSignature().map(HasIRI::getIRI).anyMatch(iri::equals);
    }

    @Override
    public boolean containsAnnotationPropertyInSignature(@Nonnull IRI iri, @Nonnull Imports imports) {
        return imports.stream(this).anyMatch(o -> o.containsAnnotationPropertyInSignature(iri));
    }

    @Override
    public boolean containsDatatypeInSignature(@Nonnull IRI iri) {
        return datatypesInSignature().map(HasIRI::getIRI).anyMatch(iri::equals);
    }

    @Override
    public boolean containsDatatypeInSignature(@Nonnull IRI iri, @Nonnull Imports imports) {
        return imports.stream(this).anyMatch(o -> o.containsDatatypeInSignature(iri));
    }

    @Override
    public boolean containsIndividualInSignature(@Nonnull IRI iri) {
        return individualsInSignature().map(HasIRI::getIRI).anyMatch(iri::equals);
    }

    @Override
    public boolean containsIndividualInSignature(@Nonnull IRI iri, @Nonnull Imports imports) {
        return imports.stream(this).anyMatch(o -> o.containsIndividualInSignature(iri));
    }

    /*
     * =======================
     * To work with OWL-Axioms
     * =======================
     */

    @Override
    public Stream<OWLAxiom> axioms() {
        return base.axioms();
    }

    @Override
    public <T extends OWLAxiom> Stream<T> axioms(@Nonnull AxiomType<T> axiomType) {
        return base.axioms(axiomType);
    }

    /**
     * Returns all axioms that inherit the interface {@link OWLClassAxiom}
     *
     * @return Steam of {@link OWLAxiom}
     */
    public Stream<OWLClassAxiom> classAxioms() {
        return Stream.of(OWLDisjointClassesAxiom.class,
                OWLDisjointUnionAxiom.class,
                OWLEquivalentClassesAxiom.class,
                OWLSubClassOfAxiom.class)
                .map(c -> base.axioms(c)).flatMap(Function.identity());
    }

    /**
     * Returns all axioms that inherit the interface {@link OWLObjectPropertyAxiom}
     *
     * @return Steam of {@link OWLAxiom}
     */
    public Stream<OWLObjectPropertyAxiom> objectPropertyAxioms() {
        return Stream.of(
                OWLSubObjectPropertyOfAxiom.class,
                OWLObjectPropertyDomainAxiom.class,
                OWLObjectPropertyRangeAxiom.class,

                OWLDisjointObjectPropertiesAxiom.class,
                OWLSubPropertyChainOfAxiom.class,
                OWLEquivalentObjectPropertiesAxiom.class,
                OWLInverseObjectPropertiesAxiom.class,

                OWLTransitiveObjectPropertyAxiom.class,
                OWLIrreflexiveObjectPropertyAxiom.class,
                OWLReflexiveObjectPropertyAxiom.class,
                OWLSymmetricObjectPropertyAxiom.class,
                OWLFunctionalObjectPropertyAxiom.class,
                OWLInverseFunctionalObjectPropertyAxiom.class,
                OWLAsymmetricObjectPropertyAxiom.class
        ).map(c -> base.axioms(c)).flatMap(Function.identity());
    }

    /**
     * Returns all axioms that inherit the interface {@link OWLDataPropertyAxiom}
     *
     * @return Steam of {@link OWLAxiom}
     */
    public Stream<OWLDataPropertyAxiom> dataPropertyAxioms() {
        return Stream.of(
                OWLDataPropertyDomainAxiom.class,
                OWLDataPropertyRangeAxiom.class,

                OWLDisjointDataPropertiesAxiom.class,
                OWLSubDataPropertyOfAxiom.class,
                OWLEquivalentDataPropertiesAxiom.class,

                OWLFunctionalDataPropertyAxiom.class
        ).map(c -> base.axioms(c)).flatMap(Function.identity());
    }

    /**
     * Returns all axioms that inherit the interface {@link OWLIndividualAxiom}
     *
     * @return Steam of {@link OWLAxiom}
     */
    public Stream<OWLIndividualAxiom> individualAxioms() {
        return Stream.of(
                OWLClassAssertionAxiom.class,
                OWLObjectPropertyAssertionAxiom.class,
                OWLDataPropertyAssertionAxiom.class,

                OWLNegativeObjectPropertyAssertionAxiom.class,
                OWLNegativeDataPropertyAssertionAxiom.class,

                OWLSameIndividualAxiom.class,
                OWLDifferentIndividualsAxiom.class
        ).map(c -> base.axioms(c)).flatMap(Function.identity());
    }

    /**
     * Returns all axioms that inherit the interface {@link OWLNaryAxiom}
     *
     * @return Steam of {@link OWLAxiom}
     */
    public Stream<OWLNaryAxiom> naryAxioms() {
        return Stream.of(
                OWLEquivalentClassesAxiom.class,
                OWLEquivalentDataPropertiesAxiom.class,
                OWLEquivalentObjectPropertiesAxiom.class,
                OWLSameIndividualAxiom.class,

                OWLDisjointClassesAxiom.class,
                OWLDisjointDataPropertiesAxiom.class,
                OWLDisjointObjectPropertiesAxiom.class,
                OWLDifferentIndividualsAxiom.class
        ).map(c -> base.axioms(c)).flatMap(Function.identity());
    }

    /**
     * Gets the axioms that form the definition/description of a class.
     * The results include:
     * <ul>
     * <li>Subclass axioms where the subclass is equal to the specified class</li>
     * <li>Equivalent class axioms where the specified class is an operand in the equivalent class axiom</li>
     * <li>Disjoint class axioms where the specified class is an operand in the disjoint class axiom</li>
     * <li>Disjoint union axioms, where the specified class is the named class that is equivalent to the disjoint union</li>
     * </ul>
     *
     * @param clazz The class whose describing axioms are to be retrieved.
     * @return A stream of class axioms that describe the class.
     */
    @Override
    public Stream<OWLClassAxiom> axioms(@Nonnull OWLClass clazz) {
        Stream<? extends OWLClassAxiom> subClassOf = base.axioms(OWLSubClassOfAxiom.class)
                .filter(a -> Objects.equals(a.getSubClass(), clazz));
        Stream<? extends OWLClassAxiom> disjointUnion = base.axioms(OWLDisjointUnionAxiom.class)
                .filter(a -> Objects.equals(a.getOWLClass(), clazz));
        Stream<? extends OWLClassAxiom> nary = Stream.of(
                OWLDisjointClassesAxiom.class,
                OWLEquivalentClassesAxiom.class
        ).map(c -> base.axioms(c))
                .flatMap(Function.identity())
                .filter(a -> a.operands().anyMatch(c -> Objects.equals(c, clazz)));
        return Stream.of(subClassOf, disjointUnion, nary).flatMap(Function.identity());
    }

    /**
     * Gets the axioms that form the definition/description of an object property.
     * The result set of object property axioms include:
     * <ul>
     * <li>1) Sub-property axioms where the sub property is the specified property</li>
     * <li>2) Equivalent property axioms where the axiom contains the specified property</li>
     * <li>3) Equivalent property axioms that contain the inverse of the specified property</li>
     * <li>4) Disjoint property axioms that contain the specified property</li>
     * <li>5) Domain axioms that specify a domain of the specified property</li>
     * <li>6) Range axioms that specify a range of the specified property</li>
     * <li>7) Any property characteristic axiom (i.e. Functional, Symmetric, Reflexive etc.) whose subject is the specified property</li>
     * <li>8) Inverse properties axioms that contain the specified property</li>
     * </ul>
     * <b>Note: either condition *3* or OWL-API-5.1.4 implementation (owlapi-impl) are wrong as shown by tests.</b>
     *
     * @param property The property whose defining axioms are to be retrieved.
     * @return A stream of object property axioms that describe the specified property.
     */
    @Override
    public Stream<OWLObjectPropertyAxiom> axioms(@Nonnull OWLObjectPropertyExpression property) {
        Stream<? extends OWLObjectPropertyAxiom> subPropertyOf = base.axioms(OWLSubObjectPropertyOfAxiom.class)
                .filter(a -> Objects.equals(a.getSubProperty(), property));
        @SuppressWarnings("unchecked")
        Stream<? extends OWLObjectPropertyAxiom> nary = Stream.of(
                OWLEquivalentObjectPropertiesAxiom.class,
                OWLDisjointObjectPropertiesAxiom.class,
                OWLInverseObjectPropertiesAxiom.class
        ).map(c -> base.axioms(c))
                .flatMap(Function.identity())
                .map(OWLNaryPropertyAxiom.class::cast)
                .filter(a -> a.operands().anyMatch(o -> Objects.equals(o, property)))
                .map(OWLObjectPropertyAxiom.class::cast);
        Stream<? extends OWLObjectPropertyAxiom> unary = Stream.of(
                OWLObjectPropertyDomainAxiom.class,
                OWLObjectPropertyRangeAxiom.class,
                OWLTransitiveObjectPropertyAxiom.class,
                OWLIrreflexiveObjectPropertyAxiom.class,
                OWLReflexiveObjectPropertyAxiom.class,
                OWLSymmetricObjectPropertyAxiom.class,
                OWLFunctionalObjectPropertyAxiom.class,
                OWLInverseFunctionalObjectPropertyAxiom.class,
                OWLAsymmetricObjectPropertyAxiom.class
        ).map(c -> base.axioms(c))
                .flatMap(Function.identity())
                .map(OWLUnaryPropertyAxiom.class::cast)
                .filter(a -> Objects.equals(a.getProperty(), property))
                .map(OWLObjectPropertyAxiom.class::cast);
        return Stream.of(subPropertyOf, nary, unary).flatMap(Function.identity());
    }

    /**
     * Gets the axioms that form the definition/description of a data property.
     * The result set of data property axioms include:
     * <ul>
     * <li>Sub-property axioms where the sub property is the specified property</li>
     * <li>Equivalent property axioms where the axiom contains the specified property</li>
     * <li>Disjoint property axioms that contain the specified property</li>
     * <li>Domain axioms that specify a domain of the specified property</li>
     * <li>Range axioms that specify a range of the specified property</li>
     * <li>Functional data property characteristic axiom whose subject is the specified property</li>
     * </ul>
     *
     * @param property The property whose defining axioms are to be retrieved.
     * @return A stream of data property axioms.
     */
    @Override
    public Stream<OWLDataPropertyAxiom> axioms(@Nonnull OWLDataProperty property) {
        Stream<? extends OWLDataPropertyAxiom> subPropertyOf = base.axioms(OWLSubDataPropertyOfAxiom.class)
                .filter(a -> Objects.equals(a.getSubProperty(), property));
        @SuppressWarnings("unchecked")
        Stream<? extends OWLDataPropertyAxiom> nary = Stream.of(
                OWLEquivalentDataPropertiesAxiom.class,
                OWLDisjointDataPropertiesAxiom.class,
                OWLInverseObjectPropertiesAxiom.class
        ).map(c -> base.axioms(c))
                .flatMap(Function.identity())
                .map(OWLNaryPropertyAxiom.class::cast)
                .filter(a -> a.operands().anyMatch(o -> Objects.equals(o, property)))
                .map(OWLDataPropertyAxiom.class::cast);
        Stream<? extends OWLDataPropertyAxiom> unary = Stream.of(
                OWLDataPropertyDomainAxiom.class,
                OWLDataPropertyRangeAxiom.class,
                OWLFunctionalDataPropertyAxiom.class
        ).map(c -> base.axioms(c))
                .flatMap(Function.identity())
                .map(OWLUnaryPropertyAxiom.class::cast)
                .filter(a -> Objects.equals(a.getProperty(), property))
                .map(OWLDataPropertyAxiom.class::cast);
        return Stream.of(subPropertyOf, nary, unary).flatMap(Function.identity());
    }

    /**
     * Gets the axioms that form the definition/description of an individual.
     * Conditions:
     * <ul>
     * <li>Individual type assertions that assert the type of the specified individual</li>
     * <li>Same individuals axioms that contain the specified individual</li>
     * <li>Different individuals axioms that contain the specified individual</li>
     * <li>Object property assertion axioms whose subject is the specified individual</li>
     * <li>Data property assertion axioms whose subject is the specified individual</li>
     * <li>Negative object property assertion axioms whose subject is the specified individual</li>
     * <li>Negative data property assertion axioms whose subject is the specified individual</li>
     * </ul>
     *
     * @param individual The individual whose defining axioms are to be retrieved.
     * @return Stream of individual axioms.
     */
    @Override
    public Stream<OWLIndividualAxiom> axioms(@Nonnull OWLIndividual individual) {
        Stream<? extends OWLIndividualAxiom> classAssertion = base.axioms(OWLClassAssertionAxiom.class)
                .filter(a -> Objects.equals(a.getIndividual(), individual));
        Stream<? extends OWLIndividualAxiom> nary = Stream.of(
                OWLSameIndividualAxiom.class,
                OWLDifferentIndividualsAxiom.class
        ).map(c -> base.axioms(c))
                .flatMap(Function.identity())
                .map(OWLNaryIndividualAxiom.class::cast)
                .filter(a -> a.operands().anyMatch(o -> Objects.equals(o, individual)));
        Stream<? extends OWLIndividualAxiom> propertyAssertion = Stream.of(
                OWLObjectPropertyAssertionAxiom.class,
                OWLDataPropertyAssertionAxiom.class,
                OWLNegativeObjectPropertyAssertionAxiom.class,
                OWLNegativeDataPropertyAssertionAxiom.class
        ).map(c -> base.axioms(c))
                .flatMap(Function.identity())
                .map(OWLPropertyAssertionAxiom.class::cast)
                .filter(a -> Objects.equals(a.getSubject(), individual));
        return Stream.of(classAssertion, nary, propertyAssertion).flatMap(Function.identity());
    }

    @Override
    public Stream<OWLDatatypeDefinitionAxiom> axioms(@Nonnull OWLDatatype datatype) {
        return base.axioms(OWLDatatypeDefinitionAxiom.class).filter(a -> datatype.equals(a.getDatatype()));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends OWLAxiom> Stream<T> axioms(@Nonnull OWLAxiomSearchFilter filter, @Nonnull Object key) {
        return (Stream<T>) base.axioms(StreamSupport.stream(filter.getAxiomTypes().spliterator(), false)
                .map(type -> (AxiomType<T>) type)
                .collect(Collectors.toSet())).filter(a -> filter.pass(a, key));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends OWLAxiom> Stream<T> axioms(@Nonnull OWLAxiomSearchFilter filter, @Nonnull Object key, @Nonnull Imports imports) {
        return imports.stream(this).flatMap(o -> o.axioms(filter, key));
    }

    /**
     * Generic search method: results all axioms which refer object, are instances of type.
     * WARNING: it differs from original OWL-API method.
     * For internal use only.
     *
     * @param type     {@link Class Class&lt;OWLAxiom&gt;}, not null, type of axioms.
     * @param view     {@link Class Class&lt;OWLObject&gt;}. anything. ignored.
     * @param object   {@link OWLObject} to find occurrences.
     * @param position {@link Navigation} used in conjunction with {@code object} for some several kinds of axioms.
     * @return Stream of {@link OWLAxiom}s
     * @see <a href='https://github.com/owlcs/owlapi/blob/version5/impl/src/main/java/uk/ac/manchester/cs/owl/owlapi/OWLImmutableOntologyImpl.java#L544'>uk.ac.manchester.cs.owl.owlapi.OWLImmutableOntologyImpl#axioms(Class, Class, OWLObject, Navigation)</a>
     * @see <a href='https://github.com/owlcs/owlapi/blob/version5/impl/src/main/java/uk/ac/manchester/cs/owl/owlapi/Internals.java#L495'>uk.ac.manchester.cs.owl.owlapi.Internals#get(Class, Class, Navigation)</a>
     */
    @SuppressWarnings("unchecked")
    @Override
    public <A extends OWLAxiom> Stream<A> axioms(@Nonnull Class<A> type, @Nullable Class<? extends OWLObject> view, @Nonnull OWLObject object, @Nullable Navigation position) {
        if (OWLDeclarationAxiom.class.equals(type) && OWLEntity.class.isInstance(object) && Navigation.IN_SUB_POSITION.equals(position)) {
            return (Stream<A>) base.axioms(OWLDeclarationAxiom.class).filter(a -> object.equals(a.getEntity()));
        }
        if (OWLSubObjectPropertyOfAxiom.class.equals(type) && OWLObjectPropertyExpression.class.isInstance(object)) {
            return (Stream<A>) base.axioms(OWLSubObjectPropertyOfAxiom.class)
                    .filter(a -> object.equals(Navigation.IN_SUPER_POSITION.equals(position) ? a.getSuperProperty() : a.getSubProperty()));
        }
        if (OWLSubDataPropertyOfAxiom.class.equals(type) && OWLDataPropertyExpression.class.isInstance(object)) {
            return (Stream<A>) base.axioms(OWLSubDataPropertyOfAxiom.class)
                    .filter(a -> object.equals(Navigation.IN_SUPER_POSITION.equals(position) ? a.getSuperProperty() : a.getSubProperty()));
        }
        if (OWLSubAnnotationPropertyOfAxiom.class.equals(type) && OWLAnnotationProperty.class.isInstance(object)) { // the difference: this axiom type is ignored in original OWL-API method:
            return (Stream<A>) base.axioms(OWLSubAnnotationPropertyOfAxiom.class)
                    .filter(a -> object.equals(Navigation.IN_SUPER_POSITION.equals(position) ? a.getSuperProperty() : a.getSubProperty()));
        }
        if (OWLSubClassOfAxiom.class.equals(type) && OWLClassExpression.class.isInstance(object)) {
            return (Stream<A>) base.axioms(OWLSubClassOfAxiom.class)
                    .filter(a -> object.equals(Navigation.IN_SUPER_POSITION.equals(position) ? a.getSuperClass() : a.getSubClass()));
        }
        if (OWLInverseObjectPropertiesAxiom.class.equals(type) && OWLObjectPropertyExpression.class.isInstance(object)) {
            return (Stream<A>) base.axioms(OWLInverseObjectPropertiesAxiom.class)
                    .filter(a -> object.equals(Navigation.IN_SUPER_POSITION.equals(position) ? a.getSecondProperty() : a.getFirstProperty()));
        }
        if (OWLObjectPropertyAssertionAxiom.class.equals(type) && OWLIndividual.class.isInstance(object)) {
            return (Stream<A>) base.axioms(OWLObjectPropertyAssertionAxiom.class)
                    .filter(a -> object.equals(Navigation.IN_SUPER_POSITION.equals(position) ? a.getObject() : a.getSubject()));
        }
        if (OWLNegativeObjectPropertyAssertionAxiom.class.equals(type) && OWLIndividual.class.isInstance(object)) {
            return (Stream<A>) base.axioms(OWLNegativeObjectPropertyAssertionAxiom.class)
                    .filter(a -> object.equals(Navigation.IN_SUPER_POSITION.equals(position) ? a.getObject() : a.getSubject()));
        }
        if (OWLAnnotationAssertionAxiom.class.equals(type)) {
            if (Navigation.IN_SUPER_POSITION.equals(position) && OWLAnnotationObject.class.isInstance(object)) {
                return (Stream<A>) base.axioms(OWLAnnotationAssertionAxiom.class).filter(a -> object.equals(a.getValue()));
            }
            if (Navigation.IN_SUB_POSITION.equals(position) && OWLAnnotationSubject.class.isInstance(object)) {
                return (Stream<A>) base.axioms(OWLAnnotationAssertionAxiom.class).filter(a -> object.equals(a.getSubject()));
            }
        }
        if (OWLDisjointUnionAxiom.class.equals(type) && OWLClassExpression.class.isInstance(object)) {
            return (Stream<A>) base.axioms(OWLDisjointUnionAxiom.class)
                    .filter(a -> Navigation.IN_SUPER_POSITION.equals(position) ? a.classExpressions().anyMatch(object::equals) : object.equals(a.getOWLClass()));
        }
        if (OWLSubPropertyChainOfAxiom.class.equals(type) && OWLObjectPropertyExpression.class.isInstance(object)) {
            return (Stream<A>) base.axioms(OWLSubPropertyChainOfAxiom.class)
                    .filter(a -> Navigation.IN_SUPER_POSITION.equals(position) ? a.getPropertyChain().stream().anyMatch(object::equals) : object.equals(a.getSuperProperty()));
        }
        if (OWLClassAxiom.class.equals(type) && OWLClass.class.isInstance(object)) {
            return (Stream<A>) axioms((OWLClass) object);
        }
        if (OWLObjectPropertyAxiom.class.equals(type) && OWLObjectPropertyExpression.class.isInstance(object)) {
            return (Stream<A>) axioms((OWLObjectPropertyExpression) object);
        }
        if (OWLDataPropertyAxiom.class.equals(type) && OWLDataProperty.class.isInstance(object)) {
            return (Stream<A>) axioms((OWLDataProperty) object);
        }
        if (OWLIndividualAxiom.class.equals(type) && OWLIndividual.class.isInstance(object)) {
            return (Stream<A>) axioms((OWLIndividual) object);
        }
        if (OWLNaryAxiom.class.isAssignableFrom(type)) {
            return base.axioms(type).filter(a -> ((OWLNaryAxiom) a).operands().anyMatch(o -> Objects.equals(o, object)));
        }
        // default:
        return base.axioms(type).filter(a -> OwlObjects.objects(object.getClass(), a).anyMatch(object::equals));
    }

    @Override
    public Stream<OWLAxiom> tboxAxioms(@Nonnull Imports imports) {
        return AxiomType.TBoxAxiomTypes.stream().flatMap(t -> axioms(t, imports));
    }

    @Override
    public Stream<OWLAxiom> aboxAxioms(@Nonnull Imports imports) {
        return AxiomType.ABoxAxiomTypes.stream().flatMap(t -> axioms(t, imports));
    }

    @Override
    public Stream<OWLAxiom> rboxAxioms(@Nonnull Imports imports) {
        return AxiomType.RBoxAxiomTypes.stream().flatMap(t -> axioms(t, imports));
    }

    @Override
    public Stream<OWLLogicalAxiom> logicalAxioms() {
        return base.axioms(AxiomType.AXIOM_TYPES.stream().filter(AxiomType::isLogical).collect(Collectors.toSet())).map(OWLLogicalAxiom.class::cast);
    }

    @Override
    public Stream<OWLClassAxiom> generalClassAxioms() {
        Stream<OWLSubClassOfAxiom> subClassOfAxioms = base.axioms(OWLSubClassOfAxiom.class)
                .filter(a -> a.getSubClass().isAnonymous());
        Stream<? extends OWLNaryClassAxiom> naryClassAxioms = Stream.of(OWLEquivalentClassesAxiom.class, OWLDisjointClassesAxiom.class)
                .map(base::axioms).flatMap(Function.identity())
                .filter(a -> a.classExpressions().allMatch(IsAnonymous::isAnonymous));
        return Stream.concat(subClassOfAxioms, naryClassAxioms);
    }

    @Override
    public Stream<OWLAxiom> axiomsIgnoreAnnotations(@Nonnull OWLAxiom axiom) {
        return axioms(axiom.getAxiomType()).map(OWLAxiom.class::cast).filter(ax -> ax.equalsIgnoreAnnotations(axiom));
    }

    @Override
    public Stream<OWLAxiom> axiomsIgnoreAnnotations(@Nonnull OWLAxiom axiom, @Nonnull Imports imports) {
        return imports.stream(this).flatMap(o -> o.axiomsIgnoreAnnotations(axiom));
    }

    @Override
    public Stream<OWLAxiom> referencingAxioms(@Nonnull OWLPrimitive primitive) {
        if (primitive instanceof IRI) {
            return axioms().filter(a -> OwlObjects.iris(a).anyMatch(primitive::equals));
        }
        if (primitive instanceof OWLDatatype) { // as well as iri the datatype could be hidden inside other objects (literals):
            return axioms().filter(a -> OwlObjects.objects(OWLDatatype.class, a).anyMatch(primitive::equals));
        }
        return axioms().filter(a -> OwlObjects.objects(OWLPrimitive.class, a).anyMatch(primitive::equals));
    }

    @Override
    public Stream<OWLSubAnnotationPropertyOfAxiom> subAnnotationPropertyOfAxioms(@Nonnull OWLAnnotationProperty property) {
        return axioms(Filters.subAnnotationWithSub, property);
    }

    @Override
    public Stream<OWLAnnotationPropertyDomainAxiom> annotationPropertyDomainAxioms(@Nonnull OWLAnnotationProperty property) {
        return axioms(Filters.apDomainFilter, property);
    }

    @Override
    public Stream<OWLAnnotationPropertyRangeAxiom> annotationPropertyRangeAxioms(@Nonnull OWLAnnotationProperty property) {
        return axioms(Filters.apRangeFilter, property);
    }

    @Override
    public Stream<OWLDatatypeDefinitionAxiom> datatypeDefinitions(@Nonnull OWLDatatype datatype) {
        return axioms(Filters.datatypeDefFilter, datatype);
    }

    @Override
    public int getAxiomCount() {
        return (int) axioms().count();
    }

    @Override
    public int getAxiomCount(@Nonnull Imports imports) {
        return imports.stream(this).mapToInt(OWLAxiomCollection::getAxiomCount).sum();
    }

    @Override
    public <T extends OWLAxiom> int getAxiomCount(@Nonnull AxiomType<T> axiomType) {
        return (int) axioms(axiomType).count();
    }

    @Override
    public <T extends OWLAxiom> int getAxiomCount(@Nonnull AxiomType<T> axiomType, @Nonnull Imports imports) {
        return imports.stream(this).mapToInt(o -> o.getAxiomCount(axiomType)).sum();
    }

    @Override
    public int getLogicalAxiomCount() {
        return (int) logicalAxioms().count();
    }

    @Override
    public int getLogicalAxiomCount(@Nonnull Imports imports) {
        return imports.stream(this).mapToInt(OWLAxiomCollection::getLogicalAxiomCount).sum();
    }

    @Override
    public boolean containsAxiom(@Nullable OWLAxiom axiom) {
        return base.axioms().anyMatch(a -> a.equals(axiom));
    }

    @Override
    public boolean containsAxiom(@Nonnull OWLAxiom axiom, @Nonnull Imports imports, @Nonnull AxiomAnnotations ignoreAnnotations) {
        return imports.stream(this).anyMatch(o -> ignoreAnnotations.contains(o, axiom));
    }

    @Override
    public boolean containsAxiomIgnoreAnnotations(@Nonnull OWLAxiom axiom) {
        return containsAxiom(axiom) || axioms(axiom.getAxiomType()).anyMatch(ax -> ax.equalsIgnoreAnnotations(axiom));
    }

    @SuppressWarnings("RedundantStreamOptionalCall") // java8 compile bug on map
    @Override
    public boolean contains(@Nonnull OWLAxiomSearchFilter filter, @Nonnull Object key) {
        return base.axioms(StreamSupport.stream(filter.getAxiomTypes().spliterator(), false)
                .map(type -> type)
                .collect(Collectors.toSet())).anyMatch(a -> filter.pass(a, key));
    }

    @Override
    public boolean contains(@Nonnull OWLAxiomSearchFilter filter, @Nonnull Object key, @Nonnull Imports imports) {
        return imports.stream(this).anyMatch(o -> o.contains(filter, key));
    }

    /*
     * ======================
     * Serialization methods:
     * ======================
     */

    /**
     * Reads the object while serialization.
     * Note: only base graph!
     *
     * @param in {@link ObjectInputStream}
     * @throws IOException            if an I/O error occurs.
     * @throws ClassNotFoundException if the class of a serialized object could not be found.
     * @see OntologyManagerImpl#readObject(ObjectInputStream)
     * @see OntologyModelImpl.Concurrent#readObject(ObjectInputStream)
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        Graph base = new GraphMem();
        RDFDataMgr.read(base, in, DEFAULT_SERIALIZATION_FORMAT.getLang());
        // set temporary model with default personality, it will be reset inside manager while its #readObject
        setBase(new InternalModel(base, ConfigProvider.DEFAULT_CONFIG));
    }

    /**
     * Writes the object while serialization.
     * Note: only the base graph is serialized,
     * so if you serialize and then de-serialize standalone ontology it will loss all its references,
     * please use managers serialization, it will restore any links.
     * Also please note: an exception is expected if the encapsulated graph is not {@link GraphMem}.
     *
     * @param out {@link ObjectOutputStream}
     * @throws IOException if I/O errors occur while writing to the underlying <code>OutputStream</code>
     * @throws OntApiException in case this instance encapsulates graph which is not stored in memory
     */
    private void writeObject(ObjectOutputStream out) throws IOException, OntApiException {
        Graph g = base.getBaseGraph();
        if (!(g instanceof GraphMem))
            throw new OntApiException(ontologyID + ":: Serialization is not supported for non-memory graphs.");
        out.defaultWriteObject();
        // serialize only base graph (it will be wrapped as UnionGraph):
        RDFDataMgr.write(out, g, DEFAULT_SERIALIZATION_FORMAT.getLang());
    }

    /**
     * Overridden {@link OWLObjectImpl#toString()} in order not to force the axioms loading.
     * For brief information there should be a separate method and the original implementation of toString is not very good idea in our case.
     *
     * @return String
     */
    @Override
    public String toString() {
        return String.format("Ontology(%s)", ontologyID);
    }
}

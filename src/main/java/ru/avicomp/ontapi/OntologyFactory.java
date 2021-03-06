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
import org.semanticweb.owlapi.io.OWLOntologyDocumentSource;
import org.semanticweb.owlapi.model.*;
import ru.avicomp.ontapi.config.OntLoaderConfiguration;

import javax.annotation.Nonnull;
import java.io.Serializable;

/**
 * The factory to create and load ontologies to the manager.
 * It is the core of the system, an extended {@link OWLOntologyFactory}.
 * An implementation of this interface can be divided into two different parts:
 * {@link Builder Builder}, which is responsible for creating fresh ontologies,
 * and {@link Loader Loader}, which is responsible to load ontologies into manager.
 * Created by @szuev
 *
 * @see Builder
 * @see Loader
 */
public interface OntologyFactory extends OWLOntologyFactory {

    /**
     * Creates a fresh {@link OntologyModel Ontology Model} with the given ID,
     * the return model is attached to the specified manager.
     *
     * @param manager {@link OntologyManager} the ontology manager to set, not null
     * @param id      {@link OWLOntologyID} the ID of the ontology to create, not null
     * @return {@link OntologyModel}
     * @throws OntApiException if something goes wrong
     * @since 1.3.0
     */
    OntologyModel createOntology(OntologyManager manager, OWLOntologyID id) throws OntApiException;

    /**
     * Reads a graph from the given document source and stores it as {@link OntologyModel Ontology Model} in the specified manager.
     *
     * @param manager {@link OntologyManager} manager the ontology manager to set, not null
     * @param source  {@link OWLOntologyDocumentSource} the document source that provides the means
     *                of getting a representation of a document, not null
     * @param config  {@link OntLoaderConfiguration} settings to manage loading process, not null
     * @return {@link OntologyModel}
     * @throws OWLOntologyCreationException if the ontology could not be created due to some I/O problem,
     *                                      broken source or incompatible state of manager
     * @throws OntApiException              if something else goes wrong
     * @since 1.3.0
     */
    OntologyModel loadOntology(OntologyManager manager,
                               OWLOntologyDocumentSource source,
                               OntLoaderConfiguration config) throws OWLOntologyCreationException, OntApiException;

    /**
     * Determines if the factory can create an ontology for the specified ontology document IRI.
     * It's a filter method, by default it is allowed to create ontology for any document IRI.
     *
     * @param iri {@link IRI} the document IRI
     * @return {@code true} if the factory is allowed create an ontology given the specified document IRI or
     * {@code false} if the factory cannot handle such IRI
     */
    @Override
    default boolean canCreateFromDocumentIRI(@Nonnull IRI iri) {
        return true;
    }

    /**
     * Determines if the factory can load an ontology for the specified input source.
     * It's a filter method, by default it is allowed to read ontology from any document source.
     *
     * @param source {@link OWLOntologyDocumentSource} the input source from which to load the ontology
     * @return {@code false} if the factory is not suitable for the specified document source or
     * {@code true} if it is allowed to try to handle that source
     */
    @Override
    default boolean canAttemptLoading(@Nonnull OWLOntologyDocumentSource source) {
        return true;
    }

    /**
     * Creates an (empty) ontology.
     * Notices this method is not throwing checked {@link OWLOntologyCreationException} exception.
     * In most cases it does not work with resources, so there is no need in a checked exception,
     * RuntimeException is enough in our case.
     *
     * @param manager     the ontology manager to set, must be {@link OntologyManager}, not null
     * @param id          {@link OWLOntologyID} the ID of the ontology to create, not null
     * @param documentIRI unused parameter
     * @param handler     unused parameter
     * @return {@link OntologyModel} the newly created ontology
     * @throws OntApiException if something goes wrong
     */
    @Override
    default OntologyModel createOWLOntology(@Nonnull OWLOntologyManager manager,
                                            @Nonnull OWLOntologyID id,
                                            @Nonnull IRI documentIRI,
                                            @Nonnull OWLOntologyCreationHandler handler) {
        return createOntology(OWLAdapter.get().asONT(manager), id);
    }

    /**
     * Reads a graph from the given document source and stores it as Graph based {@link OWLOntology} in the specified manager.
     *
     * @param manager the ontology manager to set, must be {@link OntologyManager}, not null
     * @param source  {@link OWLOntologyDocumentSource} the document source that provides
     *                the means of getting a representation of a document, not null
     * @param handler unused parameter
     * @param config  {@link OWLOntologyLoaderConfiguration} a configuration object which can be used
     *                to pass various options to th loader, not null
     * @return {@link OntologyModel} the newly created and loaded ontology
     * @throws OWLOntologyCreationException if the ontology could not be created due to some I/O problem,
     *                                      broken source or incompatible state of manager
     * @throws OntApiException              if something else goes wrong
     */
    @Override
    default OntologyModel loadOWLOntology(@Nonnull OWLOntologyManager manager,
                                          @Nonnull OWLOntologyDocumentSource source,
                                          @Nonnull OWLOntologyCreationHandler handler,
                                          @Nonnull OWLOntologyLoaderConfiguration config) throws OWLOntologyCreationException {
        return loadOntology(OWLAdapter.get().asONT(manager), source, OWLAdapter.get().asONT(config));
    }

    /**
     * A part of the factory, which is responsible for creating {@link Graph Graph} based ontologies.
     * Notice that it produces standalone empty ontologies, which is not associated with any manager,
     * and therefore cannot be used until construction is finished
     *
     * @see OWLOntologyBuilder
     */
    interface Builder extends OWLOntologyBuilder {

        /**
         * Creates a new detached ontology, which is related to the specified manager.
         * Does not change the manager state, although the return ontology will have a reference to it.
         *
         * @param manager {@link OntologyManager}, not null
         * @param id      {@link OWLOntologyID}, not null
         * @return {@link OntologyModel} new instance reflecting manager settings
         */
        OntologyModel createOntology(OntologyManager manager, OWLOntologyID id);

        /**
         * Creates a new detached ontology model based on the specified graph.
         * Does not change the manager state, although the result ontology will have a reference to it.
         *
         * @param graph   {@link Graph} the graph
         * @param manager {@link OntologyManager} manager
         * @param config  {@link OntLoaderConfiguration} the config
         * @return {@link OntologyModel} new instance reflecting manager settings
         * @since 1.3.0
         */
        OntologyModel createOntology(Graph graph, OntologyManager manager, OntLoaderConfiguration config);

        /**
         * Makes a fresh Graph.
         *
         * @return {@link Graph Jena Graph}
         * @since 1.3.0
         */
        Graph createGraph();

        @Override
        default OntologyModel createOWLOntology(@Nonnull OWLOntologyManager manager, @Nonnull OWLOntologyID id) {
            return createOntology(OWLAdapter.get().asONT(manager), id);
        }
    }

    /**
     * A part of the factory, which is responsible for reading ontologies from different document sources.
     * Notice that the single method requires three input parameters (i.e. {@link OWLOntologyDocumentSource},
     * {@link OntologyManager}, {@link OWLOntologyLoaderConfiguration}),
     * while the method {@link OWLOntologyFactory#loadOWLOntology} requires four.
     * This is due to the fact that {@link OntologyManagerImpl} is an {@link OWLOntologyManager} as well as {@link OWLOntologyCreationHandler}.
     * And this is also true for <a href='https://github.com/owlcs/owlapi/blob/version5/impl/src/main/java/uk/ac/manchester/cs/owl/owlapi/OWLOntologyManagerImpl.java'>uk.ac.manchester.cs.owl.owlapi.OWLOntologyManagerImpl</a>.
     * Therefore, the {@link OWLOntologyCreationHandler} can be considered just as part of internal (OWL-API) implementation
     * and so there is no need in this parameter in our single case.
     */
    interface Loader extends Serializable {

        /**
         * The base method to load ontology model ({@link OntologyModel}) to the manager ({@link OntologyManager}).
         * If the document source corresponds an ontology that has imports,
         * these imports are also treated as sources, and are populated in the manager as ontologies.
         * To handle correctly an IRI document sources,
         * the manager's {@link OWLOntologyIRIMapper IRI Mapper}
         * and {@link OntologyManager.DocumentSourceMapping Graph Mapper} are used.
         * In case of error the manager state should not change.
         *
         * @param source  {@link OWLOntologyDocumentSource} the source (iri, file iri, stream or who knows what), not null
         * @param manager {@link OntologyManager}, the manager, not null
         * @param conf    {@link OntLoaderConfiguration}, the load settings, not null
         * @return {@link OntologyModel} the result model inside the manager.
         * @throws OWLOntologyCreationException if something wrong
         * @see OWLOntologyIRIMapper
         * @see OntologyManager.DocumentSourceMapping
         */
        OntologyModel load(OWLOntologyDocumentSource source,
                           OntologyManager manager,
                           OntLoaderConfiguration conf) throws OWLOntologyCreationException;

    }
}

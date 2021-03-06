/*
 * This file is part of the ONT API.
 * The contents of this file are subject to the LGPL License, Version 3.0.
 * Copyright (c) 2019, Avicomp Services, AO
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Alternatively, the contents of this file may be used under the terms of the Apache License, Version 2.0 in which case, the provisions of the Apache License Version 2.0 are applicable instead of those above.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package ru.avicomp.ontapi.jena.model;

import java.util.Collection;
import java.util.stream.Stream;

/**
 * Interface encapsulating an anonymous resource-collection of objects in an ontology with one of the following types:
 * {@code owl:AllDisjointProperties}, {@code owl:AllDisjointClasses}, {@code owl:AllDifferent}).
 * The resource is looks like this example:
 * {@code _:x rdf:type owl:AllDisjointProperties; _:x owl:members ( R1 ... Rn ).}.
 * <p>
 * Created by @szuev on 15.11.2016.
 *
 * @param <O> {@link OntIndividual individual}, {@link OntCE class expression}, {@link OntOPE object property expression} or {@link OntNDP data property}
 */
public interface OntDisjoint<O extends OntObject> extends OntObject, HasRDFNodeList<O> {

    /**
     * Lists all pair-wise disjoint members holding by this {@link OntDisjoint Ontology Disjoint} resource.
     * In general, this method is equivalent to the expression {@code this.getList().members()}.
     *
     * @return Stream (<b>not distinct</b>) of {@link OntObject}s
     */
    default Stream<O> members() {
        return getList().members();
    }

    /**
     * @see <a href='https://www.w3.org/TR/owl2-syntax/#Disjoint_Classes'>9.1.3 Disjoint Classes</a>
     * @see OntGraphModel#createDisjointClasses(Collection)
     */
    interface Classes extends OntDisjoint<OntCE>, SetComponents<OntCE, Classes> {
    }

    /**
     * @see <a href='https://www.w3.org/TR/owl2-syntax/#Individual_Inequality'>9.6.2 Individual Inequality</a>
     * @see OntGraphModel#createDifferentIndividuals(Collection)
     */
    interface Individuals extends OntDisjoint<OntIndividual>, SetComponents<OntIndividual, Individuals> {

        /**
         * Gets an {@link OntList ONT-List}.
         * Since both predicates {@link ru.avicomp.ontapi.jena.vocabulary.OWL#members owl:members} and
         * {@link ru.avicomp.ontapi.jena.vocabulary.OWL#distinctMembers owl:distinctMembers} are allowed by specification,
         * this method returns most bulky list.
         * In case both lists have the same dimension, the method choose one that is on predicate {@code owl:members}.
         * The method {@link OntGraphModel#createDifferentIndividuals(Collection)} also prefers {@code owl:members} predicate.
         * This was done for reasons of uniformity.
         *
         * @return {@link OntList ONT-List} of {@link OntIndividual individual}s
         * @see OntGraphModel#createDifferentIndividuals(Collection)
         * @since 1.3.0
         */
        @Override
        OntList<OntIndividual> getList();

        /**
         * Lists all members from []-list on predicate {@link ru.avicomp.ontapi.jena.vocabulary.OWL#members owl:members}
         * with concatenation all members from []-list
         * on predicate {@link ru.avicomp.ontapi.jena.vocabulary.OWL#distinctMembers owl:distinctMembers}.
         *
         * @return <b>not distinct</b> Stream of {@link OntIndividual individual}s
         */
        @Override
        Stream<OntIndividual> members();
    }

    /**
     * @see <a href='https://www.w3.org/TR/owl2-syntax/#Disjoint_Object_Properties'>9.2.3 Disjoint Object Properties</a>
     * @see OntGraphModel#createDisjointObjectProperties(Collection)
     */
    interface ObjectProperties extends Properties<OntOPE>, SetComponents<OntOPE, ObjectProperties> {
    }

    /**
     * @see <a href='https://www.w3.org/TR/owl2-syntax/#Disjoint_Data_Properties'>9.3.3 Disjoint Data Properties</a>
     * @see OntGraphModel#createDisjointDataProperties(Collection)
     */
    interface DataProperties extends Properties<OntNDP>, SetComponents<OntNDP, DataProperties> {
    }

    /**
     * Abstraction for Pairwise Disjoint Properties anonymous {@link OntObject Ontology Object}.
     *
     * @param <P> either {@link OntOPE object property expression} or {@link OntNDP data property}
     */
    interface Properties<P extends OntPE> extends OntDisjoint<P> {
    }
}

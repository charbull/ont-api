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
package ru.avicomp.owlapi.tests.api.annotations;

import org.junit.Test;
import org.semanticweb.owlapi.model.*;
import ru.avicomp.owlapi.tests.api.baseclasses.TestBase;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.contains;
import static ru.avicomp.owlapi.OWLFunctionalSyntaxFactory.Class;
import static ru.avicomp.owlapi.OWLFunctionalSyntaxFactory.*;

/**
 * @author Matthew Horridge, The University of Manchester, Information
 *         Management Group
 * @since 3.0.0
 */
@SuppressWarnings("javadoc")
public class GetAxiomsIgnoringAnnotationsTestCase extends TestBase {

    @Test
    public void testGetAxiomsIgnoringAnnoations() {
        OWLLiteral annoLiteral = Literal("value");
        OWLAnnotationProperty annoProp = AnnotationProperty(iri("annoProp"));
        OWLAnnotation anno = df.getOWLAnnotation(annoProp, annoLiteral);
        OWLAxiom axiom = df.getOWLSubClassOfAxiom(Class(iri("A")),
                Class(iri("B")), singleton(anno));
        OWLOntology ont = getOWLOntology();
        ont.getOWLOntologyManager().addAxiom(ont, axiom);
        assertTrue(contains(ont.axiomsIgnoreAnnotations(axiom), axiom));
        OWLAxiom noAnnotations = axiom.getAxiomWithoutAnnotations();
        assertFalse(contains(ont.axiomsIgnoreAnnotations(axiom), noAnnotations));
        assertTrue(contains(ont.axiomsIgnoreAnnotations(noAnnotations),
                axiom));
        assertFalse(contains(
                ont.axiomsIgnoreAnnotations(noAnnotations), noAnnotations));
    }
}

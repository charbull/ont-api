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

package ru.avicomp.ontapi.tests.jena;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.jena.OntJenaException;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

/**
 * To test {@link OntIndividual}.
 * <p>
 * Created by @ssz on 11.05.2019.
 */
public class OntIndividualTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(OntIndividualTest.class);

    @Test
    public void testPositiveAssertions() {
        OntGraphModel m = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD);
        OntIndividual i1 = m.createIndividual("I1");
        OntIndividual i2 = m.createIndividual("I2");
        OntNDP d = m.createDataProperty("D");
        OntNOP p = m.createObjectProperty("P");
        OntNAP a = m.getRDFSComment();

        Assert.assertSame(i1, i1.addAssertion(d, m.createLiteral("1"))
                .addAssertion(d, m.createLiteral("2")).addAssertion(p, i2).addAssertion(a, m.createLiteral("3")));
        Assert.assertEquals(4, i1.positiveAssertions().count());
        Assert.assertEquals(2, i1.positiveAssertions(d).count());
        Assert.assertEquals(8, m.size());

        Assert.assertSame(i1, i1.removeAssertion(d, null).removeAssertion(p, i2));
        Assert.assertEquals(1, i1.positiveAssertions().count());
        Assert.assertSame(i1, i1.removeAssertion(null, null));
        Assert.assertEquals(0, i1.positiveAssertions().count());
        Assert.assertEquals(4, m.size());
    }

    @Test
    public void testDifferentIndividuals() {
        OntGraphModel m = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD);
        OntIndividual a = m.createIndividual("A");
        OntIndividual b = m.createOntClass("C1").createIndividual("B");
        OntIndividual c = m.createOntClass("C2").createIndividual();
        OntIndividual d = m.createIndividual("D");

        Assert.assertNotNull(a.addDifferentFromStatement(b));
        Assert.assertSame(a, a.addDifferentIndividual(c).addDifferentIndividual(d).removeDifferentIndividual(b));
        Assert.assertEquals(2, a.differentIndividuals().count());
        Assert.assertSame(a, a.removeDifferentIndividual(null));
        Assert.assertEquals(7, m.size());
    }

    @Test
    public void testSameIndividuals() {
        OntGraphModel m = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD);
        OntIndividual a = m.createIndividual("A");
        OntIndividual b = m.createOntClass("C1").createIndividual("B");
        OntIndividual c = m.createOntClass("C2").createIndividual();
        OntIndividual d = m.createIndividual("D");

        Assert.assertNotNull(a.addSameAsStatement(b));
        Assert.assertSame(a, a.addSameIndividual(c).addSameIndividual(d).removeSameIndividual(b));
        Assert.assertEquals(2, a.sameIndividuals().count());
        Assert.assertSame(a, a.removeSameIndividual(null));
        Assert.assertEquals(7, m.size());
    }

    @Test
    public void testNegativeAssertions() {
        OntGraphModel m = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD);
        OntIndividual i1 = m.createIndividual("I1");
        OntIndividual i2 = m.createIndividual("I2");
        OntIndividual i3 = m.createIndividual("I3");
        OntNDP d = m.createDataProperty("D");
        OntNOP p = m.createObjectProperty("P");

        Assert.assertSame(i1, i1.addNegativeAssertion(d, m.createLiteral("1")));
        Assert.assertEquals(1, i1.negativeAssertions().count());
        Assert.assertEquals(0, i1.positiveAssertions().count());
        Assert.assertSame(i1, i1.addNegativeAssertion(d, m.createLiteral("2"))
                .addNegativeAssertion(p, i2).addNegativeAssertion(p, i3));
        Assert.assertEquals(4, i1.negativeAssertions().count());
        Assert.assertEquals(0, i1.positiveAssertions().count());

        Assert.assertEquals(2, m.statements(null, OWL.targetIndividual, null).count());
        Assert.assertEquals(2, m.statements(null, OWL.targetValue, null).count());
        Assert.assertEquals(21, m.size());

        Assert.assertSame(i1, i1.removeNegativeAssertion(d, null).removeNegativeAssertion(p, i3));
        Assert.assertEquals(1, i1.negativeAssertions().count());
        Assert.assertEquals(1, m.statements(null, OWL.targetIndividual, null).count());
        Assert.assertEquals(0, m.statements(null, OWL.targetValue, null).count());

        Assert.assertSame(i1, i1.removeNegativeAssertion(null, null));
        Assert.assertEquals(5, m.size());
    }

    @Test
    public void testRemoveIndividual() {
        OntGraphModel m = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD);
        OntIndividual i1 = m.createIndividual("I1");
        OntIndividual i2 = m.createIndividual("I2");
        OntIndividual i3 = m.createIndividual("I3");
        OntNDP d = m.createDataProperty("D");
        OntNOP p = m.createObjectProperty("P");

        i1.addNegativeAssertion(p, i2)
                .addAssertion(p, i3)
                .addNegativeAssertion(d, m.createLiteral("1"))
                .addAssertion(d, m.createLiteral("2")).addComment("The individual to test");
        Assert.assertEquals(16, m.size());

        Assert.assertEquals(4, m.removeOntObject(i1).size());
    }

    @Test
    public void testClassAssertions() {
        OntGraphModel m = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD);
        OntIndividual i1 = m.createIndividual("I1");
        OntClass c1 = m.createOntClass("C1");
        OntClass c2 = m.createOntClass("C2");
        OntIndividual i2 = c2.createIndividual();
        long size = 4;

        Assert.assertSame(i1, i1.detachClass(c1).detachClass(m.getOWLThing()));
        Assert.assertEquals(size, m.size());

        Assert.assertNotNull(i1.addClassAssertion(c2));
        Assert.assertSame(i1, i1.attachClass(c2).attachClass(c1).attachClass(m.getOWLThing()));
        Assert.assertEquals(3, i1.classes().count());
        Assert.assertEquals(size + 3, m.size());

        Assert.assertSame(i1, i1.detachClass(c2));
        Assert.assertEquals(size + 2, m.size());
        Assert.assertEquals(2, i1.classes().count());
        Assert.assertSame(i1, i1.detachClass(null));
        Assert.assertEquals(size, m.size());
        Assert.assertEquals(0, i1.classes().count());

        Assert.assertSame(i2, i2.attachClass(m.getOWLThing()).attachClass(c1).attachClass(c2));
        Assert.assertEquals(size + 2, m.size());
        Assert.assertEquals(3, i2.classes().count());

        // not possible to delete all class assertions:
        try {
            i2.detachClass(null);
            Assert.fail("Possible to delete all class expressions");
        } catch (OntJenaException j) {
            LOGGER.debug("Expected: {}", j.getMessage());
        }
        Assert.assertEquals(size + 2, m.size());
        Assert.assertSame(i2, i2.detachClass(c2).detachClass(m.getOWLThing()));
        Assert.assertEquals(1, i2.classes().count());
        Assert.assertEquals(size, m.size());

        // not possible to delete the last class assertions:
        try {
            i2.detachClass(c1);
            Assert.fail("Possible to delete the last class expressions");
        } catch (OntJenaException j) {
            LOGGER.debug("Expected: {}", j.getMessage());
        }
        Assert.assertEquals(size, m.size());
    }
}

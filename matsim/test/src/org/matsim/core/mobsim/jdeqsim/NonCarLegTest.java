package org.matsim.core.mobsim.jdeqsim;

import java.util.LinkedList;

import org.matsim.core.api.experimental.events.PersonEvent;
import org.matsim.core.events.ActivityEndEventImpl;
import org.matsim.core.events.ActivityStartEventImpl;
import org.matsim.core.events.AgentArrivalEventImpl;
import org.matsim.core.events.AgentDepartureEventImpl;
import org.matsim.core.mobsim.jdeqsim.util.TestHandlerDetailedEventChecker;
import org.matsim.core.population.PopulationImpl;
import org.matsim.testcases.MatsimTestCase;

public class NonCarLegTest extends MatsimTestCase {

	public void test_EmptyCarRoute() {
		// reusing assertions from empty car leg test (as output is the same)
		EmptyCarLegTest nonCarLegTest = new EmptyCarLegTest();
		nonCarLegTest.startTestDES("test/input/org/matsim/core/mobsim/jdeqsim/config2.xml", false, null, null);
	}

}

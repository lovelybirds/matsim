package org.matsim.core.mobsim.jdeqsim.util;

import java.util.HashMap;
import java.util.LinkedList;

import org.matsim.api.basic.v01.Id;
import org.matsim.api.basic.v01.TransportMode;
import org.matsim.api.core.v01.ScenarioImpl;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.api.experimental.events.PersonEvent;
import org.matsim.core.api.experimental.events.handler.PersonEventHandler;
import org.matsim.core.config.Config;
import org.matsim.core.events.ActivityEndEventImpl;
import org.matsim.core.events.ActivityStartEventImpl;
import org.matsim.core.events.AgentArrivalEventImpl;
import org.matsim.core.events.AgentDepartureEventImpl;
import org.matsim.core.events.AgentWait2LinkEventImpl;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.LinkEnterEventImpl;
import org.matsim.core.events.LinkLeaveEventImpl;
import org.matsim.core.events.parallelEventsHandler.ParallelEvents;
import org.matsim.core.mobsim.jdeqsim.JDEQSimulation;
import org.matsim.core.network.NetworkLayer;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.PopulationImpl;
import org.matsim.core.population.routes.NetworkRouteWRefs;
import org.matsim.core.scenario.ScenarioLoaderImpl;
import org.matsim.testcases.MatsimTestCase;

public class TestHandlerDetailedEventChecker extends MatsimTestCase implements PersonEventHandler {

	protected HashMap<Id, LinkedList<PersonEvent>> events = new HashMap<Id, LinkedList<PersonEvent>>();
	public LinkedList<PersonEvent> allEvents = new LinkedList<PersonEvent>();
	// private HashMap<Id, ExpectedNumberOfEvents> expectedNumberOfMessages =
	// new HashMap<Id, ExpectedNumberOfEvents>();
	protected boolean printEvent = true;

	public void checkAssertions(final PopulationImpl population) {

		// at least one event
		assertTrue(events.size() > 0);

		// all events of one agent must have ascending time stamps
		double lastTimeStamp;
		for (LinkedList<PersonEvent> list : events.values()) {
			lastTimeStamp = Double.NEGATIVE_INFINITY;
			for (int i = 0; i < list.size(); i++) {
				if (lastTimeStamp > list.get(i).getTime()) {
					for (int j = 0; j < list.size(); j++) {
						System.out.println(list.get(j).toString());
					}
					System.out.println(lastTimeStamp);
					System.out.println(list.get(i).getTime());
					fail("Messages are not arriving in a consistent manner.");
				}

				assertTrue(lastTimeStamp <= list.get(i).getTime());
				lastTimeStamp = list.get(i).getTime();
			}
		}

		// compare plan and events for each agent
		// compare: type of events, linkId
		for (LinkedList<PersonEvent> list : events.values()) {
			Person p = population.getPersons().get(list.get(0).getPersonId());
			// printEvents(list.get(0).agentId);
			Plan plan = p.getSelectedPlan();
			int index = 0;

			ActivityImpl act = null;
			LegImpl leg = null;
			for (PlanElement pe : plan.getPlanElements()) {
				if (pe instanceof ActivityImpl) {
					act = (ActivityImpl) pe;

					if (leg != null) {
						// each leg ends with enter on act link
						// => only for non empty car legs and non-cars legs this
						// statement is true
						if (leg.getMode().equals(TransportMode.car) && ((NetworkRouteWRefs) leg.getRoute()).getLinks().size() > 0) {
							assertTrue(list.get(index) instanceof LinkEnterEventImpl);
							assertTrue(act.getLinkId().toString().equalsIgnoreCase(
									((LinkEnterEventImpl) list.get(index)).getLinkId().toString()));
							index++;
						}

						// each leg ends with arrival on act link
						assertTrue(list.get(index) instanceof AgentArrivalEventImpl);
						assertTrue(act.getLinkId().toString().equalsIgnoreCase(
								((AgentArrivalEventImpl) list.get(index)).getLinkId().toString()));
						index++;

						// each leg ends with arrival on act link
						assertTrue(list.get(index) instanceof ActivityStartEventImpl);
						assertEquals(act.getLinkId(), ((ActivityStartEventImpl) list.get(index)).getLinkId());
						index++;
					}
				} else if (pe instanceof LegImpl) {
					leg = (LegImpl) pe;

					// act end event
					assertTrue(list.get(index) instanceof ActivityEndEventImpl);
					assertEquals(act.getLinkId(), ((ActivityEndEventImpl) list.get(index)).getLinkId());
					index++;

					// each leg starts with departure on act link
					assertTrue(list.get(index) instanceof AgentDepartureEventImpl);
					assertTrue(act.getLinkId().toString().equalsIgnoreCase(
							((AgentDepartureEventImpl) list.get(index)).getLinkId().toString()));
					index++;

					// each CAR leg must enter/leave act link
					if (leg.getMode().equals(TransportMode.car)) {

						// if car leg contains empty route, then this check is
						// not applicable
						if (((NetworkRouteWRefs) leg.getRoute()).getLinks().size() > 0) {
							// the first LinkEnterEvent is a AgentWait2LinkEvent
							assertTrue(list.get(index) instanceof AgentWait2LinkEventImpl);
							assertTrue(act.getLinkId().toString().equalsIgnoreCase(
									((AgentWait2LinkEventImpl) list.get(index)).getLinkId().toString()));
							index++;

							assertTrue(list.get(index) instanceof LinkLeaveEventImpl);
							assertTrue(act.getLinkId().toString().equalsIgnoreCase(
									((LinkLeaveEventImpl) list.get(index)).getLinkId().toString()));
							index++;
						}

						for (Link link : ((NetworkRouteWRefs) leg.getRoute()).getLinks()) {
							// enter link and leave each link on route
							assertTrue(list.get(index) instanceof LinkEnterEventImpl);
							assertTrue(link.getId().toString().equalsIgnoreCase(
									((LinkEnterEventImpl) list.get(index)).getLinkId().toString()));
							index++;

							assertTrue(list.get(index) instanceof LinkLeaveEventImpl);
							assertTrue(link.getId().toString().equalsIgnoreCase(
									((LinkLeaveEventImpl) list.get(index)).getLinkId().toString()));
							index++;
						}
					}

				}
			}
		}
	}

	public void handleEvent(PersonEvent event) {
		if (!events.containsKey(event.getPersonId())) {
			events.put(event.getPersonId(), new LinkedList<PersonEvent>());
		}
		events.get(event.getPersonId()).add(event);
		if (printEvent) {
			System.out.println(event.toString());
		}
		allEvents.add(event);
	}

	public void reset(int iteration) {
	}

	// if populationModifier == null, then the DummyPopulationModifier is used
	// if planFilePath == null, then the plan specified in the config file is
	// used
	public void startTestDES(String configFilePath, boolean printEvent, String planFilePath, PopulationModifier populationModifier) {
		Config config = loadConfig(configFilePath);
		if (planFilePath != null) {
			config.plans().setInputFile(planFilePath);
		}
		this.printEvent = printEvent;
		ScenarioLoaderImpl loader = new ScenarioLoaderImpl(config);
		loader.loadScenario();
		ScenarioImpl data = loader.getScenario();
		NetworkLayer network = (NetworkLayer) data.getNetwork();
		PopulationImpl population = data.getPopulation();
		if (populationModifier != null) {
			population = populationModifier.modifyPopulation(population);
		}
		EventsManagerImpl events = new ParallelEvents(1);
		events.addHandler(this);
		events.initProcessing();
		new JDEQSimulation(network, population, events).run();
		events.finishProcessing();

		// this.calculateExpectedNumberOfEvents(population); // this method
		// doesn't do anything useful/stateful
		this.checkAssertions(population);
	}
}

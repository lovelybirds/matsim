/* *********************************************************************** *
 * project: org.matsim.*
 * EventsToScore.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007, 2008 by the members listed in the COPYING,  *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.core.scoring;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.*;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.internal.HasPersonId;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.population.PersonImpl;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.io.IOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Calculates the score of the selected plans of a given scenario
 * based on events. The final scores are written to the selected plans of each person in the
 * scenario.
 * 
 * This class is the bridge between a stream of Events, the ScoringFunctionFactory and the Plan database.
 * This mechanism is considered core to MATSim, and changing it is not supported, except of course
 * by providing your own ScoringFunctionFactory.
 * 
 * Therefore, this class is instantiated and used by the Controler. Create your own instance if you want
 * to compute scores from an Event file, for example. You will still need a Scenario with proper selected
 * Plans, though. This is not yet fully decoupled.
 *
 * @author mrieser, michaz
 */
public class EventsToScore implements BasicEventHandler {
	static private final Logger log = Logger.getLogger(EventsToScore.class);

	private EventsToActivities eventsToActivities;
	private EventsToLegs eventsToLegs;
	private ScoringFunctionsForPopulation scoringFunctionsForPopulation;
	private Scenario scenario;
	private ScoringFunctionFactory scoringFunctionFactory;
    private final Map<Id, ScoringFunction> scoringFunctions = new TreeMap<Id, ScoringFunction>();
    private double learningRate;
	private boolean finished = false;
	
	private int iteration = -1 ;

	private double scoreSum = 0.0;
	private long scoreCount = 0;
	private Integer scoreMSAstartsAtIteration;
	

	/**
	 * Initializes EventsToScore with a learningRate of 1.0.
	 */
	public EventsToScore(final Scenario scenario, final ScoringFunctionFactory factory) {
		this(scenario, factory, 1.0);
	}

	public EventsToScore(final Scenario scenario, final ScoringFunctionFactory scoringFunctionFactory, final double learningRate) {
		this.scenario = scenario;
		this.scoringFunctionFactory = scoringFunctionFactory;
		this.learningRate = learningRate;
		initHandlers(scoringFunctionFactory);
		this.scoreMSAstartsAtIteration = this.scenario.getConfig().vspExperimental().getScoreMSAStartsAtIteration() ;
	}

	private void initHandlers(final ScoringFunctionFactory factory) {
        for (Person person : scenario.getPopulation().getPersons().values()) {
            this.scoringFunctions.put(person.getId(), factory.createNewScoringFunction(person));
        }
        HashSet<PersonExperienceListenerProvider> personExperienceListenerProviders = new HashSet<PersonExperienceListenerProvider>();
        personExperienceListenerProviders.add(new PersonExperienceListenerProvider() {
            @Override
            public PersonExperienceListener provideFor(Person person) {
                return scoringFunctions.get(person.getId());
            }
        });
        this.scoringFunctionsForPopulation = new ScoringFunctionsForPopulation(scenario, personExperienceListenerProviders);
        this.eventsToActivities = new EventsToActivities();
		this.eventsToActivities.setActivityHandler(this.scoringFunctionsForPopulation);
		this.eventsToLegs = new EventsToLegs(this.scenario);
		this.eventsToLegs.setLegHandler(this.scoringFunctionsForPopulation);
	}

	@Override
	public void handleEvent(Event event) {
		// this is for the activity and leg related stuff ("old" scoring function)
		if ( event instanceof LinkEnterEvent ) {
			eventsToLegs.handleEvent((LinkEnterEvent) event) ;
		} else if ( event instanceof LinkLeaveEvent ) {
			eventsToLegs.handleEvent((LinkLeaveEvent) event ) ;
		} else if ( event instanceof PersonDepartureEvent ) {
			eventsToLegs.handleEvent((PersonDepartureEvent) event) ;
		} else if ( event instanceof PersonArrivalEvent ) {
			eventsToLegs.handleEvent((PersonArrivalEvent) event ) ;
		} else if ( event instanceof ActivityStartEvent ) {
			eventsToActivities.handleEvent((ActivityStartEvent) event) ;
		} else if ( event instanceof ActivityEndEvent ) {
			eventsToActivities.handleEvent( (ActivityEndEvent) event ) ;
		} else if ( event instanceof TeleportationArrivalEvent ) {
			eventsToLegs.handleEvent( (TeleportationArrivalEvent) event ) ;
		} else if ( event instanceof PersonEntersVehicleEvent ) {
			eventsToLegs.handleEvent( (PersonEntersVehicleEvent) event) ;
		} else if ( event instanceof VehicleArrivesAtFacilityEvent ) {
			eventsToLegs.handleEvent( (VehicleArrivesAtFacilityEvent) event ) ;
		} else if ( event instanceof TransitDriverStartsEvent ) {
			eventsToLegs.handleEvent( (TransitDriverStartsEvent) event ) ;
		} 

		// this is for the stuff that is directly based on events.
		// note that this passes on _all_ person events, even those already passed above.
		// for the time being, not all PersonEvents may "implement HasPersonId".
		// link enter/leave events are NOT passed on, for performance reasons.
		// kai/dominik, dec'12
		if ( event instanceof HasPersonId ) {
			ScoringFunction sf = getScoringFunctionForAgent( ((HasPersonId)event).getPersonId());
			if (sf != null) {
				if ( event instanceof PersonStuckEvent ) {
					sf.agentStuck( event.getTime() ) ;
				} else if ( event instanceof PersonMoneyEvent ) {
					sf.addMoney( ((PersonMoneyEvent)event).getAmount() ) ;
				} else {
					sf.handleEvent( event ) ;
				}
			}
		}
	}


	/**
	 * Finishes the calculation of the plans' scores and assigns the new scores
	 * to the plans.
	 * I think this should be split into two methods: One can want to close the ScoringFunctions to look
	 * at scores WITHOUT wanting something to be written into Plans.
	 * Actually, I think the two belong in different classes. michaz '12
	 * <p/>
	 * yy Absolutely.  kai, oct'12
	 */
	public void finish() {
		eventsToActivities.finish();	
		scoringFunctionsForPopulation.finishScoringFunctions();
		assignNewScores();
		finished = true;
	}
	
	private void assignNewScores() {
		log.info("it: " + this.iteration + " msaStart: " + this.scoreMSAstartsAtIteration);
		for (Person person : scenario.getPopulation().getPersons().values()) {
			ScoringFunction sf = scoringFunctions.get(person.getId());
			double score = sf.getScore();
			Plan plan = person.getSelectedPlan();
			Double oldScore = plan.getScore();
			if (oldScore == null) {
				plan.setScore(score);
				if ( plan.getScore().isNaN() ) {
					log.warn("score is NaN; plan:" + plan.toString() );
				}
			} else {
				if ( this.scoreMSAstartsAtIteration == null || this.iteration < this.scoreMSAstartsAtIteration ) {
					final double newScore = this.learningRate * score + (1 - this.learningRate) * oldScore;
					if ( log.isTraceEnabled() ) { 
						log.trace( " lrn: " + this.learningRate + " oldScore: " + oldScore + " simScore: " + score + " newScore: " + newScore );
					}
					plan.setScore(newScore);
					if ( plan.getScore().isNaN() ) {
						log.warn("score is NaN; plan:" + plan.toString() );
					}
				} else {
					double alpha = 1./(this.iteration - this.scoreMSAstartsAtIteration + 1) ;
					alpha *= scenario.getConfig().strategy().getMaxAgentPlanMemorySize() ; //(**)
					if ( alpha>1 ) {
						alpha = 1. ;
					}
					final double newScore = alpha * score + (1.-alpha) * oldScore;
					if ( log.isTraceEnabled() ) {
						log.trace( " alpha: " + alpha + " oldScore: " + oldScore + " simScore: " + score + " newScore: " + newScore );
					}
					plan.setScore( newScore ) ;
					if ( plan.getScore().isNaN() ) {
						log.warn("score is NaN; plan:" + plan.toString() );
					}
					/*
					// the above is some variant of MSA (method of successive
					// averages). It is not the same as MSA since
					// a plan is typically not scored in every iteration.
					// However, plans are called with rates, for example
					// only every 10th iteration. Yet, something like 1/(10x)
					// still diverges in the same way as 1/x
					// when integrated, so MSA should still converge to the
					// correct result. kai, oct'12
					// The above argument may be correct.  But something 9/10*old+1/10*new is too slow.  Now
					// multiplying with number of plans (**) in hope that it is better.  (Where is the theory department?) kai, nov'13 
					// yyyy this has never been tested with scenarios :-(  .  At least there is a test case.  kai, oct'12
					// (In the meantime, I have used it in certain of my own 1% runs, e.g. Ivory Coast.)
					 */
				}
			}

			this.scoreSum += score;
			this.scoreCount++;
		}
	}

	

	/**
	 * Returns the actual average plans' score before it was assigned to the
	 * plan and possibility mixed with old scores (learningrate).
	 *
	 * @return the average score of the plans before mixing with the old scores
	 *         (learningrate)
	 */
	public double getAveragePlanPerformance() {
		if (this.scoreSum == 0)
			return Double.NaN;
		else
			return (this.scoreSum / this.scoreCount);
	}

	/**
	 * Returns the score of a single agent. This method only returns useful
	 * values if the method {@link #finish() } was called before. description
	 *
	 * @param agentId
	 *            The id of the agent the score is requested for.
	 * @return The score of the specified agent.
	 */
	public Double getAgentScore(final Id agentId) {
		if (!finished) {
			throw new IllegalStateException("Must call finish first.");
		}
		ScoringFunction scoringFunction = getScoringFunctionForAgent(agentId);
		if (scoringFunction == null)
			return null;
		return scoringFunction.getScore();
	}

	@Override
	public void reset(final int iteration) {
		this.eventsToActivities.reset(iteration);
		this.eventsToLegs.reset(iteration);
		initHandlers(scoringFunctionFactory);
		finished = false;
		this.iteration = iteration ;
		// ("reset" is called just before the mobsim starts, so it probably has the correct iteration number for our purposes) 
	}

	public ScoringFunction getScoringFunctionForAgent(Id agentId) {
		return scoringFunctions.get(agentId);
	}

	public Map<Id, Plan> getAgentRecords() {
		return scoringFunctionsForPopulation.getAgentRecords();
	}

	public void writeExperiencedPlans(String iterationFilename) {
        Population population = PopulationUtils.createPopulation(scenario.getConfig());
        for (Map.Entry<Id, Plan> entry : scoringFunctionsForPopulation.getAgentRecords().entrySet()) {
            PersonImpl person = new PersonImpl(entry.getKey());
            Plan plan = entry.getValue();
            plan.setScore(getScoringFunctionForAgent(person.getId()).getScore());
            person.addPlan(plan);
            population.addPerson(person);
            if (plan.getScore().isNaN()) {
                log.warn("score is NaN; plan:" + plan.toString());
            }
        }
        new PopulationWriter(population, scenario.getNetwork()).writeV5(iterationFilename + ".xml.gz");

        Map<Id, List<Double>> partialScores = new TreeMap<Id, List<Double>>();
        for (Map.Entry<Id, ScoringFunction> entry : this.scoringFunctions.entrySet()) {
            if (entry.getValue() instanceof SumScoringFunction) {
                partialScores.put(entry.getKey(), ((SumScoringFunction) entry.getValue()).getPartialScores());
            }
        }
        BufferedWriter out = IOUtils.getBufferedWriter(iterationFilename + "_scores.xml.gz");
        try {
            for (Map.Entry<Id,List<Double>> entry : partialScores.entrySet()) {
                out.write( entry.getKey().toString());
                for (Double score : entry.getValue()) {
                    out.write('\t'+ score.toString());
                }
                out.newLine();
            }
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
	}

}

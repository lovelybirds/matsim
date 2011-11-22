/* *********************************************************************** *
 * project: org.matsim.*
 * WithinDayQSim.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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

package org.matsim.withinday.mobsim;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.ptproject.qsim.QSim;
import org.matsim.ptproject.qsim.agents.AgentFactory;
import org.matsim.ptproject.qsim.agents.ExperimentalBasicWithindayAgentFactory;
import org.matsim.ptproject.qsim.qnetsimengine.DefaultQSimEngineFactory;
import org.matsim.ptproject.qsim.qnetsimengine.QNetsimEngineFactory;

/*
 * This extended QSim contains some methods that
 * are needed for the WithinDay Replanning Modules.
 * 
 * Some other methods are used for the Knowledge Modules. They
 * should be separated somewhen but at the moment this seems
 * to be difficult so they remain here for now...
 */
public class WithinDayQSim extends QSim {
	
	public WithinDayQSim(final Scenario scenario, final EventsManager events) {
		this(scenario, events, new DefaultQSimEngineFactory());
	}
	
	public WithinDayQSim(final Scenario scenario, final EventsManager events, QNetsimEngineFactory factory) {
		super(scenario, events, factory);
		
		// use ExperimentalBasicWithindayAgentFactory that creates ExperimentalBasicWithindayAgents who can reset their chachedNextLink
		ExperimentalBasicWithindayAgentFactory agentFactory = new ExperimentalBasicWithindayAgentFactory(this);
		super.setAgentFactory(agentFactory);
	}
	
	@Override
	public void setAgentFactory(AgentFactory factory) {
		if (factory instanceof ExperimentalBasicWithindayAgentFactory) {
			super.setAgentFactory(factory);
		}
		else throw new RuntimeException("Please use a WithinDayAgentFactory!");
	}

}

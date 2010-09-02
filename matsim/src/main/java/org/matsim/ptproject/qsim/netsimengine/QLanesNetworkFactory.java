/* *********************************************************************** *
 * project: org.matsim.*
 * QLanesNetworkFactory
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
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

package org.matsim.ptproject.qsim.netsimengine;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.lanes.LaneDefinitions;
import org.matsim.ptproject.qsim.interfaces.QNetworkFactory;


public class QLanesNetworkFactory implements QNetworkFactory<QNode, QLinkInternalI> {

	private QNetworkFactory<QNode, QLinkInternalI> delegate;
	private LaneDefinitions laneDefinitions;

	public QLanesNetworkFactory(QNetworkFactory<QNode, QLinkInternalI> delegate, LaneDefinitions laneDefintions){
		this.delegate = delegate;
		this.laneDefinitions = laneDefintions;
	}

	@Override
	public QLinkInternalI createQueueLink(Link link, QSimEngine engine,
			QNode queueNode) {
		QLinkInternalI ql = null;
		if (this.laneDefinitions.getLanesToLinkAssignments().containsKey(link.getId())){
			ql = new QLinkLanesImpl(link, engine, queueNode, this.laneDefinitions.getLanesToLinkAssignments().get(link.getId()).getLanes());
		}
		else {
			ql = this.delegate.createQueueLink(link, engine, queueNode);
		}
		return ql;
	}

	@Override
	public QNode createQueueNode(Node node, QSimEngine simEngine) {
		return this.delegate.createQueueNode(node, simEngine);
	}

}

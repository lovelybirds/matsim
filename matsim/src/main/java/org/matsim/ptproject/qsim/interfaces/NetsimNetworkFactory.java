/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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

package org.matsim.ptproject.qsim.interfaces;

import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.api.internal.MatsimFactory;
import org.matsim.ptproject.qsim.qnetsimengine.QNetsimEngine;


/**
 * @author dgrether
 */
public interface NetsimNetworkFactory<QN extends NetsimNode, QL extends NetsimLink> extends MatsimFactory {

	public QN createNetsimNode(Node node, QNetsimEngine simEngine);

	public QL createNetsimLink(Link link, QNetsimEngine simEngine, QN queueNode);
	
	public NetsimNetwork createNetsimNetwork( Netsim mobsim ) ;

}

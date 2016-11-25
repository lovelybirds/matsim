package org.matsim.contrib.accessibility;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.accessibility.utils.AggregationObject;
import org.matsim.contrib.accessibility.utils.Distances;
import org.matsim.contrib.accessibility.utils.NetworkUtil;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.facilities.ActivityFacility;
import org.matsim.utils.leastcostpathtree.LeastCostPathTree;

/**
 * @author thibautd
 */
 class ConstantSpeedAccessibilityExpContributionCalculator implements AccessibilityContributionCalculator {
	private static final Logger log = Logger.getLogger( ConstantSpeedAccessibilityExpContributionCalculator.class );

	// estimates travel time by a constant speed along network,
	// considering all links (including highways, which seems to be realistic in south africa,
	// but less elsewhere)
	private final LeastCostPathTree lcptTravelDistance = new LeastCostPathTree( new FreeSpeedTravelTime(), new LinkLengthTravelDisutility());

	private final double betaWalkTT;
	private final double betaWalkTD;
	private final double walkSpeedMeterPerHour;

	private double logitScaleParameter;
	private double betaTT;	// in MATSim this is [utils/h]: cnScoringGroup.getTravelingBike_utils_hr() - cnScoringGroup.getPerforming_utils_hr()
	private double betaTD;	// in MATSim this is 0 !!! since getMonetaryDistanceCostRateBike doesn't exist:

	private double constant;

	private double speedMeterPerHour = -1;

	private Node fromNode = null;

	private final Network network;

	public ConstantSpeedAccessibilityExpContributionCalculator( final String mode, Config config, Network network) {

		this.network = network;
		final PlanCalcScoreConfigGroup planCalcScoreConfigGroup = config.planCalcScore() ;

		if ( planCalcScoreConfigGroup.getOrCreateModeParams( mode ).getMonetaryDistanceRate() != 0. ) {
			log.error( "monetary distance cost rate for "+mode+" different from zero but not used in accessibility computations");
		}

		logitScaleParameter = planCalcScoreConfigGroup.getBrainExpBeta() ;
		final Double speedMetersPerSec = config.plansCalcRoute().getTeleportedModeSpeeds().get( mode );
		if ( speedMetersPerSec==null ) {
			log.error( "mode=" + mode );
		}
		speedMeterPerHour = speedMetersPerSec * 3600.;

		final PlanCalcScoreConfigGroup.ModeParams modeParams = planCalcScoreConfigGroup.getOrCreateModeParams( mode );
		betaTT = modeParams.getMarginalUtilityOfTraveling() - planCalcScoreConfigGroup.getPerforming_utils_hr();
		betaTD = modeParams.getMarginalUtilityOfDistance();

		betaWalkTT		= planCalcScoreConfigGroup.getModes().get(TransportMode.walk).getMarginalUtilityOfTraveling() - planCalcScoreConfigGroup.getPerforming_utils_hr();
		betaWalkTD		= planCalcScoreConfigGroup.getModes().get(TransportMode.walk).getMarginalUtilityOfDistance();

		constant = modeParams.getConstant();

		this.walkSpeedMeterPerHour = config.plansCalcRoute().getTeleportedModeSpeeds().get( TransportMode.walk ) * 3600;
	}

	@Override
	public void notifyNewOriginNode(Node fromNode, Double departureTime) {
		this.fromNode = fromNode;
		this.lcptTravelDistance.calculate(network, fromNode, departureTime);
	}

	@Override
	public double computeContributionOfOpportunity(ActivityFacility origin, AggregationObject destination, Double departureTime) {
		// get the nearest link:
		Link nearestLink = NetworkUtils.getNearestLinkExactly(network,origin.getCoord());

		// captures the distance (as walk time) between the origin via the link to the node:
		Distances distances = NetworkUtil.getDistances2NodeViaGivenLink(origin.getCoord(), nearestLink, fromNode);

		// get stored network node (this is the nearest node next to an aggregated work place)
		Node destinationNode = destination.getNearestNode();

		// TODO: extract this walk part?
		// In the state found before modularization (june 15), this was anyway not consistent accross modes
		// (different for PtMatrix), pointing to the fact that making this mode-specific might make sense.
		// distance to road, and then to node:
		double walkTravelTimeMeasuringPoint2Road_h 	= distances.getDistancePoint2Intersection() / this.walkSpeedMeterPerHour;

		// disutilities to get on or off the network
		double walkDisutilityMeasuringPoint2Road = (walkTravelTimeMeasuringPoint2Road_h * betaWalkTT) + (distances.getDistancePoint2Intersection() * betaWalkTD);
		double expVhiWalk = Math.exp(this.logitScaleParameter * walkDisutilityMeasuringPoint2Road);
		double sumExpVjkWalk = destination.getSum();


		double road2NodeBikeTime_h					= distances.getDistanceIntersection2Node() / speedMeterPerHour;
		double travelDistance_meter = lcptTravelDistance.getTree().get(destinationNode.getId()).getCost(); 				// travel link distances on road network for bicycle and walk
		double bikeDisutilityRoad2Node = (road2NodeBikeTime_h * betaTT) + (distances.getDistanceIntersection2Node() * betaTD); // toll or money ???
		double bikeDisutility = ((travelDistance_meter/ speedMeterPerHour) * betaTT) + (travelDistance_meter * betaTD);// toll or money ???
		// This is equivalent to the sum of the exponential of the utilities for all destinations (I had to write it on
		// paper to check it is correct...)
		return expVhiWalk * Math.exp(logitScaleParameter * (constant + bikeDisutility + bikeDisutilityRoad2Node)) * sumExpVjkWalk;
	}
}

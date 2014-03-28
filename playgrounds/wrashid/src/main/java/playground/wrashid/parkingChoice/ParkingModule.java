package playground.wrashid.parkingChoice;

import java.util.LinkedList;

import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.parking.lib.DebugLib;
import org.matsim.contrib.parking.lib.EventHandlerAtStartupAdder;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.utils.geometry.CoordImpl;

import playground.wrashid.PSF.energy.AfterSimulationListener;
import playground.wrashid.parkingChoice.api.ParkingSelectionManager;
import playground.wrashid.parkingChoice.api.PreferredParkingManager;
import playground.wrashid.parkingChoice.api.ReservedParkingManager;
import playground.wrashid.parkingChoice.infrastructure.ParkingImpl;
import playground.wrashid.parkingChoice.infrastructure.api.Parking;
import playground.wrashid.parkingChoice.scoring.ParkingScoreAccumulator;
import playground.wrashid.parkingChoice.scoring.ParkingScoreCollector;
import playground.wrashid.parkingSearch.planLevel.init.ParkingRoot;

public class ParkingModule {

	
	private final Controler controler;
	private ParkingScoreAccumulator parkingScoreAccumulator;
	private ParkingManager parkingManager;

	public ParkingManager getParkingManager(){
		return parkingManager;
	}
	
	public ParkingModule(Controler controler, LinkedList<Parking> parkingCollection){
		this.controler = controler;
		
		EventHandlerAtStartupAdder eventHandlerAtStartupAdder = new EventHandlerAtStartupAdder();
		controler.addControlerListener(eventHandlerAtStartupAdder);
		
		// TODO: remove this in refactoring, just here due to the output graph
		// class: playground.wrashid.parkingSearch.planLevel.analysis.ParkingWalkingDistanceMeanAndStandardDeviationGraph
		ParkingRoot.setParkingWalkingDistanceScalingFactorForOutput(1.0);
		
		parkingManager = new ParkingManager(controler, parkingCollection);
		ParkingSimulation parkingSimulation=new ParkingSimulation(parkingManager, controler);
		ParkingScoreCollector parkingScoreCollector=new ParkingScoreCollector(controler);
		parkingSimulation.addParkingArrivalEventHandler(parkingScoreCollector);
		parkingSimulation.addParkingDepartureEventHandler(parkingScoreCollector);
		controler.addControlerListener(parkingManager);
		parkingScoreAccumulator = new ParkingScoreAccumulator(parkingScoreCollector, parkingManager);
		controler.addControlerListener(parkingScoreAccumulator);
		PlanUpdater planUpdater=new PlanUpdater(parkingManager);
		controler.addControlerListener(planUpdater);
		
		eventHandlerAtStartupAdder.addEventHandler(parkingSimulation);
	}
	
	public Double getAverageWalkingDistance(){
		return parkingScoreAccumulator.getAverageWalkingDistance();
	}
	
	/**
	 * If you want to use reserved Parkings in the simulation, you must set the ReservedParkingManager
	 * @param reservedParkingManager
	 */
	public void setReservedParkingManager(ReservedParkingManager reservedParkingManager){
		parkingManager.setReservedParkingManager(reservedParkingManager);
	}
	

	public void setParkingSelectionManager(ParkingSelectionManager parkingSelectionManager){
		parkingManager.setParkingSelectionManager(parkingSelectionManager);
	}
	
	
	/**
	 * If you want to use Preferred Parkings, set this first
	 * @param reservedParkingManager
	 */
	public void setPreferredParkingManager(PreferredParkingManager preferredParkingManager){
		parkingManager.setPreferredParkingManager(preferredParkingManager);
	}
	
	class PlanUpdater implements AfterMobsimListener{

		private final ParkingManager parkingManager;

		public PlanUpdater(ParkingManager parkingManager){
			this.parkingManager = parkingManager;
			
		}
		
		@Override
		public void notifyAfterMobsim(AfterMobsimEvent event) {
			for (Person person : event.getControler().getPopulation().getPersons().values()) {
				
				
				parkingManager.getPlanUsedInPreviousIteration().put(person.getId(), person.getSelectedPlan());
			}
		}
		
		
	}
	
}

package set10111.cw;

import jade.core.*;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public class Main {
	public static void main(String[] args) {
		Profile myProfile = new ProfileImpl();
		Runtime myRuntime = Runtime.instance();
		try{
			ContainerController myContainer = myRuntime.createMainContainer(myProfile);	
			AgentController rma = myContainer.createNewAgent("rma", "jade.tools.rma.rma", null);
			rma.start();

			AgentController Manufacturer = myContainer.createNewAgent("Manufacturer", Manufacturer.class.getCanonicalName(), null);
			Manufacturer.start();

			// 2 supplier agents.
			AgentController Supplier1 = myContainer.createNewAgent("Supplier1", Supplier1.class.getCanonicalName(), null);
			Supplier1.start();
			
			AgentController Supplier2 = myContainer.createNewAgent("Supplier2", Supplier2.class.getCanonicalName(), null);
			Supplier2.start();

			// Number of customers.
			int numCusts = 3;
			AgentController customer;
			for(int i=0; i<numCusts; i++) {
				customer = myContainer.createNewAgent("Customer" + i, Customers.class.getCanonicalName(), null);
				customer.start();
			}

			AgentController tickerAgent = myContainer.createNewAgent("ticker", BuyerSellerTicker.class.getCanonicalName(),
					null);
			tickerAgent.start();

		}
		catch(Exception e){
			System.out.println("Exception starting agent: " + e.toString());
		}


	}
}
package set10111.cw;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jade.content.Concept;
import jade.content.ContentElement;
import jade.content.lang.Codec;
import jade.content.lang.Codec.CodecException;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.onto.basic.Action;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import ontology.ECommerceOntology;
import ontology.elements.Battery;
import ontology.elements.Deliver;
import ontology.elements.OrderInst;
import ontology.elements.Owns;
import ontology.elements.Phone;
import ontology.elements.RAM;
import ontology.elements.Screen;
import ontology.elements.Storage;

public class Customers extends Agent {

	private int day;

	private AID tickerAgent;
	private AID manufacturer;

	private ArrayList<OrderInst> Orders = new ArrayList<OrderInst>();

	private Codec codec = new SLCodec();
	private Ontology ontology = ECommerceOntology.getInstance();


	protected void setup() {
		//add this agent to the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("customer");
		sd.setName(getLocalName() + "-customer-agent");
		dfd.addServices(sd);
		try{
			DFService.register(this, dfd);
		}
		catch(FIPAException e){
			e.printStackTrace();
		}

		// Add manufacturer agent
		manufacturer = new AID("Manufacturer", AID.ISLOCALNAME);


		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);


		addBehaviour(new TickerWaiter(this));
	}


	public class TickerWaiter extends CyclicBehaviour {

		//behaviour to wait for a new day
		public TickerWaiter(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchContent("new day"),
					MessageTemplate.MatchContent("terminate"));
			ACLMessage msg = myAgent.receive(mt); 
			if(msg != null) {
				if(tickerAgent == null) {
					tickerAgent = msg.getSender();
				}
				if(msg.getContent().equals("new day")) {

					//spawn new sequential behaviour for day's activities
					SequentialBehaviour dailyActivity = new SequentialBehaviour();
					//sub-behaviours will execute in the order they are added
					dailyActivity.addSubBehaviour(new QueryBuyerBehaviour(myAgent));
					//dailyActivity.addSubBehaviour(new ReceiveDeliveryListener(myAgent));
					ArrayList<Behaviour> cyclicBehaviours = new ArrayList<>();
					CyclicBehaviour rDL = new ReceiveDeliveryListener(myAgent);
					myAgent.addBehaviour(rDL);
					cyclicBehaviours.add(rDL);
					dailyActivity.addSubBehaviour(new EndDay(myAgent));
					myAgent.addBehaviour(dailyActivity);
					day++;
				}
				else {
					//termination message to end simulation
					myAgent.doDelete();
				}
			}
			else{
				block();
			}
		}

	}


	private class QueryBuyerBehaviour extends OneShotBehaviour {
		public QueryBuyerBehaviour(Agent a) {
			super(a);
		}

		boolean messageReceived = false;

		public void action() {
			int step = 0;
			OrderInst phoneOrder = new OrderInst();	

			switch(step){
			case 0:

				String type;
				int screen;
				int storage;
				int ram;
				int battery;
				int quantity = (int) Math.round(Math.floor(1 + 50 * Math.random()));
				int unitPrice = (int) Math.round(Math.floor(100 + 500 * Math.random()));
				int dueDate = (int) Math.round(Math.floor(1 + 10 * Math.random()));
				int penalty = (int) Math.round(Math.floor((quantity) * Math.floor(1 + 50 * Math.random())));


				if(Math.random() < 0.5) {
					type = "small";
					screen = 5;
					battery = 2000;
				}
				else {
					type = "phablet";
					screen = 7;
					battery = 3000;
				}

				if(Math.random() < 0.5) {
					ram = 4;
				}
				else {
					ram = 8;
				}

				if(Math.random() < 0.5) {
					storage = 64;
				}
				else {
					storage = 256;
				}

				// Prepare the Query-IF message
				ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
				msg.addReceiver(manufacturer);
				msg.setLanguage(codec.getName());
				msg.setOntology(ontology.getName());

				// Prepare the content

				phoneOrder.setQuantity(quantity);
				phoneOrder.setUnitPrice(unitPrice);
				phoneOrder.setDueDate(dueDate);
				phoneOrder.setPenalty(penalty);
				phoneOrder.setCustomer(myAgent.getAID());

				Phone phone = new Phone();

				phone.setType(type);

				Screen SCRN = new Screen();
				SCRN.setComponentName("Screen");
				SCRN.setScreenSize(screen);
				phone.setScreen(SCRN);

				Storage STRG = new Storage();
				STRG.setComponentName("Storage");
				STRG.setStorageSize(storage);
				phone.setStorage(STRG);

				RAM RM = new RAM();
				RM.setComponentName("RAM");
				RM.setRamSize(ram);
				phone.setRAM(RM);

				Battery BTY = new Battery();
				BTY.setComponentName("Battery");
				BTY.setBatterySize(battery);
				phone.setBattery(BTY);

				phoneOrder.setPhone(phone);

				Action myOrder = new Action();
				myOrder.setAction(phoneOrder);
				myOrder.setActor(manufacturer);
				try {
					// Let JADE convert from Java objects to string
					getContentManager().fillContent(msg, myOrder);
					send(msg);
					//myAgent.addBehaviour(new CollectOffers(myAgent));


				}
				catch (CodecException ce) {
					ce.printStackTrace();
				}
				catch (OntologyException oe) {
					oe.printStackTrace();
				}
				step++;

			case 2:

				while(messageReceived == false){
					MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL), MessageTemplate.MatchPerformative(ACLMessage.REFUSE)); 
					ACLMessage msg2 = receive(mt);
					if(msg2 != null){

						if(msg2.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
							Orders.add(phoneOrder);
							messageReceived = true;
						}
						else if (msg2.getPerformative() == ACLMessage.REFUSE){
							messageReceived = true;
						}	
					}
				}
			}
		}

//		@Override
//		public boolean done() {
//			// TODO Auto-generated method stub
//			return messageReceived == true;
//		}
	}


	public class ReceiveDeliveryListener extends CyclicBehaviour
	{
		public ReceiveDeliveryListener(Agent a) {
			super(a);
		}

		@Override
		public void action() {

			MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchConversationId("delivery")); 
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null)
			{
				try
				{
					ContentElement ce = null;

					ce = getContentManager().extractContent(msg);
					if(ce instanceof Action)
					{
						Concept action = ((Action)ce).getAction();
						if(action instanceof Deliver)
						{
							Deliver delivery = (Deliver)action;

							int i = 0;

							while(i < Orders.size()) {
								if(Orders.get(i).getQuantity() == delivery.getQuantity() && (Orders.get(i).getQuantity() * Orders.get(i).getUnitPrice()) == delivery.getOrderCost()) {
									Orders.remove(i);
									i = Orders.size();
								}
								else {
									i++;
								}
							}
						}
					}
				} catch (CodecException ce) {
					ce.printStackTrace();
				}
				catch (OntologyException oe) {
					oe.printStackTrace();
				} 
			}
		}
	}


	public class EndDay extends OneShotBehaviour {

		public EndDay(Agent a) {
			super(a);
		}

		@Override
		public void action() {

			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			msg.addReceiver(tickerAgent);
			msg.setContent("done");
			myAgent.send(msg);

		}

	}
}


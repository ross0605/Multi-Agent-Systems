package set10111.cw;

import java.util.HashMap;

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
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import ontology.ECommerceOntology;
import ontology.elements.Battery;
import ontology.elements.Component;
import ontology.elements.OrderInst;
import ontology.elements.Owns;
import ontology.elements.Phone;
import ontology.elements.RAM;
import ontology.elements.Screen;
import ontology.elements.Sell;
import ontology.elements.Storage;
import set10111.cw.Supplier1.EndDay;
import set10111.cw.Supplier1.TickerWaiter;

public class Supplier2 extends Agent {
	private AID tickerAgent;
	public int day;

	HashMap<Integer, Integer> stockList = new HashMap<Integer, Integer>();

	private Codec codec = new SLCodec();
	private Ontology ontology = ECommerceOntology.getInstance();

	boolean orderingFinished = false;


	protected void setup() {

		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);

		// ADD COMPONENTS FOR SALE

		Storage storage64 = new Storage();
		storage64.setStorageSize(64);
		stockList.put(64, 15);

		Storage storage256 = new Storage();
		storage256.setStorageSize(256);
		stockList.put(256, 40);

		RAM RAM4 = new RAM();
		RAM4.setRamSize(8);
		stockList.put(4, 20);

		RAM RAM8 = new RAM();
		RAM8.setRamSize(7);
		stockList.put(8, 35);


		//add this agent to the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("supplier2");
		sd.setName(getLocalName() + "-supplier-agent");
		dfd.addServices(sd);
		try{
			DFService.register(this, dfd);
		}
		catch(FIPAException e){
			e.printStackTrace();
		}

		addBehaviour(new TickerWaiter(this));
		addBehaviour(new orderEnd(this));

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
					day++;
					orderingFinished = false;
					//spawn new sequential behaviour for day's activities
					SequentialBehaviour dailyActivity = new SequentialBehaviour();
					//sub-behaviours will execute in the order they are added
					dailyActivity.addSubBehaviour(new QueryBehaviour(myAgent));
					dailyActivity.addSubBehaviour(new EndDay(myAgent));
					myAgent.addBehaviour(dailyActivity);

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


	public class orderEnd extends CyclicBehaviour {
		//behaviour to wait for order to end
		public orderEnd(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchContent("end of order");
			ACLMessage msg = myAgent.receive(mt); 
			if(msg != null) {
				orderingFinished = true;
			}
			else{
				block();
			}
		}

	}


	private class QueryBehaviour extends Behaviour{

		public QueryBehaviour(Agent a) {
			super(a);
		}

		int step = 1;

		int messagesReceived = 0;

		@Override
		public void action() {
			while(messagesReceived != 3) {
				step = 1;
				switch(step) {
				case 1:
					//This behaviour should only respond to QUERY_IF messages
					MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchPerformative(ACLMessage.QUERY_IF), MessageTemplate.MatchPerformative(ACLMessage.INFORM)); 
					ACLMessage msg = receive(mt);
					if(msg != null){
						if(msg.getContent().contains("end of order")) {
							messagesReceived = 3;
						}
						else {
							try {
								ContentElement ce = null;
								// Let JADE convert from String to Java objects
								// Output will be a ContentElement
								ce = getContentManager().extractContent(msg);
								if (ce instanceof Owns) {
									Owns owns = (Owns) ce;
									Component it = owns.getComponent();
									Component compOrder = (Component)it;

									//check if seller has it in stock
									if(stockList.containsKey(compOrder.getComponentSize())) {

										ACLMessage response = new ACLMessage(ACLMessage.CFP);
										response.addReceiver(msg.getSender());
										response.setLanguage(codec.getName());
										response.setOntology(ontology.getName());
										response.setContent(msg.getContent());
										myAgent.send(response);
										step++;

									}


								}
								else {
									System.out.println("out of stock");

									ACLMessage response = new ACLMessage(ACLMessage.REFUSE);
									response.setContent(msg.getOntology());
									response.addReceiver(msg.getSender() );
									myAgent.send(response);

								}			
							} catch (Exception e) {}
						}
					}

				case 2:

					MessageTemplate sr = MessageTemplate.MatchPerformative(ACLMessage.PROPOSE); 
					ACLMessage msgS = receive(sr);
					if(msgS != null){
						try {
							ContentElement ceS = null;

							// Let JADE convert from String to Java objects
							// Output will be a ContentElement
							ceS = getContentManager().extractContent(msgS);
							if(ceS instanceof Action) {
								Concept action = ((Action)ceS).getAction();
								if (action instanceof Sell) {
									Sell order = (Sell)action;
									Component itS = order.getComponent();
									Component compOrderS = (Component) itS;

									ACLMessage reply = new ACLMessage(ACLMessage.CONFIRM);
									reply.addReceiver(msgS.getSender());
									reply.setLanguage(codec.getName());
									reply.setOntology(ontology.getName());

									Component replyComponent = new Component();

									replyComponent.setComponentName(compOrderS.getComponentName());
									replyComponent.setComponentSize(compOrderS.getComponentSize());
									Sell sellCompS = new Sell();
									sellCompS.setBuyer(msgS.getSender());
									sellCompS.setComponent(replyComponent);
									sellCompS.setDeliveryDate(4);
									sellCompS.setComponentPrice(stockList.get(compOrderS.getComponentSize()));
									sellCompS.setQuantity(order.getQuantity());

									Action request = new Action();
									request.setAction(sellCompS);
									request.setActor(msgS.getSender());

									try {
										getContentManager().fillContent(reply, request);
										send(reply);
										messagesReceived++;

									}
									catch (CodecException ce2) {
										ce2.printStackTrace();
									}
									catch (OntologyException oe) {
										oe.printStackTrace();
									} 
								}

							}
						} catch (Exception e) {}

					}

					if (messagesReceived == 2) {
						orderingFinished = true;
					}
				}
			}
		}

		@Override
		public boolean done() {
			// TODO Auto-generated method stub
			return orderingFinished = true;
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

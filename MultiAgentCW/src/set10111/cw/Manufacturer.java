package set10111.cw;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jade.content.onto.basic.Action;

import jade.content.Concept;
import jade.content.ContentElement;
import java.util.Collections;
import java.util.Comparator;

import jade.content.lang.Codec;
import jade.content.lang.Codec.CodecException;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
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
import ontology.elements.Deliver;
import ontology.elements.OrderInst;
import ontology.elements.Owns;
import ontology.elements.Phone;
import ontology.elements.RAM;
import ontology.elements.Screen;
import ontology.elements.Sell;
import ontology.elements.Storage;

public class Manufacturer extends Agent {


	private int day;
	private int customerCount = 0;
	private int bank = 0;
	private int todaysCost = 0;
	private int ordersBuiltToday = 0;
	int lateFees = 0;

	// Income from order 1 and order 2
	private int income = 0;
	private int incomeO2 = 0;

	// Boolean representing whether we will accept an order today or not.
	private boolean noOrder = false;

	// Points to the end of the orders list
	private int oS = 0;

	// Used for holding the statistics.
	private int totalComponentCost = 0;
	private int totalComponentsOrdered = 0;
	private int totalLateFees = 0;
	private int totalWarehouseCost = 0;
	private int totalOrdersTaken = 0;
	private int totalOrdersDelivered = 0;
	private int totalOrdersMoney = 0;

	// Agent names.
	private AID tickerAgent;
	private ArrayList<AID> customers = new ArrayList<>();
	private AID supplier1;
	private AID supplier2;

	// Lists of open orders, deliveries waiting and the current stock levels.
	private ArrayList<OrderInst> openOrders = new ArrayList<OrderInst>();
	private ArrayList<Sell> openDeliveries = new ArrayList<Sell>();
	private HashMap<Integer, Integer> stock = new HashMap<Integer, Integer>();

	private Codec codec = new SLCodec();
	private Ontology ontology = ECommerceOntology.getInstance();

	// SETUP for agents and agent-oriented behaviours 
	protected void setup() {
		// add this agent to the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("manufacturer");
		sd.setName(getLocalName() + "-manufacturer-agent");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (FIPAException e) {
			e.printStackTrace();
		}

		supplier1 = new AID("Supplier1", AID.ISLOCALNAME);
		supplier2 = new AID("Supplier2", AID.ISLOCALNAME);

		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);

		addBehaviour(new TickerWaiter(this));
		addBehaviour(new FindCustomers(this));

	}

	public class TickerWaiter extends CyclicBehaviour {
		// behaviour to wait for a new day
		public TickerWaiter(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.or(MessageTemplate.MatchContent("new day"),
					MessageTemplate.MatchContent("terminate"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				if (tickerAgent == null) {
					tickerAgent = msg.getSender();
				}
				if (msg.getContent().equals("new day")) {

					// Reset these values on each new day.
					noOrder = false;
					todaysCost = 0;
					income = 0;
					incomeO2 = 0;
					lateFees = 0;

					// spawn new sequential behaviour for day's activities
					SequentialBehaviour dailyActivity = new SequentialBehaviour();
					// sub-behaviours will execute in the order they are added
					dailyActivity.addSubBehaviour(new OrderListener(myAgent));
					dailyActivity.addSubBehaviour(new orderParts(myAgent));
					dailyActivity.addSubBehaviour(new checkDeliveries(myAgent));
					dailyActivity.addSubBehaviour(new buildOrder1(myAgent));
					dailyActivity.addSubBehaviour(new buildOrder2(myAgent));
					dailyActivity.addSubBehaviour(new Expenditure(myAgent));
					dailyActivity.addSubBehaviour(new EndDay(myAgent));
					myAgent.addBehaviour(dailyActivity);
					day++;
				} else {
					// termination message to end simulation
					myAgent.doDelete();
				}
			} else {
				block();
			}
		}

	}


	// Find the customer agents.
	public class FindCustomers extends OneShotBehaviour {

		public FindCustomers(Agent a) {
			super(a);
		}

		@Override
		public void action() {
			DFAgentDescription sellerTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("customer");
			sellerTemplate.addServices(sd);
			try{
				customers.clear();
				DFAgentDescription[] agentsType1  = DFService.search(myAgent,sellerTemplate); 
				for(int i=0; i<agentsType1.length; i++){
					customers.add(agentsType1[i].getName()); // this is the AID
					customerCount++;
				}
			}
			catch(FIPAException e) {
				e.printStackTrace();
			}

		}

	}


	// Listen for all orders being placed.
	public class OrderListener extends Behaviour {

		public OrderListener(Agent a) {
			super(a);
		}

		boolean finished = false;

		// Orders received from agents
		int messageReceived = 0;
		// The AID of the accepted order.
		AID accepted;
		// Step for switch case.
		private int step = 0;
		// Keeps track of the index of the received orders list.
		private int index = 0;
		// List of all received orders.
		ArrayList<OrderInst> orders = new ArrayList<OrderInst>();
		
		@Override
		public void action() {
			switch(step) {
			case 0:
				while(messageReceived != customerCount) {

					MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchLanguage(codec.getName()),
							MessageTemplate.MatchOntology(ontology.getName()));
					ACLMessage msg = blockingReceive(mt);

					if (msg != null) {
						try {
							ContentElement ce = null;
							if (msg.getPerformative() == ACLMessage.REQUEST) {
								ce = getContentManager().extractContent(msg);
								if (ce instanceof Action) {
									Concept action = ((Action)ce).getAction();
									if(action instanceof OrderInst)
									{

										// Extract and print order information
										OrderInst phoneOrder = (OrderInst)action;
										System.out.println("ORDER NO. " + (messageReceived + 1));
										System.out.println("Screen: "
												+ phoneOrder.getPhone().getScreen().getScreenSize() + "\""
												+ "    Storage: "
												+ phoneOrder.getPhone().getStorage().getStorageSize() + "Gb" 
												+ "    RAM: "
												+ phoneOrder.getPhone().getRAM().getRamSize() + "Gb"
												+ "    Battery: "
												+ phoneOrder.getPhone().getBattery().getBatterySize() + "mAh"
												+ "    Quantity: " + phoneOrder.getQuantity() + " units"
												+ "    Unit Price: £" + phoneOrder.getUnitPrice() + " per unit"
												+ "    Due Date: " + phoneOrder.getDueDate() + " days"
												+ "    Penalty per day: £" + phoneOrder.getPenalty()
												+ "    AID: " + phoneOrder.getCustomer());
										System.out.println("");

										orders.add(phoneOrder);

										messageReceived++;
									}
									else {
										return;
									}
								}
							}
						}
						catch (CodecException ce) {
							ce.printStackTrace();
						} catch (OntologyException oe) {
							oe.printStackTrace();
						}
					}
				} step++;

			case 1:

				// Accept order based on highest potential profit.
				// If the day is past 90 days, will only accept offers that have a due date of less than or equal to day 100.
				// In order to give the manufacturer time to build any remaining orders left, past day 95, the manufacturer will only 
				// accept orders if there are less than 2 orders left to build. These orders must also have a due date of less
				// than or equal to 100 days.

				ArrayList<Integer> maxIncome = new ArrayList<Integer>(); 
				int m = 0;

				while(m != customerCount) {
					maxIncome.add((orders.get(m).getQuantity() * orders.get(m).getUnitPrice()));
					m++;
				}

				int maxNo = Collections.max(maxIncome);

				index = maxIncome.indexOf(maxNo);

				if(day > 90) {

					int i = 0;
					int j = 0;
					int k = 0;

					if(day > 97 && openOrders.size() > 2) {

						oS--;

						while(j < customerCount) {
							ACLMessage reply1 = new ACLMessage(ACLMessage.REFUSE);
							reply1.addReceiver(customers.get(j));
							myAgent.send(reply1);
							j++;

						}

						noOrder = true;
						System.out.println("Refusing all orders to catch up on order backlog.");
						step++;
						finished = true;
						break;
					}


					while(k < customerCount) {
						if((day + orders.get(i).getDueDate()) <= 100) {
							openOrders.add(orders.get(i));
							noOrder = false;

							ACLMessage reply = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
							reply.addReceiver(customers.get(i));
							myAgent.send(reply);
							totalOrdersTaken = totalOrdersTaken+1;
							System.out.println("Manufacturer accepting order " + (i + 1) + ".");

							k = customerCount;
						}
						else {
							noOrder = true;
							i++;
							k++;
						}
					}

					if(noOrder == false) {
						while(j < customerCount) {
							if(j != i) {
								ACLMessage reply1 = new ACLMessage(ACLMessage.REFUSE);
								reply1.addReceiver(customers.get(j));
								myAgent.send(reply1);
								j++;
							}
							else {
								j++;
							}
						}
					}


					if(noOrder == true) {

						oS--;

						while(j < customerCount) {
							ACLMessage reply1 = new ACLMessage(ACLMessage.REFUSE);
							reply1.addReceiver(customers.get(j));
							myAgent.send(reply1);
							j++;
						}
					}
					step++;
				} 
				else {

					System.out.println("Manufacturer accepting order " + (index+1) + ".");
					openOrders.add(orders.get(index));
					accepted = orders.get(index).getCustomer();
					System.out.println("Accepting order from " + accepted);

					// ACCEPT ORDER -------------------------------------------------------------------------------------------------------------------------------
					if(noOrder == false) {
						ACLMessage reply = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
						reply.addReceiver(accepted);
						myAgent.send(reply);
						totalOrdersTaken = totalOrdersTaken+1;
					}

					step++;

					// DECLINE ORDERS ------------------------------------------------------------------------------------------------------------------------------

					if(noOrder == false) {
						int z = 0;

						while(z != customerCount) {
							if (z != index) {
								ACLMessage reply1 = new ACLMessage(ACLMessage.REFUSE);
								reply1.addReceiver(orders.get(z).getCustomer());
								myAgent.send(reply1);
								z++;
							}
							else {
								z++;
							}
						}
					}
					else if (noOrder == true) {

						int k = 0;

						while(k != customerCount) {
							ACLMessage reply1 = new ACLMessage(ACLMessage.REFUSE);
							reply1.addReceiver(orders.get(k).getCustomer());
							myAgent.send(reply1);
							k++;
							System.out.println("Sending refuse to customer " + k);
						}
					}

					step++;

				}

			case 3:
				finished = true;
			}
		}

		@Override
		public boolean done() {
			return finished == true;
		}
	}


	public class orderParts extends OneShotBehaviour {

		public orderParts(Agent a) {
			super(a);
		}

		boolean finished = false;

		private int step = 1;

		boolean messageReceived = false;

		AID supplierName = null;

		public void action() {

			if(noOrder == false) {

				// Always order some parts from supplier 1. If the due date is 5+ days, set the supplierName variable
				// to supplier 2. This means we will order the available parts from supplier 2 at a lower price.
				// For each part, order, receive confirmation of the parts stock and send a sell request, 
				// and then receive confirmation of purchase. Move on to the next step.

				if(openOrders.get(oS).getDueDate() < 4) {
					supplierName = new AID("Supplier1", AID.ISLOCALNAME);
				}

				else if(openOrders.get(oS).getDueDate() >= 4) {
					supplierName = new AID("Supplier2", AID.ISLOCALNAME);
				}

				switch(step) {
				case 1:

					ACLMessage msg = new ACLMessage(ACLMessage.QUERY_IF);
					msg.addReceiver(supplier1);
					msg.setLanguage(codec.getName());
					msg.setOntology(ontology.getName());

					Component screenOrder = new Component();

					screenOrder.setComponentName("Screen");
					screenOrder.setComponentSize(openOrders.get(oS).getPhone().getScreen().getScreenSize());
					Owns screenRequest = new Owns();
					screenRequest.setOwner(supplier1);
					screenRequest.setComponent(screenOrder);
					screenRequest.setQuantity(openOrders.get(oS).getQuantity());

					try {
						getContentManager().fillContent(msg, screenRequest);
						send(msg);
						step++;
					}
					catch (CodecException ce) {
						ce.printStackTrace();
					}
					catch (OntologyException oe) {
						oe.printStackTrace();
					}

				case 2:

					while(messageReceived != true) {

						MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP); 
						ACLMessage msgR1 = receive(mt);
						if(msgR1 != null){
							try {
								ACLMessage response = new ACLMessage(ACLMessage.PROPOSE);
								response.addReceiver(msgR1.getSender());
								response.setLanguage(codec.getName());
								response.setOntology(ontology.getName());

								Component componentSale = new Component();

								componentSale.setComponentName("Screen");
								componentSale.setComponentSize(openOrders.get(oS).getPhone().getScreen().getScreenSize());
								Sell sellComp = new Sell();
								sellComp.setBuyer(msgR1.getSender());
								sellComp.setComponent(componentSale);
								sellComp.setDeliveryDate(day);
								sellComp.setComponentPrice(oS);
								sellComp.setQuantity(openOrders.get(oS).getQuantity());

								Action request = new Action();
								request.setAction(sellComp);
								request.setActor(msgR1.getSender());

								try {
									getContentManager().fillContent(response, request);
									send(response);
									messageReceived = true;
									//step++;

								}
								catch (CodecException ce2) {
									ce2.printStackTrace();
								}
								catch (OntologyException oe) {
									oe.printStackTrace();
								} 

							} catch (Exception e) {}
						}
					}
					step++;

				case 3:

					int i = 0;
					while(i != 1) {

						MessageTemplate sr = MessageTemplate.MatchPerformative(ACLMessage.CONFIRM); 
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

										order.setDeliveryDate((day + order.getDeliveryDate()));

										todaysCost = (todaysCost + (order.getQuantity() * order.getComponentPrice()));

										openDeliveries.add(order);

										i++;
									}
								}
							} catch (Exception e) {}
						}
					}


				case 4:

					messageReceived = false;

					ACLMessage msg2 = new ACLMessage(ACLMessage.QUERY_IF);
					msg2.addReceiver(supplierName);
					msg2.setLanguage(codec.getName());
					msg2.setOntology(ontology.getName());

					Component storageOrder = new Component();

					storageOrder.setComponentName("Storage");
					storageOrder.setComponentSize(openOrders.get(oS).getPhone().getStorage().getStorageSize());
					Owns storageRequest = new Owns();
					storageRequest.setOwner(supplierName);
					storageRequest.setComponent(storageOrder);	
					storageRequest.setQuantity(openOrders.get(oS).getQuantity());

					try {
						getContentManager().fillContent(msg2, storageRequest);
						send(msg2);
						step++;
					}	
					catch (CodecException ce) {
						ce.printStackTrace();
					}
					catch (OntologyException oe) {
						oe.printStackTrace();
					}  


				case 5:

					while(messageReceived != true) {

						MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP); 
						ACLMessage msgR2 = receive(mt);
						if(msgR2 != null){
							try {
								ACLMessage response = new ACLMessage(ACLMessage.PROPOSE);
								response.addReceiver(msgR2.getSender());
								response.setLanguage(codec.getName());
								response.setOntology(ontology.getName());

								Component componentSale = new Component();

								componentSale.setComponentName("Storage");
								componentSale.setComponentSize(openOrders.get(oS).getPhone().getStorage().getStorageSize());
								Sell sellComp = new Sell();
								sellComp.setBuyer(msgR2.getSender());
								sellComp.setComponent(componentSale);
								sellComp.setDeliveryDate(day);
								sellComp.setComponentPrice(0);
								sellComp.setQuantity(openOrders.get(oS).getQuantity());

								Action request = new Action();
								request.setAction(sellComp);
								request.setActor(msgR2.getSender());

								try {
									getContentManager().fillContent(response, request);
									send(response);
									messageReceived = true;

								}
								catch (CodecException ce2) {
									ce2.printStackTrace();
								}
								catch (OntologyException oe) {
									oe.printStackTrace();
								} 
							}
							catch (Exception e) {}
						}
					}

					step++;

				case 6:

					int i2 = 0;
					while(i2 != 1) {

						MessageTemplate sr = MessageTemplate.MatchPerformative(ACLMessage.CONFIRM); 
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

										order.setDeliveryDate((day + order.getDeliveryDate()));

										todaysCost = (todaysCost + (order.getQuantity() * order.getComponentPrice()));

										openDeliveries.add(order);

										i2++;
									}
								}
							} catch (Exception e) {}
						}
					}

				case 7:

					messageReceived = false;

					ACLMessage msg3 = new ACLMessage(ACLMessage.QUERY_IF);
					msg3.addReceiver(supplierName);
					msg3.setLanguage(codec.getName());
					msg3.setOntology(ontology.getName());

					Component ramOrder = new Component();

					ramOrder.setComponentName("RAM");
					ramOrder.setComponentSize(openOrders.get(oS).getPhone().getRAM().getRamSize());
					Owns ramRequest = new Owns();
					ramRequest.setOwner(supplierName);
					ramRequest.setComponent(ramOrder);	
					ramRequest.setQuantity(openOrders.get(oS).getQuantity());

					try {
						getContentManager().fillContent(msg3, ramRequest);
						send(msg3);
						step++;
					}	
					catch (CodecException ce) {
						ce.printStackTrace();
					}
					catch (OntologyException oe) {
						oe.printStackTrace();
					}  


				case 8:

					while(messageReceived != true) {

						MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP); 
						ACLMessage msgR3 = receive(mt);
						if(msgR3 != null){
							try {
								ACLMessage response = new ACLMessage(ACLMessage.PROPOSE);
								response.addReceiver(msgR3.getSender());
								response.setLanguage(codec.getName());
								response.setOntology(ontology.getName());

								Component componentSale = new Component();

								componentSale.setComponentName("RAM");
								componentSale.setComponentSize(openOrders.get(oS).getPhone().getRAM().getRamSize());
								Sell sellComp = new Sell();
								sellComp.setBuyer(msgR3.getSender());
								sellComp.setComponent(componentSale);
								sellComp.setDeliveryDate(day);
								sellComp.setComponentPrice(0);
								sellComp.setQuantity(openOrders.get(oS).getQuantity());

								Action request = new Action();
								request.setAction(sellComp);
								request.setActor(msgR3.getSender());

								try {
									getContentManager().fillContent(response, request);
									send(response);
									messageReceived = true;

								}
								catch (CodecException ce2) {
									ce2.printStackTrace();
								}
								catch (OntologyException oe) {
									oe.printStackTrace();
								} 
							}
							catch (Exception e) {}
						}
					}

					step++;

				case 9:

					int i3 = 0;
					while(i3 != 1) {

						MessageTemplate sr = MessageTemplate.MatchPerformative(ACLMessage.CONFIRM); 
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

										order.setDeliveryDate((day + order.getDeliveryDate()));

										todaysCost = (todaysCost + (order.getQuantity() * order.getComponentPrice()));

										openDeliveries.add(order);

										i3++;
									}
								}
							} catch (Exception e) {}
						}
					}

				case 10:

					messageReceived = false;

					ACLMessage msg4 = new ACLMessage(ACLMessage.QUERY_IF);
					msg4.addReceiver(supplier1);
					msg4.setLanguage(codec.getName());
					msg4.setOntology(ontology.getName());

					Component batteryOrder = new Component();

					batteryOrder.setComponentName("Battery");
					batteryOrder.setComponentSize(openOrders.get(oS).getPhone().getBattery().getBatterySize());
					Owns batteryRequest = new Owns();
					batteryRequest.setOwner(supplier1);
					batteryRequest.setComponent(batteryOrder);
					batteryRequest.setQuantity(openOrders.get(oS).getQuantity());

					try {
						getContentManager().fillContent(msg4, batteryRequest);
						send(msg4);
						step++;
					}
					catch (CodecException ce) {
						ce.printStackTrace();
					}
					catch (OntologyException oe) {
						oe.printStackTrace();
					}


				case 11:

					while(messageReceived != true) {

						MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP); 
						ACLMessage msgR4 = receive(mt);
						if(msgR4 != null){
							try {
								ACLMessage response = new ACLMessage(ACLMessage.PROPOSE);
								response.addReceiver(msgR4.getSender());
								response.setLanguage(codec.getName());
								response.setOntology(ontology.getName());

								Component componentSale = new Component();

								componentSale.setComponentName("Battery");
								componentSale.setComponentSize(openOrders.get(oS).getPhone().getBattery().getBatterySize());
								Sell sellComp = new Sell();
								sellComp.setBuyer(msgR4.getSender());
								sellComp.setComponent(componentSale);
								sellComp.setDeliveryDate(day);
								sellComp.setComponentPrice(0);
								sellComp.setQuantity(openOrders.get(oS).getQuantity());

								Action request = new Action();
								request.setAction(sellComp);
								request.setActor(msgR4.getSender());

								try {
									getContentManager().fillContent(response, request);
									send(response);
									messageReceived = true;

								}
								catch (CodecException ce2) {
									ce2.printStackTrace();
								}
								catch (OntologyException oe) {
									oe.printStackTrace();
								} 
							} 
							catch (Exception e) {} 
						}
					}
					step++;

				case 12:

					int i4 = 0;
					while(i4 != 1) {

						MessageTemplate sr = MessageTemplate.MatchPerformative(ACLMessage.CONFIRM); 
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

										order.setDeliveryDate((day + order.getDeliveryDate()));

										todaysCost = (todaysCost + (order.getQuantity() * order.getComponentPrice()));
										totalComponentsOrdered = totalComponentsOrdered + (openOrders.get(oS).getQuantity() * 4);
										totalComponentCost = totalComponentCost + todaysCost;

										openDeliveries.add(order);

										i4++;
									}
								}
							} catch (Exception e) {}
						}
					}
					step++;

				case 13:

					ACLMessage endMsg = new ACLMessage(ACLMessage.INFORM);
					endMsg.addReceiver(supplier1);
					endMsg.setContent("end of order");
					myAgent.send(endMsg);

					ACLMessage endMsg2 = new ACLMessage(ACLMessage.INFORM);
					endMsg2.addReceiver(supplier2);
					endMsg2.setContent("end of order");
					myAgent.send(endMsg2);
					System.out.println("Total orders cost: £" + todaysCost);

					oS++;
				}
			}

			else {

				// Send end of order informs to each agent to inform them that ordering is finished for the day,
				// and that they can send an end of day inform to the ticker agent.

				ACLMessage endMsg = new ACLMessage(ACLMessage.INFORM);
				endMsg.addReceiver(supplier1);
				endMsg.setContent("end of order");
				myAgent.send(endMsg);

				ACLMessage endMsg2 = new ACLMessage(ACLMessage.INFORM);
				endMsg2.addReceiver(supplier2);
				endMsg2.setContent("end of order");
				myAgent.send(endMsg2);

				System.out.println("Total orders cost: £" + todaysCost);
				oS++;

			}
		}
	}


	// This method checks if there are any orders being delivered today. If so it removes the ordered components from the openDeliveries list and places them
	// in the stock list ready for use.
	public class checkDeliveries extends OneShotBehaviour {

		public checkDeliveries(Agent a) {
			super(a);
		}

		@Override
		public void action() {

			System.out.println("");

			if(openDeliveries.isEmpty() == false) {
				for(Sell temp : openDeliveries) {
					if (temp.getDeliveryDate() == day) {
						System.out.println(temp.getComponent().getComponentName() + " x" + temp.getQuantity() + " delivered");
						if(stock.containsKey(temp.getComponent().getComponentSize())){
							stock.put(temp.getComponent().getComponentSize(), stock.get(temp.getComponent().getComponentSize()) + temp.getQuantity());
						}
						else {
							stock.put(temp.getComponent().getComponentSize(), temp.getQuantity());
						}

					}
				}
				System.out.println("");
			}
		}
	}


	// This method is used to sort a list used in the next methods. Sorts list in ascending order by due date.
	public static Comparator<OrderInst> DueDateComp = new Comparator<OrderInst>() {

		public int compare(OrderInst i1, OrderInst i2) {

			int item1 = i1.getDueDate();
			int item2 = i2.getDueDate();

			/*For ascending order*/
			return item1-item2;
		}
	};


	// This method will attempt to build the first order. This order can be any quantity of items.
	public class buildOrder1 extends Behaviour {

		public buildOrder1(Agent a) {
			super(a);
		}

		boolean finished = false;

		int orderToBuild;


		@Override
		public void action() {

			// WORK OUT LATE FEES BEFORE ORDERS ARE BUILT AND REMOVED FROM OPEN ORDERS
			for (int i = 0; i < openOrders.size(); i++) {
				if(openOrders.get(i).getDueDate() < 0) {
					lateFees = lateFees + openOrders.get(i).getPenalty();
				}
			}

			totalLateFees = totalLateFees + lateFees;


			if(day != 1 && !openOrders.isEmpty()) {
				// Create index list of orders
				int[] indexes = new int[openOrders.size()];

				// Copy open orders to list
				ArrayList<OrderInst> copy = new ArrayList<OrderInst>(openOrders);

				// Sort list
				Collections.sort(copy, DueDateComp);

				// Print list
				for (int n = 0; n < openOrders.size(); n++) {
					indexes[n] = copy.indexOf(openOrders.get(n));
				}


				// Determine which is the first order that can be built, if any

				boolean buildable = false;
				boolean noneBuildable = false;
				int n = 0;

				while(buildable == false || noneBuildable == false) {

					OrderInst order = openOrders.get(indexes[n]);
					int quantity = order.getQuantity();

					int partOne = order.getPhone().getScreen().getScreenSize();
					int partTwo = order.getPhone().getStorage().getStorageSize();
					int partThree = order.getPhone().getRAM().getRamSize();
					int partFour = order.getPhone().getBattery().getBatterySize();


					// If the parts key is in the stock list
					if(stock.containsKey(partOne) && stock.containsKey(partTwo) && stock.containsKey(partThree) && stock.containsKey(partFour)) {

						// If the parts quantity is in stock
						if(stock.get(partOne) >= quantity) {
							if (stock.get(partTwo) >= quantity) {
								if (stock.get(partThree) >= quantity) {
									if (stock.get(partFour) >= quantity) {
										orderToBuild = indexes[n];
										buildable = true;
										break;

									}
									else {
										break;
									}
								} else {
									break;
								}
							} else {
								break;
							}

						} else {
							break;
						}

					}
					else {

						if (n == (openOrders.size()-1)) {
							noneBuildable = true;
							break;

						}

						n++;
						buildable = false;
					}

				}

				finished = true;

				if(buildable == true) {
					if(!openOrders.isEmpty()) {

						OrderInst order = openOrders.get(orderToBuild);

						int partOne = order.getPhone().getScreen().getScreenSize();
						int partTwo = order.getPhone().getStorage().getStorageSize();
						int partThree = order.getPhone().getRAM().getRamSize();
						int partFour = order.getPhone().getBattery().getBatterySize();

						int quantity = order.getQuantity();

						Phone phone = new Phone();

						Screen SCRN = new Screen();
						SCRN.setComponentName("Screen");
						SCRN.setScreenSize(partOne);
						phone.setScreen(SCRN);
						stock.replace(partOne, (stock.get(partOne)-quantity));
						

						Storage STRG = new Storage();
						STRG.setComponentName("Storage");
						STRG.setStorageSize(partTwo);
						phone.setStorage(STRG);
						stock.replace(partTwo, (stock.get(partTwo)-quantity));

						RAM RM = new RAM();
						RM.setComponentName("RAM");
						RM.setRamSize(partThree);
						phone.setRAM(RM);
						stock.replace(partThree, (stock.get(partThree)-quantity));

						Battery BTY = new Battery();
						BTY.setComponentName("Battery");
						BTY.setBatterySize(partFour);
						phone.setBattery(BTY);
						stock.replace(partFour, (stock.get(partFour)-quantity));

						System.out.println("Building order: " + openOrders.get(orderToBuild).getQuantity() + "x " + partOne + " " + partTwo + " " + partThree + " " + partFour + " due in " + openOrders.get(orderToBuild).getDueDate() + " day(s).");

						// Deliver the built phones in the form of a list of phones.

						Deliver deliveryOrder = new Deliver();

						deliveryOrder.setPhones(phone);
						deliveryOrder.setQuantity(quantity);
						deliveryOrder.setOrderCost((openOrders.get(orderToBuild).getQuantity() * openOrders.get(orderToBuild).getUnitPrice()));

						// Send confirmation of delivery.
						ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
						msg.setConversationId("delivery");
						msg.addReceiver(order.getCustomer());
						msg.setLanguage(codec.getName());
						msg.setOntology(ontology.getName());

						Action delivery = new Action();
						delivery.setAction(deliveryOrder);
						delivery.setActor(order.getCustomer());

						try {
							getContentManager().fillContent(msg, delivery);
							send(msg);
							System.out.println("Order delivered to " + openOrders.get(orderToBuild).getCustomer());
							totalOrdersDelivered = totalOrdersDelivered+1;
							income = (openOrders.get(orderToBuild).getQuantity() * openOrders.get(orderToBuild).getUnitPrice());
							ordersBuiltToday = openOrders.get(orderToBuild).getQuantity();
							openOrders.remove(orderToBuild);
							oS--;
							finished = true;

						}
						catch (CodecException ce) {
							ce.printStackTrace();
						}
						catch (OntologyException oe) {
							oe.printStackTrace();
						} 

					}
				}
				else if (noneBuildable == true){
					finished = true;
				}
				else{
					finished = true;
				}
			}
		}


		@Override
		public boolean done() {
			// TODO Auto-generated method stub
			return finished = true;
		}
	}


	// This method will go through the remaining open orders and build any order that has a quantity that is less than 50 when
	// added to the quantity of the first order, because we can only build a maximum of 50 orders per day.
	public class buildOrder2 extends OneShotBehaviour {

		public buildOrder2(Agent a) {
			super(a);
		}

		@Override
		public void action() {

			if(day != 1 && !openOrders.isEmpty()){

				int i = 0;

				if(ordersBuiltToday < 50){
					while(i < openOrders.size()){
						if((openOrders.get(i).getQuantity() + ordersBuiltToday) <= 50){

							OrderInst order = openOrders.get(i);
							int quantity = order.getQuantity();

							int partOne = order.getPhone().getScreen().getScreenSize();
							int partTwo = order.getPhone().getStorage().getStorageSize();
							int partThree = order.getPhone().getRAM().getRamSize();
							int partFour = order.getPhone().getBattery().getBatterySize();

							// If the parts key is in the stock list
							if(stock.containsKey(partOne) && stock.containsKey(partTwo) && stock.containsKey(partThree) && stock.containsKey(partFour)) {
								if(stock.get(partOne) >= quantity) {
									if (stock.get(partTwo) >= quantity) {
										if (stock.get(partThree) >= quantity) {
											if (stock.get(partFour) >= quantity) {
												

												Phone phone = new Phone();

												Screen SCRN = new Screen();
												SCRN.setComponentName("Screen");
												SCRN.setScreenSize(partOne);
												phone.setScreen(SCRN);
												stock.replace(partOne, (stock.get(partOne)-quantity));

												Storage STRG = new Storage();
												STRG.setComponentName("Storage");
												STRG.setStorageSize(partTwo);
												phone.setStorage(STRG);
												stock.replace(partTwo, (stock.get(partTwo)-quantity));

												RAM RM = new RAM();
												RM.setComponentName("RAM");
												RM.setRamSize(partThree);
												phone.setRAM(RM);
												stock.replace(partThree, (stock.get(partThree)-quantity));

												Battery BTY = new Battery();
												BTY.setComponentName("Battery");
												BTY.setBatterySize(partFour);
												phone.setBattery(BTY);
												stock.replace(partFour, (stock.get(partFour)-quantity));


												System.out.println("Building order: " + openOrders.get(i).getQuantity() + "x " + partOne + " " + partTwo + " " + partThree + " " + partFour + " due in " + openOrders.get(i).getDueDate() + " day(s).");

												Deliver deliveryOrder = new Deliver();

												deliveryOrder.setPhones(phone);
												deliveryOrder.setQuantity(quantity);
												deliveryOrder.setOrderCost((openOrders.get(i).getQuantity() * openOrders.get(i).getUnitPrice()));


												ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
												msg.addReceiver(order.getCustomer());
												msg.setConversationId("delivery");
												msg.setLanguage(codec.getName());
												msg.setOntology(ontology.getName());

												Action delivery = new Action();
												delivery.setAction(deliveryOrder);
												delivery.setActor(order.getCustomer());

												try {
													getContentManager().fillContent(msg, delivery);
													send(msg);
													System.out.println("Order delivered to " + openOrders.get(i).getCustomer());
													totalOrdersDelivered = totalOrdersDelivered+1;
													incomeO2 = incomeO2 + (openOrders.get(i).getQuantity() * openOrders.get(i).getUnitPrice());
													ordersBuiltToday = openOrders.get(i).getQuantity();
													openOrders.remove(i);
													oS--;
													i = openOrders.size();

												}
												catch (CodecException ce) {
													ce.printStackTrace();
												}
												catch (OntologyException oe) {
													oe.printStackTrace();
												} 

											}

											break;

										}
										else {
											break;
										}
									} else {
										break;
									}
								} else {
									break;
								}

							} else {
								break;
							}
						}
						else {
							i++;
						}
					}
				} else {
					return;
				}

				// NOTE: I opened up these if statements rather than having them in an if(X AND X AND X AND X etc.) because it behaved differently when in this format.
			}
		}
	}


	// This method works out some of the expenditure and takes some expenditure that is worked out further up in the program and prints it at the end of each day.
	public class Expenditure extends OneShotBehaviour {

		public Expenditure(Agent a) {
			super(a);
		}

		@Override
		public void action() {

			// Storage costs calculated here.

			int sum = 0;
			for (int f : stock.values()) {
				sum += f;
			}

			System.out.println("");
			System.out.println("TODAY'S EXPENDITURE: ");
			System.out.println("Order costs: -£" +  todaysCost);
			System.out.println("Late fees: -£" + lateFees);
			System.out.println("Warehouse cost: -£" + (5*sum));
			System.out.println("Orders Delivered: +£" + (income + incomeO2));

			bank = bank + (income+incomeO2) + (0-lateFees) + (0-5*sum) + (0-todaysCost);

			System.out.println("");
			System.out.println("Today: £" + ((0-todaysCost) + (0-lateFees) + (0-(5*sum)) + (income + incomeO2)));
			System.out.println("Bank: £" + bank);


			
			totalOrdersMoney = totalOrdersMoney + income + incomeO2;
			totalWarehouseCost = totalWarehouseCost + (5*sum);



		}
	}


	// This class handles the end of day, and also prints some statistics at the end of day 100. Statistics could have been done in expenditure class too.
	public class EndDay extends OneShotBehaviour {

		public EndDay(Agent a) {
			super(a);
		}

		@Override
		public void action() {

			// Change due dates of orders.
			for (int i = 0; i < openOrders.size(); i++) {
				openOrders.get(i).setDueDate((openOrders.get(i).getDueDate() - 1));
			}


			if(day == 100) {
				System.out.println("");
				System.out.println("-------------------------------------");
				System.out.println("------------ STATISTICS -------------");
				System.out.println("- Total orders taken:       " + totalOrdersTaken);
				System.out.println("- Total orders delivered:   " + totalOrdersDelivered);
				System.out.println("- Total components ordered: " + totalComponentsOrdered);
				System.out.println("- Total component costs:    £" + totalComponentCost);
				System.out.println("- Total warehouse costs:    £" + totalWarehouseCost);
				System.out.println("- Total late fees:          £" + totalLateFees);
				System.out.println("- Total income from orders: £" + totalOrdersMoney);
				System.out.println("");
				System.out.println("- Total profit            : £" + bank);
				System.out.println("-------------------------------------");
			}

			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			msg.addReceiver(tickerAgent);
			msg.setContent("doneMNF");
			myAgent.send(msg);

		}

	}
}


package ontology.elements;

import java.util.ArrayList;

import jade.content.AgentAction;

public class Deliver implements AgentAction {
	
	private Phone phones = new Phone();
	private int quantity;
	private int orderCost;
	
	public Phone getPhone() {
		return phones;
	}
	
	public void setPhones(Phone phones) {
		this.phones = phones;
	}
	
	
	public int getQuantity() {
		return quantity;
	}
	
	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}
	
	
	public int getOrderCost() {
		return orderCost;
	}
	
	public void setOrderCost(int orderCost) {
		this.orderCost = orderCost;
	}

}

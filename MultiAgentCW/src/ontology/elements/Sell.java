package ontology.elements;

import jade.content.AgentAction;
import jade.core.AID;

public class Sell implements AgentAction {
	private AID buyer;
	private Component component;
	private int quantity;
	private int componentPrice;
	private int deliveryDate;
	
	public AID getBuyer() {
		return buyer;
	}
	
	public void setBuyer(AID buyer) {
		this.buyer = buyer;
	}
	
	public Component getComponent() {
		return component;
	}
	
	public void setComponent(Component component) {
		this.component = component;
	}
	
	public int getQuantity() {
		return quantity;
	}
	
	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}
	
	public int getComponentPrice() {
		return componentPrice;
	}
	
	public void setComponentPrice(int componentPrice) {
		this.componentPrice = componentPrice;
	}
	
	public int getDeliveryDate() {
		return deliveryDate;
	}
	
	public void setDeliveryDate(int deliveryDate) {
		this.deliveryDate = deliveryDate;
	}
}

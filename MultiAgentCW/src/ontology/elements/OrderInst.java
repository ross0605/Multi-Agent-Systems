package ontology.elements;

import java.util.ArrayList;

import jade.content.Concept;
import jade.content.onto.annotations.Slot;
import jade.core.AID;

public class OrderInst implements Concept {
	
	private int quantity;
	private int unitPrice;
	private int dueDate;
	private int penalty;
	private Phone phone;
	private AID customerAID;

	
	@Slot(mandatory = true)
	public int getQuantity() {
		return quantity;
	}
	
	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}
	
	@Slot(mandatory = true)
	public int getUnitPrice() {
		return unitPrice;
	}
	
	public void setUnitPrice(int unitPrice) {
		this.unitPrice = unitPrice;
	}
	
	@Slot(mandatory = true)
	public int getDueDate() {
		return dueDate;
	}
	
	public void setDueDate(int dueDate) {
		this.dueDate = dueDate;
	}
	
	@Slot(mandatory = true)
	public int getPenalty() {
		return penalty;
	}
	
	public void setPenalty(int penalty) {
		this.penalty = penalty;
	}
	
	@Slot(mandatory = true)
	public Phone getPhone()
	{
		return phone;
	}

	public void setPhone(Phone phone)
	{
		this.phone = phone;
	}
	
	@Slot(mandatory = true)
	public AID getCustomer()
	{
		return customerAID;
	}

	public void setCustomer(AID customerAID)
	{
		this.customerAID = customerAID;
	}

}

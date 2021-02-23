package ontology.elements;

import jade.content.Concept;
import jade.content.onto.annotations.AggregateSlot;
import jade.content.onto.annotations.Slot;

public class Component implements Concept {
	
	private String componentName;
	private int componentSize;
	
	@AggregateSlot(cardMin = 1, cardMax = 4)
	public String getComponentName() {
		return componentName;
	}
	
	public void setComponentName(String componentName) {
		this.componentName = componentName;
	}
	
	@Slot (mandatory = false)
	public int getComponentSize() {
		return componentSize;
	}
	
	public void setComponentSize(int componentSize) {
		this.componentSize = componentSize;
	}

}

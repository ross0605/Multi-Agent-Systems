package ontology.elements;

import jade.content.Concept;
import jade.content.onto.annotations.Slot;

public class RAM extends Component {
	
	private int ramSize;

	@Slot(mandatory = true)
	public int getRamSize() {
		return ramSize;
	}
	
	public void setRamSize(int ramSize) {
		this.ramSize = ramSize;
	}

}

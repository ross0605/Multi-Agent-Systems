package ontology.elements;

import jade.content.Concept;
import jade.content.onto.annotations.Slot;

public class Screen extends Component {
	
	private int screenSize;

	@Slot(mandatory = true)
	public int getScreenSize() {
		return screenSize;
	}
	
	public void setScreenSize(int screenSize) {
		this.screenSize = screenSize;
	}

}

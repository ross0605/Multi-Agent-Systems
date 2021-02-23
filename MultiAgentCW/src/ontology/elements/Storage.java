package ontology.elements;

import jade.content.Concept;
import jade.content.onto.annotations.Slot;

public class Storage extends Component {
	
	private int storageSize;

	@Slot(mandatory = true)
	public int getStorageSize() {
		return storageSize;
	}
	
	public void setStorageSize(int storageSize) {
		this.storageSize = storageSize;
	}

}

package ontology.elements;

import jade.content.Concept;
import jade.content.onto.annotations.Slot;

public class Battery extends Component {
	
	private int batterySize;

	@Slot(mandatory = true)
	public int getBatterySize() {
		return batterySize;
	}
	
	public void setBatterySize(int batterySize) {
		this.batterySize = batterySize;
	}

}

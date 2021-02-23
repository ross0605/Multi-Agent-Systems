package ontology.elements;

import jade.content.Concept;
import jade.content.onto.annotations.Slot;

public class Phone implements Concept {

	private String type;
	private Screen screen;
	private Storage storage;
	private RAM ram;
	private Battery battery;

	@Slot(mandatory = true)
	public String getType()
	{
		return type;
	}

	public void setType(String type)
	{
		this.type = type;
	}
	
	@Slot(mandatory = true)
	public Screen getScreen()
	{
		return screen;
	}

	public void setScreen(Screen screen)
	{
		this.screen = screen;
	}

	@Slot(mandatory = true)
	public Storage getStorage()
	{
		return storage;
	}

	public void setStorage(Storage storage)
	{
		this.storage = storage;
	}

	@Slot(mandatory = true)
	public RAM getRAM()
	{
		return ram;
	}

	public void setRAM(RAM ram)
	{
		this.ram = ram;
	}

	@Slot(mandatory = true)
	public Battery getBattery()
	{
		return battery;
	}

	public void setBattery(Battery battery)
	{
		this.battery = battery;
	}
}

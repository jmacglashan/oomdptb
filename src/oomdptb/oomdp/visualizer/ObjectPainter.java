package oomdptb.oomdp.visualizer;

import java.awt.Graphics2D;
import oomdptb.oomdp.*;

public abstract class ObjectPainter {

	protected Domain 		domain;
	
	
	public ObjectPainter(Domain domain){
		this.domain = domain;
	}
	
	public void setDomain(Domain domain){
		this.domain = domain;
	}
	
	
	
	/* g2: 				graphics context to which the object should be painted
	 * ob				the instantiated object to be painted
	 * cWidth/cHeight:	dimensions of the canvas size
	 */
	public abstract void paintObject(Graphics2D g2, ObjectInstance ob, float cWidth, float cHeight);
	
	
}
